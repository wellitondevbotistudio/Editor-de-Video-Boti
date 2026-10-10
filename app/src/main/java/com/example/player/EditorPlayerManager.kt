package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
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

    // LoadControl otimizado para playback local de vídeo rápido e baixa latência
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            1500, // minBufferMs
            4000, // maxBufferMs
            500,  // bufferForPlaybackMs
            1000  // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setSeekParameters(SeekParameters.EXACT)
        .build().apply {
            playWhenReady = false
            setSeekParameters(SeekParameters.EXACT)
        }

    // Instância estável e reutilizável de PlayerView para evitar reinflações de layout e cortes de codec
    var cachedPlayerView: PlayerView? = null
        private set

    fun getOrCreatePlayerView(ctx: Context): PlayerView {
        val existing = cachedPlayerView
        if (existing != null && existing.context == ctx) {
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            if (existing.player != exoPlayer) {
                existing.player = exoPlayer
            }
            return existing
        }
        val newView = android.view.LayoutInflater.from(ctx).inflate(
            com.example.R.layout.media3_player_view_texture,
            null
        ) as PlayerView
        newView.player = exoPlayer
        cachedPlayerView = newView
        return newView
    }

    val audioSyncManager: AudioSyncManager = AudioSyncManager(context)

    // Flow leve dedicado para atualizações de alta frequência do playhead
    private val _highResPositionMs = MutableStateFlow(0L)
    val highResPositionMs: StateFlow<Long> = _highResPositionMs.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeClips: List<MediaClip> = emptyList()
    private var activeAudios: List<AudioTrackItem> = emptyList()
    private var activeTexts: List<com.example.model.TextOverlayItem> = emptyList()
    private var activeStickers: List<com.example.model.StickerItem> = emptyList()
    private var activeVfx: List<com.example.model.VFXEffectItem> = emptyList()

    private var trackingJob: Job? = null
    private var hardwareSeekJob: Job? = null
    private var lastPhotoTickTime: Long = 0L
    private var lastMasterTickTime: Long = 0L
    @Volatile
    private var isInterClipSwitching: Boolean = false

    // Token / geração de seek para invalidar respostas assíncronas defasadas
    private val seekGeneration = AtomicLong(0L)
    @Volatile
    private var confirmedSeekGeneration: Long = 0L

    // ID do clipe atualmente carregado no ExoPlayer
    private var loadedClipId: String? = null
    private var loadedMediaPath: String? = null
    var isVideoMuted: Boolean = false

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
                    if (_playbackState.value.currentClipIndex >= 0) {
                        handleClipEnded()
                    }
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
                    // Não pausa a reprodução mestre se o player estiver apenas bufferizando entre cortes
                    if (!isPlaying && exoPlayer.playWhenReady) {
                        return
                    }
                    if (!isInterClipSwitching) {
                        _playbackState.update { it.copy(isPlaying = isPlaying) }
                    }
                }
            }
        })
    }

    fun setClipsAndAudios(
        clips: List<MediaClip>,
        audios: List<AudioTrackItem>,
        texts: List<com.example.model.TextOverlayItem> = emptyList(),
        stickers: List<com.example.model.StickerItem> = emptyList(),
        vfx: List<com.example.model.VFXEffectItem> = emptyList(),
        initialSeekPlayhead: Long? = null
    ) {
        activeClips = clips
        activeAudios = audios
        activeTexts = texts
        activeStickers = stickers
        activeVfx = vfx
        val totalDuration = TimelineUtils.calculateTotalProjectDuration(clips, audios, texts, stickers, vfx)
        audioSyncManager.setTracks(audios)
        _playbackState.update { it.copy(totalDurationMs = totalDuration) }

        if (clips.isEmpty() && audios.isEmpty() && texts.isEmpty() && stickers.isEmpty() && vfx.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            loadedClipId = null
            loadedMediaPath = null
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

    fun setLayerMuteState(isVideoMuted: Boolean, isAudioMuted: Boolean) {
        this.isVideoMuted = isVideoMuted
        audioSyncManager.isMasterMuted = isAudioMuted
        if (isVideoMuted) {
            exoPlayer.volume = 0f
        } else {
            val currentClip = activeClips.getOrNull(_playbackState.value.currentClipIndex)
            exoPlayer.volume = if (currentClip?.isMuted == true) 0f else (currentClip?.volume?.coerceIn(0f, 1f) ?: 1f)
        }
    }

    fun setClips(clips: List<MediaClip>, initialSeekPlayhead: Long? = null) {
        setClipsAndAudios(clips, activeAudios, activeTexts, activeStickers, activeVfx, initialSeekPlayhead)
    }

    fun setAudios(audios: List<AudioTrackItem>) {
        setClipsAndAudios(activeClips, audios, activeTexts, activeStickers, activeVfx, _playbackState.value.currentPositionMs)
    }

    fun play() {
        if (activeClips.isEmpty() && activeAudios.isEmpty() && activeTexts.isEmpty() && activeStickers.isEmpty() && activeVfx.isEmpty()) return

        val totalDuration = getTotalDuration()
        val currentPos = _playbackState.value.currentPositionMs

        // Se estiver no final da timeline, reinicia do início
        val effectivePos = if (currentPos >= totalDuration && totalDuration > 0L) {
            0L
        } else {
            currentPos
        }

        lastMasterTickTime = System.currentTimeMillis()
        isInterClipSwitching = false

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
        val currentExactPos = _highResPositionMs.value
        _playbackState.update { it.copy(isPlaying = false, currentPositionMs = currentExactPos) }
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
    fun getTotalDuration(): Long {
        return TimelineUtils.calculateTotalProjectDuration(activeClips, activeAudios, activeTexts, activeStickers, activeVfx)
    }

    fun seekTo(timelinePositionMs: Long) {
        val totalDuration = getTotalDuration()
        val clampedPlayhead = timelinePositionMs.coerceIn(0L, totalDuration.coerceAtLeast(0L))
        val currentGen = seekGeneration.incrementAndGet()

        // 1. Atualização IMEDIATA para feedback instantâneo sem atraso
        _highResPositionMs.value = clampedPlayhead
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
        val total = getTotalDuration()
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
            val total = getTotalDuration()
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
            val mediaPath = if (file.exists()) file.absolutePath else clip.uri
            val isDifferentSource = loadedMediaPath != mediaPath || exoPlayer.mediaItemCount == 0

            if (isDifferentSource) {
                isInterClipSwitching = true
                lastMasterTickTime = System.currentTimeMillis()
                val mediaItem = if (file.exists()) {
                    MediaItem.fromUri(Uri.fromFile(file))
                } else {
                    MediaItem.fromUri(Uri.parse(clip.uri))
                }
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                loadedClipId = clip.id
                loadedMediaPath = mediaPath
            } else {
                loadedClipId = clip.id
            }

            val safeSpeed = TimelineUtils.getSafeSpeed(clip)
            exoPlayer.setPlaybackParameters(PlaybackParameters(safeSpeed))

            val targetVolume = if (isVideoMuted || clip.isMuted) 0f else clip.volume.coerceIn(0f, 1f)
            exoPlayer.volume = targetVolume

            val clampedSourcePosition = sourcePositionMs.coerceIn(
                TimelineUtils.getEffectiveTrimStart(clip),
                TimelineUtils.getEffectiveTrimEnd(clip)
            )
            val currentExoPos = exoPlayer.currentPosition
            if (isDifferentSource || kotlin.math.abs(currentExoPos - clampedSourcePosition) > 10L) {
                exoPlayer.seekTo(clampedSourcePosition)
            }
            if (expectedGen != null) {
                confirmedSeekGeneration = expectedGen
            }

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
        if (currentIndex < 0) {
            val totalDuration = getTotalDuration()
            val currentPos = _playbackState.value.currentPositionMs
            if (currentPos >= totalDuration) {
                pause()
                _playbackState.update { it.copy(currentPositionMs = totalDuration) }
                onTimelinePositionChanged?.invoke(totalDuration)
            }
            return
        }

        val nextIndex = TimelineUtils.getNextClipIndex(activeClips, currentIndex)

        if (nextIndex != null) {
            loadNextClip(nextIndex)
        } else {
            val totalDuration = getTotalDuration()
            val totalClipsDuration = TimelineUtils.calculateProjectTimelineDuration(activeClips)

            // Se houver faixas de áudio, textos, stickers ou efeitos estendendo além do último clipe de vídeo:
            // Continua a reprodução da timeline usando o relógio virtual
            if (totalClipsDuration < totalDuration) {
                _playbackState.update {
                    it.copy(
                        currentClipId = null,
                        currentClipIndex = -1,
                        isPhotoActive = false,
                        activePhotoPath = null,
                        currentPositionMs = totalClipsDuration
                    )
                }
                exoPlayer.pause()
                lastPhotoTickTime = System.currentTimeMillis()
                audioSyncManager.syncWithMasterPlayhead(totalClipsDuration, isMasterPlaying = true)
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

        val path = nextClip.localPath.ifBlank { nextClip.uri }
        val file = File(path)
        val mediaPath = if (file.exists()) file.absolutePath else nextClip.uri
        val isDifferentSource = loadedMediaPath != mediaPath
        val trimStart = TimelineUtils.getEffectiveTrimStart(nextClip)

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
            isInterClipSwitching = false
        } else {
            _playbackState.update { it.copy(isPhotoActive = false, activePhotoPath = null) }
            val currentExoPos = exoPlayer.currentPosition
            val isContiguousSameSource = !isDifferentSource && kotlin.math.abs(currentExoPos - trimStart) < 350L

            if (isContiguousSameSource && exoPlayer.isPlaying) {
                // Mesma fonte e corte contínuo (ex: clipe dividido com transição):
                // Continua reproduzindo diretamente sem pausa, sem recarregamento e sem seek!
                loadedClipId = nextClip.id
                val safeSpeed = TimelineUtils.getSafeSpeed(nextClip)
                exoPlayer.setPlaybackParameters(PlaybackParameters(safeSpeed))
                val targetVolume = if (isVideoMuted || nextClip.isMuted) 0f else nextClip.volume.coerceIn(0f, 1f)
                exoPlayer.volume = targetVolume
                isInterClipSwitching = false
            } else {
                isInterClipSwitching = true
                lastMasterTickTime = System.currentTimeMillis()
                val currentGen = seekGeneration.incrementAndGet()
                confirmedSeekGeneration = currentGen
                prepareAndSeekVideo(nextClip, nextIndex, trimStart, currentGen)
            }
        }

        audioSyncManager.syncWithMasterPlayhead(nextStartTimeline, isMasterPlaying = _playbackState.value.isPlaying)
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = coroutineScope.launch {
            var lastUiStateEmitTime = 0L
            var lastAudioSyncTime = 0L

            while (isActive) {
                // Cadência otimizada sincronizada (~30-33ms / ~30fps) para o playhead de alta resolução
                delay(32)
                if (!_playbackState.value.isPlaying) continue

                val now = System.currentTimeMillis()

                // Transição suave inter-clipes sem pausar ou reiniciar a linha do tempo
                if (isInterClipSwitching) {
                    val deltaMs = (now - lastMasterTickTime).coerceIn(0L, 80L)
                    lastMasterTickTime = now

                    val currentPos = _highResPositionMs.value
                    val nextPos = currentPos + deltaMs
                    val totalDuration = getTotalDuration()

                    if (nextPos >= totalDuration) {
                        pause()
                        _highResPositionMs.value = totalDuration
                        _playbackState.update { it.copy(currentPositionMs = totalDuration) }
                        onTimelinePositionChanged?.invoke(totalDuration)
                    } else {
                        _highResPositionMs.value = nextPos
                        // Throttle para atualizações estruturais de UI (250ms) evitando recomposition storm
                        if (now - lastUiStateEmitTime >= 250L) {
                            _playbackState.update { it.copy(currentPositionMs = nextPos) }
                            lastUiStateEmitTime = now
                        }
                        onTimelinePositionChanged?.invoke(nextPos)
                        if (now - lastAudioSyncTime >= 120L) {
                            audioSyncManager.syncWithMasterPlayhead(nextPos, isMasterPlaying = true)
                            lastAudioSyncTime = now
                        }
                        if (exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_READY) {
                            isInterClipSwitching = false
                        }
                    }
                    continue
                }

                // Se houver uma requisição de seek em andamento, protege currentTimeMs contra coordenadas antigas
                if (seekGeneration.get() != confirmedSeekGeneration) {
                    val currentIndex = _playbackState.value.currentClipIndex
                    if (currentIndex in activeClips.indices && activeClips[currentIndex].type != MediaType.PHOTO) {
                        val info = TimelineUtils.findClipAtTimelinePosition(activeClips, _highResPositionMs.value)
                        val expectedSource = info?.sourcePositionMs ?: 0L
                        val currentExo = exoPlayer.currentPosition
                        // Confirma o seek apenas quando o ExoPlayer atinge a posição desejada
                        if (abs(currentExo - expectedSource) < 300L) {
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
                        val deltaMs = now - lastPhotoTickTime
                        lastPhotoTickTime = now

                        val currentPos = _highResPositionMs.value
                        val nextPos = currentPos + deltaMs
                        val clipStart = TimelineUtils.getClipStartTimelineMs(activeClips, currentIndex)
                        val clipDuration = TimelineUtils.calculateClipTimelineDuration(clip)
                        val clipEnd = clipStart + clipDuration

                        if (nextPos >= clipEnd) {
                            handleClipEnded()
                        } else {
                            _highResPositionMs.value = nextPos
                            if (now - lastUiStateEmitTime >= 250L) {
                                _playbackState.update { it.copy(currentPositionMs = nextPos) }
                                lastUiStateEmitTime = now
                            }
                            onTimelinePositionChanged?.invoke(nextPos)
                            if (now - lastAudioSyncTime >= 120L) {
                                audioSyncManager.syncWithMasterPlayhead(nextPos, isMasterPlaying = true)
                                lastAudioSyncTime = now
                            }
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
                            _highResPositionMs.value = timelinePos
                            if (now - lastUiStateEmitTime >= 250L) {
                                _playbackState.update { it.copy(currentPositionMs = timelinePos) }
                                lastUiStateEmitTime = now
                            }
                            onTimelinePositionChanged?.invoke(timelinePos)
                            if (now - lastAudioSyncTime >= 120L) {
                                audioSyncManager.syncWithMasterPlayhead(timelinePos, isMasterPlaying = true)
                                lastAudioSyncTime = now
                            }
                        }
                    }
                } else {
                    // Apenas áudio ou reprodução após os clipes
                    val deltaMs = now - lastPhotoTickTime
                    lastPhotoTickTime = now

                    val totalDuration = getTotalDuration()
                    val nextPos = _highResPositionMs.value + deltaMs

                    if (nextPos >= totalDuration) {
                        pause()
                        _highResPositionMs.value = totalDuration
                        _playbackState.update { it.copy(currentPositionMs = totalDuration) }
                        onTimelinePositionChanged?.invoke(totalDuration)
                    } else {
                        _highResPositionMs.value = nextPos
                        if (now - lastUiStateEmitTime >= 250L) {
                            _playbackState.update { it.copy(currentPositionMs = nextPos) }
                            lastUiStateEmitTime = now
                        }
                        onTimelinePositionChanged?.invoke(nextPos)
                        if (now - lastAudioSyncTime >= 120L) {
                            audioSyncManager.syncWithMasterPlayhead(nextPos, isMasterPlaying = true)
                            lastAudioSyncTime = now
                        }
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
        cachedPlayerView?.player = null
        cachedPlayerView = null
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
