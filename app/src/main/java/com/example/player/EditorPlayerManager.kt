package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioTrackItem
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.util.TimelineUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentClipId: String? = null,
    val currentClipIndex: Int = -1,
    val isPhotoActive: Boolean = false,
    val activePhotoPath: String? = null,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Gerenciador central do motor de reprodução do editor.
 * Mantém um relógio único determinístico (`currentPositionMs`) para a timeline global,
 * sincronizando ExoPlayer, AudioSyncManager, transições e overlays.
 */
class EditorPlayerManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = false
    }

    val audioSyncManager: AudioSyncManager = AudioSyncManager(context)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeClips: List<MediaClip> = emptyList()
    private var activeAudios: List<AudioTrackItem> = emptyList()

    private var trackingJob: Job? = null
    private var hardwareSeekJob: Job? = null
    private var lastPhotoTickTime: Long = 0L

    // Token / geração de seek para invalidar respostas assíncronas defasadas
    private val seekGeneration = AtomicLong(0L)
    @Volatile
    private var confirmedSeekGeneration: Long = 0L

    // ID do clipe atualmente carregado no ExoPlayer
    private var loadedClipId: String? = null

    var onTimelinePositionChanged: ((Long) -> Unit)? = null

    init {
        audioSyncManager.onAudioTrackError = { _, err ->
            _playbackState.update { it.copy(errorMessage = err) }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _playbackState.update { it.copy(isBuffering = isBuffering) }

                if (playbackState == Player.STATE_ENDED) {
                    handleClipEnded()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    confirmedSeekGeneration = seekGeneration.get()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = "Erro de reprodução: ${error.localizedMessage ?: "Erro desconhecido"}"
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        errorMessage = msg,
                        isBuffering = false
                    )
                }
                audioSyncManager.pauseAll()
                stopTracking()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isCurrentClipPhoto() && activeClips.isNotEmpty()) {
                    _playbackState.update { it.copy(isPlaying = isPlaying) }
                }
            }
        })
    }

    fun setClipsAndAudios(
        clips: List<MediaClip>,
        audios: List<AudioTrackItem>,
        initialSeekPlayhead: Long? = null
    ) {
        val totalDuration = TimelineUtils.calculateTotalProjectDuration(clips, audios)
        activeClips = clips
        activeAudios = audios
        audioSyncManager.setTracks(audios)
        _playbackState.update { it.copy(totalDurationMs = totalDuration) }

        if (clips.isEmpty() && audios.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            loadedClipId = null
            audioSyncManager.pauseAll()
            _playbackState.update {
                it.copy(
                    currentClipId = null,
                    currentClipIndex = -1,
                    isPhotoActive = false,
                    activePhotoPath = null,
                    isPlaying = false,
                    currentPositionMs = 0L
                )
            }
            return
        }

        val targetPlayhead = initialSeekPlayhead ?: _playbackState.value.currentPositionMs
        seekTo(targetPlayhead.coerceIn(0L, totalDuration))
    }

    fun setClips(clips: List<MediaClip>, initialSeekPlayhead: Long? = null) {
        setClipsAndAudios(clips, activeAudios, initialSeekPlayhead)
    }

    fun setAudios(audios: List<AudioTrackItem>) {
        setClipsAndAudios(activeClips, audios, _playbackState.value.currentPositionMs)
    }

    /**
     * Inicia a reprodução de forma previsível e determinística:
     * 1. Lê currentTimeMs global
     * 2. Identifica o clipe correspondente
     * 3. Calcula a posição interna do clipe
     * 4. Posiciona o player corretamente
     * 5. Inicia reprodução
     * 6. Atualiza currentTimeMs continuamente
     * 7. Detecta troca de clipe
     * 8. Continua automaticamente
     * 9. Para exatamente no final da timeline
     */
    fun play() {
        if (activeClips.isEmpty() && activeAudios.isEmpty()) return

        val totalDuration = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
        val currentPos = _playbackState.value.currentPositionMs

        // Se estiver no final da timeline, reinicia do início
        val effectivePos = if (currentPos >= totalDuration && totalDuration > 0L) {
            0L
        } else {
            currentPos
        }

        // 1. Atualiza estado para tocando e tempo inicial efetivo
        _playbackState.update {
            it.copy(
                isPlaying = true,
                currentPositionMs = effectivePos,
                errorMessage = null
            )
        }

        // 2 & 3. Identifica clipe e calcula posição de origem
        val info = TimelineUtils.findClipAtTimelinePosition(activeClips, effectivePos)
        if (info != null) {
            val clip = info.clip
            val clipIndex = info.index

            _playbackState.update {
                it.copy(
                    currentClipId = clip.id,
                    currentClipIndex = clipIndex
                )
            }

            if (clip.type == MediaType.PHOTO) {
                exoPlayer.pause()
                _playbackState.update {
                    it.copy(
                        isPhotoActive = true,
                        activePhotoPath = clip.localPath.ifBlank { clip.thumbnailPath.ifBlank { clip.uri } }
                    )
                }
                lastPhotoTickTime = System.currentTimeMillis()
            } else {
                _playbackState.update { it.copy(isPhotoActive = false, activePhotoPath = null) }
                // 4. Posiciona o player de vídeo na posição exata antes de dar play
                val currentGen = seekGeneration.incrementAndGet()
                confirmedSeekGeneration = currentGen
                prepareAndSeekVideo(clip, clipIndex, info.sourcePositionMs, currentGen)
                exoPlayer.play()
            }
        } else {
            // Apenas áudio ou espaço vazio
            lastPhotoTickTime = System.currentTimeMillis()
        }

        // 5. Inicia áudio multitrack sincronizado
        audioSyncManager.syncWithMasterPlayhead(effectivePos, isMasterPlaying = true)

        // 6. Inicia tracking contínuo
        startTracking()
    }

    fun pause() {
        _playbackState.update { it.copy(isPlaying = false) }
        exoPlayer.pause()
        audioSyncManager.pauseAll()
        stopTracking()
    }

    fun stop() {
        pause()
        seekTo(0L)
    }

    /**
     * Executa seek para uma posição absoluta na timeline.
     * Utiliza seekGeneration para invalidar callbacks antigos e coalescer seeks rápidos de scrubbing.
     */
    fun seekTo(timelinePositionMs: Long) {
        val totalDuration = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
        val clampedPlayhead = timelinePositionMs.coerceIn(0L, totalDuration.coerceAtLeast(0L))
        val currentGen = seekGeneration.incrementAndGet()

        // 1. Atualização IMEDIATA de UI para feedback instantâneo da agulha e dos overlays
        _playbackState.update { it.copy(currentPositionMs = clampedPlayhead) }
        onTimelinePositionChanged?.invoke(clampedPlayhead)

        // 2. Coalesce e executa o seek de hardware cancelando requests intermediários
        hardwareSeekJob?.cancel()
        hardwareSeekJob = coroutineScope.launch {
            delay(16) // Coalesce lightning-fast scrubber events
            if (currentGen != seekGeneration.get()) return@launch

            if (activeClips.isEmpty()) {
                _playbackState.update {
                    it.copy(
                        currentClipId = null,
                        currentClipIndex = -1,
                        isPhotoActive = false,
                        activePhotoPath = null
                    )
                }
                audioSyncManager.seekTo(clampedPlayhead, isMasterPlaying = _playbackState.value.isPlaying)
                return@launch
            }

            val info = TimelineUtils.findClipAtTimelinePosition(activeClips, clampedPlayhead)
            if (info == null) {
                audioSyncManager.seekTo(clampedPlayhead, isMasterPlaying = _playbackState.value.isPlaying)
                return@launch
            }

            // Se um seek mais recente chegou durante o lançamento, descarta este
            if (currentGen != seekGeneration.get()) return@launch

            val clip = info.clip
            val clipIndex = info.index

            _playbackState.update {
                it.copy(
                    currentClipId = clip.id,
                    currentClipIndex = clipIndex
                )
            }

            if (clip.type == MediaType.PHOTO) {
                exoPlayer.pause()
                _playbackState.update {
                    it.copy(
                        isPhotoActive = true,
                        activePhotoPath = clip.localPath.ifBlank { clip.thumbnailPath.ifBlank { clip.uri } },
                        errorMessage = null
                    )
                }
                lastPhotoTickTime = System.currentTimeMillis()
                confirmedSeekGeneration = currentGen
            } else {
                _playbackState.update {
                    it.copy(isPhotoActive = false, activePhotoPath = null)
                }
                prepareAndSeekVideo(clip, clipIndex, info.sourcePositionMs, currentGen)
            }

            audioSyncManager.seekTo(clampedPlayhead, isMasterPlaying = _playbackState.value.isPlaying)
        }
    }

    fun rewind(stepMs: Long = 5000L) {
        val target = (_playbackState.value.currentPositionMs - stepMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun forward(stepMs: Long = 5000L) {
        val total = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
        val target = (_playbackState.value.currentPositionMs + stepMs).coerceAtMost(total)
        seekTo(target)
    }

    fun previousClip() {
        if (activeClips.isEmpty()) {
            seekTo(0L)
            return
        }
        val currentPos = _playbackState.value.currentPositionMs
        val info = TimelineUtils.findClipAtTimelinePosition(activeClips, currentPos)
        if (info == null) {
            seekTo(0L)
            return
        }

        val clipStart = info.timelineStartMs
        // Se já passou mais de 1s do início do clipe atual, volta ao início dele
        if (currentPos - clipStart > 1000L) {
            seekTo(clipStart)
        } else {
            // Caso contrário, vai para o clipe anterior
            val prevIndex = info.index - 1
            if (prevIndex >= 0) {
                val prevStart = TimelineUtils.getClipStartTimelineMs(activeClips, prevIndex)
                seekTo(prevStart)
            } else {
                seekTo(0L)
            }
        }
    }

    fun nextClip() {
        if (activeClips.isEmpty()) return
        val currentPos = _playbackState.value.currentPositionMs
        val info = TimelineUtils.findClipAtTimelinePosition(activeClips, currentPos)
        if (info == null) return

        val nextIndex = info.index + 1
        if (nextIndex in activeClips.indices) {
            val nextStart = TimelineUtils.getClipStartTimelineMs(activeClips, nextIndex)
            seekTo(nextStart)
        } else {
            val total = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
            seekTo(total)
        }
    }

    private fun prepareAndSeekVideo(
        clip: MediaClip,
        clipIndex: Int,
        sourcePositionMs: Long,
        expectedGen: Long? = null
    ) {
        if (expectedGen != null && expectedGen != seekGeneration.get()) return

        val path = clip.localPath.ifBlank { clip.uri }
        val file = File(path)

        if ((path.startsWith("/") && !file.exists()) || (file.exists() && file.length() == 0L)) {
            _playbackState.update {
                it.copy(errorMessage = "Mídia não encontrada ou vazia: ${clip.originalName.ifBlank { clip.title }}")
            }
            exoPlayer.pause()
            return
        }

        try {
            val isDifferentClip = loadedClipId != clip.id || exoPlayer.mediaItemCount == 0

            if (isDifferentClip) {
                val mediaItem = if (file.exists()) {
                    MediaItem.fromUri(Uri.fromFile(file))
                } else {
                    MediaItem.fromUri(Uri.parse(clip.uri))
                }
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                loadedClipId = clip.id
            }

            val safeSpeed = TimelineUtils.getSafeSpeed(clip)
            exoPlayer.setPlaybackParameters(PlaybackParameters(safeSpeed))

            val volume = clip.volume.coerceIn(0f, 1f)
            exoPlayer.volume = volume

            val clampedSourcePosition = sourcePositionMs.coerceIn(
                TimelineUtils.getEffectiveTrimStart(clip),
                TimelineUtils.getEffectiveTrimEnd(clip)
            )
            exoPlayer.seekTo(clampedSourcePosition)

            if (_playbackState.value.isPlaying) {
                exoPlayer.play()
            }
        } catch (e: Exception) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = "Erro ao carregar mídia: ${e.localizedMessage ?: "Falha no codec"}"
                )
            }
        }
    }

    private fun handleClipEnded() {
        val currentIndex = _playbackState.value.currentClipIndex
        val nextIndex = TimelineUtils.getNextClipIndex(activeClips, currentIndex)

        if (nextIndex != null) {
            loadNextClip(nextIndex)
        } else {
            val totalDuration = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
            val currentPos = _playbackState.value.currentPositionMs

            // Se houver faixas de áudio estendendo além do último clipe de vídeo, continue tocando
            if (currentPos < totalDuration) {
                _playbackState.update {
                    it.copy(
                        currentClipId = null,
                        currentClipIndex = -1,
                        isPhotoActive = false,
                        activePhotoPath = null
                    )
                }
                exoPlayer.pause()
                lastPhotoTickTime = System.currentTimeMillis()
            } else {
                pause()
                _playbackState.update { it.copy(currentPositionMs = totalDuration) }
                onTimelinePositionChanged?.invoke(totalDuration)
            }
        }
    }

    private fun loadNextClip(nextIndex: Int) {
        if (nextIndex !in activeClips.indices) return
        val nextClip = activeClips[nextIndex]
        val nextStartTimeline = TimelineUtils.getClipStartTimelineMs(activeClips, nextIndex)

        _playbackState.update {
            it.copy(
                currentClipId = nextClip.id,
                currentClipIndex = nextIndex,
                currentPositionMs = nextStartTimeline
            )
        }
        onTimelinePositionChanged?.invoke(nextStartTimeline)

        if (nextClip.type == MediaType.PHOTO) {
            exoPlayer.pause()
            _playbackState.update {
                it.copy(
                    isPhotoActive = true,
                    activePhotoPath = nextClip.localPath.ifBlank { nextClip.thumbnailPath.ifBlank { nextClip.uri } }
                )
            }
            lastPhotoTickTime = System.currentTimeMillis()
        } else {
            _playbackState.update { it.copy(isPhotoActive = false, activePhotoPath = null) }
            val trimStart = TimelineUtils.getEffectiveTrimStart(nextClip)
            val currentGen = seekGeneration.incrementAndGet()
            confirmedSeekGeneration = currentGen
            prepareAndSeekVideo(nextClip, nextIndex, trimStart, currentGen)
        }

        audioSyncManager.syncWithMasterPlayhead(nextStartTimeline, isMasterPlaying = _playbackState.value.isPlaying)
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = coroutineScope.launch {
            while (isActive) {
                delay(25) // ~40 fps polling
                if (!_playbackState.value.isPlaying) continue

                // Se houver uma requisição de seek em andamento, protege currentTimeMs contra coordenadas antigas
                if (seekGeneration.get() != confirmedSeekGeneration) {
                    val currentIndex = _playbackState.value.currentClipIndex
                    if (currentIndex in activeClips.indices && activeClips[currentIndex].type != MediaType.PHOTO) {
                        val info = TimelineUtils.findClipAtTimelinePosition(activeClips, _playbackState.value.currentPositionMs)
                        val expectedSource = info?.sourcePositionMs ?: 0L
                        val currentExo = exoPlayer.currentPosition
                        // Se o exoPlayer já chegou próximo ou saiu do estado de buffering, confirma o seek
                        if (abs(currentExo - expectedSource) < 300L || !exoPlayer.isLoading) {
                            confirmedSeekGeneration = seekGeneration.get()
                        } else {
                            continue
                        }
                    } else {
                        confirmedSeekGeneration = seekGeneration.get()
                    }
                }

                val currentIndex = _playbackState.value.currentClipIndex

                if (currentIndex in activeClips.indices) {
                    val clip = activeClips[currentIndex]

                    if (clip.type == MediaType.PHOTO) {
                        val now = System.currentTimeMillis()
                        val deltaMs = now - lastPhotoTickTime
                        lastPhotoTickTime = now

                        val currentPos = _playbackState.value.currentPositionMs
                        val nextPos = currentPos + deltaMs
                        val clipStart = TimelineUtils.getClipStartTimelineMs(activeClips, currentIndex)
                        val clipDuration = TimelineUtils.calculateClipTimelineDuration(clip)
                        val clipEnd = clipStart + clipDuration

                        if (nextPos >= clipEnd) {
                            handleClipEnded()
                        } else {
                            _playbackState.update { it.copy(currentPositionMs = nextPos) }
                            onTimelinePositionChanged?.invoke(nextPos)
                            audioSyncManager.syncWithMasterPlayhead(nextPos, isMasterPlaying = true)
                        }
                    } else {
                        val sourcePos = exoPlayer.currentPosition
                        val trimEnd = TimelineUtils.getEffectiveTrimEnd(clip)

                        if (sourcePos >= trimEnd) {
                            handleClipEnded()
                        } else {
                            val trimStart = TimelineUtils.getEffectiveTrimStart(clip)
                            val clampedSource = sourcePos.coerceIn(trimStart, trimEnd)
                            val timelinePos = TimelineUtils.sourcePositionToTimelinePosition(
                                activeClips,
                                currentIndex,
                                clampedSource
                            )
                            _playbackState.update { it.copy(currentPositionMs = timelinePos) }
                            onTimelinePositionChanged?.invoke(timelinePos)
                            audioSyncManager.syncWithMasterPlayhead(timelinePos, isMasterPlaying = true)
                        }
                    }
                } else {
                    // Apenas áudio ou reprodução após os clipes
                    val now = System.currentTimeMillis()
                    val deltaMs = now - lastPhotoTickTime
                    lastPhotoTickTime = now

                    val totalDuration = TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios)
                    val nextPos = _playbackState.value.currentPositionMs + deltaMs

                    if (nextPos >= totalDuration) {
                        pause()
                        _playbackState.update { it.copy(currentPositionMs = totalDuration) }
                        onTimelinePositionChanged?.invoke(totalDuration)
                    } else {
                        _playbackState.update { it.copy(currentPositionMs = nextPos) }
                        onTimelinePositionChanged?.invoke(nextPos)
                        audioSyncManager.syncWithMasterPlayhead(nextPos, isMasterPlaying = true)
                    }
                }
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun isCurrentClipPhoto(): Boolean {
        val idx = _playbackState.value.currentClipIndex
        return if (idx in activeClips.indices) activeClips[idx].type == MediaType.PHOTO else false
    }

    fun release() {
        stopTracking()
        hardwareSeekJob?.cancel()
        try {
            exoPlayer.stop()
        } catch (_: Exception) {}
        try {
            exoPlayer.release()
        } catch (_: Exception) {}
        try {
            audioSyncManager.release()
        } catch (_: Exception) {}
        coroutineScope.cancel()
    }
}
