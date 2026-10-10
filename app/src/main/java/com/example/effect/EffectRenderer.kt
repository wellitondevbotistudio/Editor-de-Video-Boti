package com.example.effect

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

    // Pré-computa o RenderEffect uma única vez quando as cores mudam,
    // eliminando alocações contínuas de ColorMatrixColorFilter e Skia RenderEffect por frame de desenho
    val renderEffectModifier = remember(androidColorMatrix, effectState.hasAdjustments, effectState.hasFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (effectState.hasAdjustments || effectState.hasFilter)) {
            try {
                val filter = ColorMatrixColorFilter(androidColorMatrix)
                RenderEffect.createColorFilterEffect(filter).asComposeRenderEffect()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

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
                renderEffect = renderEffectModifier
            }
    ) {
        // Recorte visual do elemento se cropRatio estiver ativo
        val baseCropAspect = when (effectState.cropRatio) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:5" -> 4f / 5f
            "4:3" -> 4f / 3f
            else -> null
        }

        // Verifica se a rotação é ortogonal ímpar (90°, 270°, etc.)
        val normalizedRot = ((effectState.rotation % 360f) + 360f) % 360f
        val isRotatedOdd = (normalizedRot in 45f..135f) || (normalizedRot in 225f..315f)

        // Se a rotação for de 90° ou 270°, inverte a razão de aspecto alvo mantendo a proporção de corte sem distorção
        val cropAspect = if (baseCropAspect != null && isRotatedOdd) {
            1f / baseCropAspect
        } else {
            baseCropAspect
        }

        if (cropAspect != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(cropAspect)
                        .clipToBounds()
                ) {
                    content(composeColorMatrix)
                }
            }
        } else {
            // Conteúdo da mídia sem corte proporcional
            content(composeColorMatrix)
        }

        // Overlay dos efeitos VFX ativos
        if (effectState.activeVfx.isNotEmpty()) {
            VfxOverlayRenderer(activeVfxList = effectState.activeVfx)
        }
    }
}
