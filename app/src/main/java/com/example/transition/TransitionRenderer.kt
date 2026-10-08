package com.example.transition

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.effect.ClipEffectState
import com.example.effect.TransformedMediaContainer
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.model.VFXEffectItem
import java.io.File

/**
 * Renderizador de transições reais entre clips de vídeo e imagem.
 */
@Composable
fun TransitionAwareMediaSurface(
    clips: List<MediaClip>,
    activeVFX: List<VFXEffectItem>,
    currentPlayheadMs: Long,
    activeClip: MediaClip?,
    isPhotoActive: Boolean,
    activePhotoPath: String?,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    modifier: Modifier = Modifier,
    isVideoVisible: Boolean = true,
    isVfxVisible: Boolean = true,
    playerViewProvider: ((android.content.Context) -> PlayerView)? = null
) {
    if (!isVideoVisible) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val effectiveVFX = remember(activeVFX, isVfxVisible, currentPlayheadMs / 100) {
        if (isVfxVisible) {
            activeVFX.filter { effect ->
                effect.isEnabled && currentPlayheadMs >= effect.startTimeMs && currentPlayheadMs < (effect.startTimeMs + effect.durationMs)
            }
        } else emptyList()
    }
    val activeTransition = remember(clips, currentPlayheadMs / 50) {
        TransitionEngine.findActiveTransition(clips, currentPlayheadMs)
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (activeTransition != null) {
            // Renderiza Clip A com transformação da transição
            val effectStateA = ClipEffectState.fromClip(activeTransition.clipA, effectiveVFX)
            val transformA = activeTransition.transformA

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = transformA.alpha
                        translationX = transformA.translationX
                        translationY = transformA.translationY
                        scaleX = transformA.scale
                        scaleY = transformA.scale
                    }
            ) {
                SingleClipMediaView(
                    clip = activeTransition.clipA,
                    effectState = effectStateA,
                    isPlayerActive = activeClip?.id == activeTransition.clipA.id,
                    exoPlayer = exoPlayer,
                    playerViewProvider = playerViewProvider
                )
            }

            // Renderiza Clip B com transformação da transição
            val effectStateB = ClipEffectState.fromClip(activeTransition.clipB, effectiveVFX)
            val transformB = activeTransition.transformB

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = transformB.alpha
                        translationX = transformB.translationX
                        translationY = transformB.translationY
                        scaleX = transformB.scale
                        scaleY = transformB.scale
                    }
            ) {
                SingleClipMediaView(
                    clip = activeTransition.clipB,
                    effectState = effectStateB,
                    isPlayerActive = activeClip?.id == activeTransition.clipB.id,
                    exoPlayer = exoPlayer,
                    playerViewProvider = playerViewProvider
                )
            }
        } else if (activeClip != null) {
            // Renderização padrão sem transição
            val effectState = ClipEffectState.fromClip(activeClip, effectiveVFX)
            TransformedMediaContainer(
                effectState = effectState,
                modifier = Modifier.fillMaxSize()
            ) { composeColorMatrix ->
                if (isPhotoActive || activeClip.type == MediaType.PHOTO) {
                    val photoModel = when {
                        activePhotoPath?.isNotBlank() == true -> File(activePhotoPath)
                        activeClip.localPath.isNotBlank() -> File(activeClip.localPath)
                        else -> activeClip.uri
                    }
                    AsyncImage(
                        model = photoModel,
                        contentDescription = "Foto",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeColorMatrix),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            playerViewProvider?.invoke(ctx) ?: LayoutInflater.from(ctx).inflate(
                                R.layout.media3_player_view_texture,
                                null
                            ) as PlayerView
                        },
                        update = { playerView ->
                            if (playerView.player != exoPlayer) {
                                playerView.player = exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleClipMediaView(
    clip: MediaClip,
    effectState: ClipEffectState,
    isPlayerActive: Boolean,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    playerViewProvider: ((android.content.Context) -> PlayerView)? = null
) {
    TransformedMediaContainer(
        effectState = effectState,
        modifier = Modifier.fillMaxSize()
    ) { composeColorMatrix ->
        if (clip.type == MediaType.PHOTO) {
            val model = if (clip.localPath.isNotBlank()) File(clip.localPath) else clip.uri
            AsyncImage(
                model = model,
                contentDescription = clip.title,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeColorMatrix),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            if (isPlayerActive) {
                AndroidView(
                    factory = { ctx ->
                        playerViewProvider?.invoke(ctx) ?: LayoutInflater.from(ctx).inflate(
                            R.layout.media3_player_view_texture,
                            null
                        ) as PlayerView
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Se for clipe anterior/posterior durante transição, renderiza thumbnail ou frame estático
                val thumbModel = when {
                    clip.thumbnailPath.isNotBlank() -> File(clip.thumbnailPath)
                    clip.localPath.isNotBlank() -> File(clip.localPath)
                    else -> clip.uri
                }
                AsyncImage(
                    model = thumbModel,
                    contentDescription = clip.title,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(composeColorMatrix),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
