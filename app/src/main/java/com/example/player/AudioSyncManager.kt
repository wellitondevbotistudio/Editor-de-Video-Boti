package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioTrackItem
import com.example.util.TimelineUtils
import java.io.File
import kotlin.math.abs

class AudioSyncManager(private val context: Context) {

    private val trackPlayers = mutableMapOf<String, ExoPlayer>()
    private var activeTracks: List<AudioTrackItem> = emptyList()
    var onAudioTrackError: ((String, String) -> Unit)? = null

    var isMasterMuted: Boolean = false
        set(value) {
            field = value
            trackPlayers.forEach { (id, player) ->
                val track = activeTracks.find { it.id == id }
                player.volume = if (value || track?.isMuted == true) 0f else (track?.volume?.coerceIn(0f, 1f) ?: 0.8f)
            }
        }

    fun setTracks(tracks: List<AudioTrackItem>) {
        activeTracks = tracks
        val activeIds = tracks.map { it.id }.toSet()

        // Release players for tracks that were removed
        val removedIds = trackPlayers.keys.filterNot { it in activeIds }
        removedIds.forEach { id ->
            trackPlayers[id]?.stop()
            trackPlayers[id]?.release()
            trackPlayers.remove(id)
        }

        // Pre-configure existing players with updated volume/mute
        tracks.forEach { track ->
            trackPlayers[track.id]?.let { player ->
                val targetVolume = if (isMasterMuted || track.isMuted) 0f else track.volume.coerceIn(0f, 1f)
                player.volume = targetVolume
            }
        }
    }

    fun updateTrackVolumeAndMute(trackId: String, volume: Float, isMuted: Boolean) {
        val targetVolume = if (isMasterMuted || isMuted) 0f else volume.coerceIn(0f, 1f)
        trackPlayers[trackId]?.volume = targetVolume
    }

    fun syncWithMasterPlayhead(playheadMs: Long, isMasterPlaying: Boolean) {
        if (activeTracks.isEmpty()) return

        for (track in activeTracks) {
            val file = if (track.localPath.isNotBlank()) File(track.localPath) else null

            if (file == null || !file.exists()) {
                // If file doesn't exist, ensure any existing player is stopped
                trackPlayers[track.id]?.pause()
                if (file != null && !file.exists() && track.localPath.isNotBlank()) {
                    onAudioTrackError?.invoke(track.id, "Arquivo de áudio não encontrado: ${track.name}")
                }
                continue
            }

            val isActive = TimelineUtils.isAudioActiveAtTimelinePosition(track, playheadMs)
            val desiredSourceMs = TimelineUtils.timelinePositionToAudioSourcePosition(track, playheadMs)

            if (isActive && desiredSourceMs != null) {
                val player = getOrCreatePlayer(track, file)
                val targetVolume = if (isMasterMuted || track.isMuted) 0f else track.volume.coerceIn(0f, 1f)
                player.volume = targetVolume

                // Check sync drift between audio player and master playhead
                val currentPos = player.currentPosition
                val diff = desiredSourceMs - currentPos
                val drift = abs(diff)

                // Deadband inteligente de 150ms: variações pequenas são imperceptíveis ao ouvido humano.
                // Variações médias (150ms a 700ms) durante playback contínuo recebem aceleração/desaceleração suave (0.97x - 1.03x)
                // sem reiniciar o buffer ou disparar seek que causa engasgos.
                if (isMasterPlaying) {
                    when {
                        drift < 150L -> {
                            // Dentro da deadband: velocidade nominal 1.0f
                            if (player.playbackParameters.speed != 1.0f) {
                                player.playbackParameters = PlaybackParameters(1.0f)
                            }
                        }
                        drift in 150L..700L -> {
                            // Correção suave de pitch/tempo sem cortes no áudio
                            val correctiveSpeed = if (diff > 0) 1.03f else 0.97f
                            if (player.playbackParameters.speed != correctiveSpeed) {
                                player.playbackParameters = PlaybackParameters(correctiveSpeed)
                            }
                        }
                        else -> {
                            // Desvio crítico (> 700ms): seek pontual com restauração de velocidade
                            player.playbackParameters = PlaybackParameters(1.0f)
                            if (player.playbackState != Player.STATE_BUFFERING) {
                                player.seekTo(desiredSourceMs)
                            }
                        }
                    }
                } else {
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = PlaybackParameters(1.0f)
                    }
                    if (drift > 60L && player.playbackState != Player.STATE_BUFFERING) {
                        player.seekTo(desiredSourceMs)
                    }
                }

                if (isMasterPlaying) {
                    if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
                        player.play()
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } else {
                // Track is outside its timeline active window
                trackPlayers[track.id]?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    }
                    if (playheadMs < track.timelineStartMs) {
                        val start = TimelineUtils.getEffectiveAudioTrimStart(track)
                        if (player.currentPosition != start) {
                            player.seekTo(start)
                        }
                    }
                }
            }
        }
    }

    fun seekTo(playheadMs: Long, isMasterPlaying: Boolean) {
        for (track in activeTracks) {
            val file = if (track.localPath.isNotBlank()) File(track.localPath) else null
            if (file == null || !file.exists()) {
                trackPlayers[track.id]?.pause()
                continue
            }

            val isActive = TimelineUtils.isAudioActiveAtTimelinePosition(track, playheadMs)
            val desiredSourceMs = TimelineUtils.timelinePositionToAudioSourcePosition(track, playheadMs)

            if (isActive && desiredSourceMs != null) {
                val player = getOrCreatePlayer(track, file)
                val targetVolume = if (isMasterMuted || track.isMuted) 0f else track.volume.coerceIn(0f, 1f)
                player.volume = targetVolume
                player.seekTo(desiredSourceMs)

                if (isMasterPlaying) {
                    player.play()
                } else {
                    player.pause()
                }
            } else {
                trackPlayers[track.id]?.let { player ->
                    player.pause()
                    if (playheadMs < track.timelineStartMs) {
                        player.seekTo(TimelineUtils.getEffectiveAudioTrimStart(track))
                    }
                }
            }
        }
    }

    fun pauseAll() {
        for ((_, player) in trackPlayers) {
            if (player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun getOrCreatePlayer(track: AudioTrackItem, file: File): ExoPlayer {
        return trackPlayers.getOrPut(track.id) {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = false
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                setMediaItem(mediaItem)
                prepare()
                volume = if (track.isMuted) 0f else track.volume.coerceIn(0f, 1f)
            }
        }
    }

    fun release() {
        pauseAll()
        for ((_, player) in trackPlayers) {
            player.stop()
            player.release()
        }
        trackPlayers.clear()
        activeTracks = emptyList()
    }
}
