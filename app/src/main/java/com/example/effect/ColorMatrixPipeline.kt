package com.example.effect

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter

/**
 * Pipeline matemático real de matrizes de cor para processamento de imagem e vídeo.
 *
 * Gera ColorMatrix 4x5 combinando:
 * Brilho + Contraste + Saturação + Filtro Artístico
 *
 * Pronto para uso com Compose Canvas/graphicsLayer, AndroidViews e futuro pipeline de exportação (Etapa 8).
 */
object ColorMatrixPipeline {

    // Luminance constants (ITU-R BT.709)
    const val LUMA_R = 0.213f
    const val LUMA_G = 0.715f
    const val LUMA_B = 0.072f

    private val identityMatrix = ColorMatrix()
    private val identityComposeMatrix = androidx.compose.ui.graphics.ColorMatrix()

    /**
     * Calcula a matriz de brilho real para a faixa [-100 .. +100].
     * 0f é neutro.
     */
    fun createBrightnessMatrix(brightness: Float): ColorMatrix {
        val clamped = brightness.coerceIn(-100f, 100f)
        val shift = (clamped / 100f) * 255f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, shift,
                0f, 1f, 0f, 0f, shift,
                0f, 0f, 1f, 0f, shift,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /**
     * Calcula a matriz de contraste real para a faixa [-100 .. +100].
     * 0f é neutro (escala 1.0f).
     * -100f produz imagem sem contraste (escala 0.0f).
     * +100f aumenta o contraste (escala 2.5f).
     */
    fun createContrastMatrix(contrast: Float): ColorMatrix {
        val clamped = contrast.coerceIn(-100f, 100f)
        val scale = if (clamped >= 0f) {
            1f + (clamped / 100f) * 1.5f
        } else {
            (clamped + 100f) / 100f
        }
        val translate = 128f * (1f - scale)

        return ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /**
     * Calcula a matriz de saturação real para a faixa [-100 .. +100].
     * 0f é neutro (escala 1.0f).
     * -100f produz imagem monocromática real (escala 0.0f).
     * +100f produz imagem hipersaturada (escala 2.0f).
     */
    fun createSaturationMatrix(saturation: Float): ColorMatrix {
        val clamped = saturation.coerceIn(-100f, 100f)
        val scale = if (clamped >= 0f) {
            1f + (clamped / 100f)
        } else {
            (clamped + 100f) / 100f
        }
        val matrix = ColorMatrix()
        matrix.setSaturation(scale)
        return matrix
    }

    /**
     * Retorna a matriz de cor correspondente ao filtro artístico selecionado.
     */
    fun createFilterMatrix(filterName: String): ColorMatrix {
        return when (filterName.trim()) {
            "P&B", "Grayscale", "Preto e Branco" -> ColorMatrix().apply { setSaturation(0f) }

            "Sépia", "Sepia" -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                )
            )

            "Vívido", "Vivid" -> ColorMatrix(
                floatArrayOf(
                    1.25f, 0f,    0f,    0f, -10f,
                    0f,    1.25f, 0f,    0f, -10f,
                    0f,    0f,    1.25f, 0f, -10f,
                    0f,    0f,    0f,    1f, 0f
                )
            ).apply {
                val sat = ColorMatrix()
                sat.setSaturation(1.4f)
                postConcat(sat)
            }

            "Quente", "Warm" -> ColorMatrix(
                floatArrayOf(
                    1.18f, 0f,    0f,    0f, 15f,
                    0f,    1.06f, 0f,    0f, 5f,
                    0f,    0f,    0.85f, 0f, -15f,
                    0f,    0f,    0f,    1f, 0f
                )
            )

            "Frio", "Cool" -> ColorMatrix(
                floatArrayOf(
                    0.88f, 0f,    0f,    0f, -10f,
                    0f,    1.02f, 0f,    0f, 0f,
                    0f,    0f,    1.22f, 0f, 20f,
                    0f,    0f,    0f,    1f, 0f
                )
            )

            "Retrô", "Vintage" -> ColorMatrix(
                floatArrayOf(
                    0.95f, 0.05f, 0.05f, 0f, 25f,
                    0.05f, 0.90f, 0.05f, 0f, 10f,
                    0.05f, 0.05f, 0.75f, 0f, -5f,
                    0f,    0f,    0f,    1f, 0f
                )
            ).apply {
                val sat = ColorMatrix()
                sat.setSaturation(0.85f)
                postConcat(sat)
            }

            "Cyberpunk" -> ColorMatrix(
                floatArrayOf(
                    1.35f, 0f,    0.10f, 0f, 25f,
                    0f,    0.85f, 0f,    0f, -15f,
                    0.15f, 0f,    1.45f, 0f, 30f,
                    0f,    0f,    0f,    1f, 0f
                )
            )

            "Filme", "Film" -> ColorMatrix(
                floatArrayOf(
                    1.08f, 0.02f, 0f,    0f, 10f,
                    0f,    1.05f, 0f,    0f, 0f,
                    0f,    0.05f, 0.95f, 0f, 15f,
                    0f,    0f,    0f,    1f, 0f
                )
            ).apply {
                val sat = ColorMatrix()
                sat.setSaturation(1.15f)
                postConcat(sat)
            }

            "Suave", "Soft" -> ColorMatrix(
                floatArrayOf(
                    0.92f, 0f,    0f,    0f, 18f,
                    0f,    0.92f, 0f,    0f, 18f,
                    0f,    0f,    0.92f, 0f, 18f,
                    0f,    0f,    0f,    1f, 0f
                )
            ).apply {
                val sat = ColorMatrix()
                sat.setSaturation(0.9f)
                postConcat(sat)
            }

            "Golden Hour" -> ColorMatrix(
                floatArrayOf(
                    1.22f, 0.05f, 0f,    0f, 25f,
                    0f,    1.10f, 0f,    0f, 12f,
                    0f,    0f,    0.78f, 0f, -20f,
                    0f,    0f,    0f,    1f, 0f
                )
            )

            else -> ColorMatrix() // Original: matriz identidade neutra
        }
    }

    /**
     * Compõe ordenadamente todas as transformações em uma única matriz 4x5:
     * Composed = Filter * Saturation * Contrast * Brightness
     */
    fun composeColorMatrix(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        filterName: String
    ): ColorMatrix {
        if (brightness == 0f && contrast == 0f && saturation == 0f && (filterName == "Original" || filterName.isBlank())) {
            return identityMatrix
        }

        val result = ColorMatrix()

        if (brightness != 0f) {
            result.postConcat(createBrightnessMatrix(brightness))
        }

        if (contrast != 0f) {
            result.postConcat(createContrastMatrix(contrast))
        }

        if (saturation != 0f) {
            result.postConcat(createSaturationMatrix(saturation))
        }

        if (filterName != "Original" && filterName.isNotBlank()) {
            result.postConcat(createFilterMatrix(filterName))
        }

        return result
    }

    /**
     * Retorna a matriz para uso no Jetpack Compose.
     */
    fun toComposeColorMatrix(androidMatrix: ColorMatrix): androidx.compose.ui.graphics.ColorMatrix {
        if (androidMatrix === identityMatrix) return identityComposeMatrix
        return androidx.compose.ui.graphics.ColorMatrix(androidMatrix.array)
    }

    /**
     * Retorna o ColorFilter pronto para Composables do Jetpack Compose.
     */
    fun toComposeColorFilter(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        filterName: String
    ): ColorFilter {
        val matrix = composeColorMatrix(brightness, contrast, saturation, filterName)
        return ColorFilter.colorMatrix(toComposeColorMatrix(matrix))
    }
}
