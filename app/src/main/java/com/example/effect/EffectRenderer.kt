package com.example.effect

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Componente arquitetural central de renderização de efeitos e transformações.
 *
 * Aplica:
 * 1. Transformações espaciais reais (escala/zoom, rotação, flip H/V, opacidade, posição X/Y).
 * 2. Pipeline de matriz de cor (brilho, contraste, saturação, filtro de cor).
 * 3. Renderização de efeitos visuais (VFX overlays).
 */
@Composable
fun TransformedMediaContainer(
    effectState: ClipEffectState,
    modifier: Modifier = Modifier,
    content: @Composable (composeColorMatrix: androidx.compose.ui.graphics.ColorMatrix) -> Unit
) {
    // Calcula as matrizes de cor através do pipeline unificado
    val androidColorMatrix = remember(
        effectState.brightness,
        effectState.contrast,
        effectState.saturation,
        effectState.filter
    ) {
        ColorMatrixPipeline.composeColorMatrix(
            brightness = effectState.brightness,
            contrast = effectState.contrast,
            saturation = effectState.saturation,
            filterName = effectState.filter
        )
    }

    val composeColorMatrix = remember(androidColorMatrix) {
        ColorMatrixPipeline.toComposeColorMatrix(androidColorMatrix)
    }

    val finalScaleX = effectState.scale * (if (effectState.flipHorizontal) -1f else 1f)
    val finalScaleY = effectState.scale * (if (effectState.flipVertical) -1f else 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = finalScaleX
                scaleY = finalScaleY
                rotationZ = effectState.rotation
                alpha = effectState.opacity.coerceIn(0f, 1f)
                translationX = effectState.positionX
                translationY = effectState.positionY

                // Em dispositivos com Android 12+ (API 31+), o RenderEffect aplica a ColorMatrix
                // diretamente ao hardware layer da View (incluindo PlayerView/TextureView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val filter = ColorMatrixColorFilter(androidColorMatrix)
                        renderEffect = RenderEffect.createColorFilterEffect(filter).asComposeRenderEffect()
                    } catch (_: Exception) {
                        // Fallback suave caso o dispositivo não suporte RenderEffect em hardware
                    }
                }
            }
    ) {
        // Conteúdo da mídia (Video PlayerView ou AsyncImage para fotos)
        content(composeColorMatrix)

        // Overlay dos efeitos VFX ativos
        if (effectState.activeVfx.isNotEmpty()) {
            VfxOverlayRenderer(activeVfxList = effectState.activeVfx)
        }
    }
}
