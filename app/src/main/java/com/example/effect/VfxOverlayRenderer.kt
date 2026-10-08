package com.example.effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VFXEffectItem

/**
 * Renderizador de efeitos visuais (VFX) reais sobre a mídia no preview.
 * Os efeitos reagem diretamente ao parâmetro de intensidade (0% a 100%).
 */
@Composable
fun VfxOverlayRenderer(
    activeVfxList: List<VFXEffectItem>,
    modifier: Modifier = Modifier
) {
    if (activeVfxList.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {
        activeVfxList.forEach { effect ->
            if (effect.isEnabled) {
                val intensityFactor = (effect.intensity / 100f).coerceIn(0f, 1f)

                when (effect.name.trim()) {
                    "Vinheta" -> VignetteEffect(intensity = intensityFactor)
                    "Luz Vazada" -> LightLeakEffect(intensity = intensityFactor)
                    "Glitch" -> GlitchEffect(intensity = intensityFactor)
                    "RGB Split" -> RgbSplitEffect(intensity = intensityFactor)
                    "VHS 90s" -> VhsEffect(intensity = intensityFactor)
                    "Bokeh Glow" -> BokehGlowEffect(intensity = intensityFactor)
                    "Pixel Art" -> PixelArtEffect(intensity = intensityFactor)
                    "Desfoque" -> SoftBlurEffect(intensity = intensityFactor)
                    "Shake" -> ShakeEffect(intensity = intensityFactor)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun VignetteEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width.coerceAtLeast(size.height) / 1.4f)
        val alpha = (0.75f * intensity).coerceIn(0f, 0.95f)

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = alpha * 0.4f),
                    Color.Black.copy(alpha = alpha)
                ),
                center = center,
                radius = radius
            )
        )
    }
}

@Composable
private fun LightLeakEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = (0.55f * intensity).coerceIn(0f, 0.85f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9800).copy(alpha = alpha),
                    Color(0xFFFF5722).copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.15f),
                radius = size.width * 0.8f
            ),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun GlitchEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = (0.45f * intensity).coerceIn(0f, 0.7f)
        val sliceCount = (6 * intensity).toInt().coerceAtLeast(2)

        for (i in 0 until sliceCount) {
            val y = (size.height / sliceCount) * i
            val h = size.height / (sliceCount * 3f)
            val color = if (i % 2 == 0) Color.Cyan else Color.Magenta

            drawRect(
                color = color.copy(alpha = alpha * 0.5f),
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun RgbSplitEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val offsetPx = 8f * intensity
        val alpha = (0.35f * intensity).coerceIn(0f, 0.6f)

        // Red fringe
        drawRect(
            color = Color.Red.copy(alpha = alpha),
            topLeft = Offset(-offsetPx, 0f),
            size = size,
            blendMode = BlendMode.Screen
        )

        // Cyan fringe
        drawRect(
            color = Color.Cyan.copy(alpha = alpha),
            topLeft = Offset(offsetPx, 0f),
            size = size,
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
private fun VhsEffect(intensity: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val alpha = (0.3f * intensity).coerceIn(0f, 0.6f)
            val step = 4.dp.toPx()

            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.Black.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
        }

        // 90s VHS Camcorder watermark
        Text(
            text = "PLAY ▶ SP\n00:19:94",
            color = Color(0xFF00FF66).copy(alpha = (0.7f * intensity).coerceIn(0.2f, 0.9f)),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

@Composable
private fun BokehGlowEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = (0.35f * intensity).coerceIn(0f, 0.65f)
        val orbs = listOf(
            Triple(0.25f, 0.35f, 45.dp.toPx()),
            Triple(0.70f, 0.60f, 60.dp.toPx()),
            Triple(0.40f, 0.75f, 35.dp.toPx()),
            Triple(0.80f, 0.25f, 50.dp.toPx())
        )

        orbs.forEach { (relX, relY, radius) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE0E7FF).copy(alpha = alpha),
                        Color(0xFFC7D2FE).copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * relX, size.height * relY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width * relX, size.height * relY),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
private fun PixelArtEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = (0.35f * intensity).coerceIn(0f, 0.55f)
        val gridSize = 10.dp.toPx()

        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Color.Black.copy(alpha = alpha),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            x += gridSize
        }

        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += gridSize
        }
    }
}

@Composable
private fun SoftBlurEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = (0.28f * intensity).coerceIn(0f, 0.5f)
        drawRect(
            color = Color.White.copy(alpha = alpha),
            blendMode = BlendMode.Overlay
        )
    }
}

@Composable
private fun ShakeEffect(intensity: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Visual motion blur trail indicating camera shake
        val offset = 6f * intensity
        drawRect(
            color = Color.Black.copy(alpha = 0.15f * intensity),
            topLeft = Offset(offset, offset),
            size = size,
            blendMode = BlendMode.Multiply
        )
    }
}
