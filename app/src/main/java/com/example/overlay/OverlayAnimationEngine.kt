package com.example.overlay

import kotlin.math.max
import kotlin.math.min

/**
 * Estado espacial e visual resultante de animação temporal de um overlay.
 */
data class OverlayTransformState(
    val scale: Float = 1.0f,
    val alpha: Float = 1.0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val visibleText: String = "",
    val isVisible: Boolean = true
)

/**
 * Motor determinístico de animação temporal baseado exclusivamente no playhead global.
 * Sem timers ou delays independentes, garantindo sincronização perfeita e suporte total a Seek.
 */
object OverlayAnimationEngine {

    /**
     * Calcula o estado transformado de um texto no playhead especificado.
     */
    fun calculateTextState(
        playheadMs: Long,
        startTimeMs: Long,
        durationMs: Long,
        baseScale: Float = 1.0f,
        baseOpacity: Float = 1.0f,
        fullText: String,
        animationIn: String = "Fade",
        animationOut: String = "Fade",
        animationDurationMs: Long = 500L,
        textAnimationMode: String = "Full"
    ): OverlayTransformState {
        val endTimeMs = startTimeMs + durationMs

        // Fora do intervalo temporal
        if (playheadMs < startTimeMs || playheadMs >= endTimeMs) {
            return OverlayTransformState(isVisible = false)
        }

        val effectiveAnimDuration = min(animationDurationMs, durationMs / 2).coerceAtLeast(1L)
        val elapsedMs = playheadMs - startTimeMs
        val remainingMs = endTimeMs - playheadMs

        var scale = baseScale
        var alpha = baseOpacity
        var transX = 0f
        var transY = 0f

        // 1. Animação de Entrada
        if (elapsedMs < effectiveAnimDuration) {
            val progressIn = (elapsedMs.toFloat() / effectiveAnimDuration).coerceIn(0f, 1f)
            val stateIn = applyAnimation(animationIn, progressIn, isEntrance = true)
            scale *= stateIn.scale
            alpha *= stateIn.alpha
            transX += stateIn.translationX
            transY += stateIn.translationY
        }
        // 2. Animação de Saída
        else if (remainingMs < effectiveAnimDuration) {
            val progressOut = (1f - (remainingMs.toFloat() / effectiveAnimDuration)).coerceIn(0f, 1f)
            val stateOut = applyAnimation(animationOut, progressOut, isEntrance = false)
            scale *= stateOut.scale
            alpha *= stateOut.alpha
            transX += stateOut.translationX
            transY += stateOut.translationY
        }

        // 3. Animação de Texto (Full, Word, Letter)
        val visibleText = calculateProgressiveText(
            fullText = fullText,
            mode = textAnimationMode,
            elapsedMs = elapsedMs,
            totalDurationMs = durationMs
        )

        return OverlayTransformState(
            scale = scale,
            alpha = alpha.coerceIn(0f, 1f),
            translationX = transX,
            translationY = transY,
            visibleText = visibleText,
            isVisible = true
        )
    }

    /**
     * Calcula o estado transformado de um sticker ou GIF.
     */
    fun calculateStickerState(
        playheadMs: Long,
        startTimeMs: Long,
        durationMs: Long,
        baseScale: Float = 1.0f,
        baseOpacity: Float = 1.0f,
        animationIn: String = "Fade",
        animationOut: String = "Fade",
        animationDurationMs: Long = 500L
    ): OverlayTransformState {
        val endTimeMs = startTimeMs + durationMs
        if (playheadMs < startTimeMs || playheadMs >= endTimeMs) {
            return OverlayTransformState(isVisible = false)
        }

        val effectiveAnimDuration = min(animationDurationMs, durationMs / 2).coerceAtLeast(1L)
        val elapsedMs = playheadMs - startTimeMs
        val remainingMs = endTimeMs - playheadMs

        var scale = baseScale
        var alpha = baseOpacity
        var transX = 0f
        var transY = 0f

        if (elapsedMs < effectiveAnimDuration) {
            val progressIn = (elapsedMs.toFloat() / effectiveAnimDuration).coerceIn(0f, 1f)
            val stateIn = applyAnimation(animationIn, progressIn, isEntrance = true)
            scale *= stateIn.scale
            alpha *= stateIn.alpha
            transX += stateIn.translationX
            transY += stateIn.translationY
        } else if (remainingMs < effectiveAnimDuration) {
            val progressOut = (1f - (remainingMs.toFloat() / effectiveAnimDuration)).coerceIn(0f, 1f)
            val stateOut = applyAnimation(animationOut, progressOut, isEntrance = false)
            scale *= stateOut.scale
            alpha *= stateOut.alpha
            transX += stateOut.translationX
            transY += stateOut.translationY
        }

        return OverlayTransformState(
            scale = scale,
            alpha = alpha.coerceIn(0f, 1f),
            translationX = transX,
            translationY = transY,
            visibleText = "",
            isVisible = true
        )
    }

    private fun applyAnimation(animName: String, progress: Float, isEntrance: Boolean): OverlayTransformState {
        // progress: 0f = início da animação, 1f = fim da animação
        val p = progress.coerceIn(0f, 1f)

        return when (animName.trim().lowercase()) {
            "fade" -> {
                if (isEntrance) OverlayTransformState(alpha = p)
                else OverlayTransformState(alpha = 1f - p)
            }
            "zoom" -> {
                if (isEntrance) {
                    val s = 0.3f + (0.7f * p)
                    OverlayTransformState(scale = s, alpha = p)
                } else {
                    val s = 1f + (0.5f * p)
                    OverlayTransformState(scale = s, alpha = 1f - p)
                }
            }
            "slide left", "slide_left" -> {
                val distance = 300f
                if (isEntrance) {
                    OverlayTransformState(translationX = (1f - p) * distance, alpha = p)
                } else {
                    OverlayTransformState(translationX = -p * distance, alpha = 1f - p)
                }
            }
            "slide right", "slide_right" -> {
                val distance = 300f
                if (isEntrance) {
                    OverlayTransformState(translationX = -(1f - p) * distance, alpha = p)
                } else {
                    OverlayTransformState(translationX = p * distance, alpha = 1f - p)
                }
            }
            "slide up", "slide_up" -> {
                val distance = 200f
                if (isEntrance) {
                    OverlayTransformState(translationY = (1f - p) * distance, alpha = p)
                } else {
                    OverlayTransformState(translationY = -p * distance, alpha = 1f - p)
                }
            }
            "slide down", "slide_down" -> {
                val distance = 200f
                if (isEntrance) {
                    OverlayTransformState(translationY = -(1f - p) * distance, alpha = p)
                } else {
                    OverlayTransformState(translationY = p * distance, alpha = 1f - p)
                }
            }
            else -> OverlayTransformState(alpha = 1f, scale = 1f) // "None"
        }
    }

    /**
     * Revela palavras ou letras progressivamente conforme a linha de tempo do overlay.
     */
    fun calculateProgressiveText(
        fullText: String,
        mode: String,
        elapsedMs: Long,
        totalDurationMs: Long
    ): String {
        if (fullText.isEmpty()) return ""
        val safeTotal = totalDurationMs.coerceAtLeast(1L)
        // A revelação progressiva ocorre nos primeiros 60% da duração total do overlay
        val typingWindow = (safeTotal * 0.6f).toLong().coerceAtLeast(1L)
        val textProgress = (elapsedMs.toFloat() / typingWindow).coerceIn(0f, 1f)

        return when (mode.trim().lowercase()) {
            "word" -> {
                val words = fullText.split(" ")
                val visibleCount = (words.size * textProgress).toInt().coerceIn(1, words.size)
                words.take(visibleCount).joinToString(" ")
            }
            "letter" -> {
                val visibleChars = (fullText.length * textProgress).toInt().coerceIn(1, fullText.length)
                fullText.take(visibleChars)
            }
            else -> fullText // "Full"
        }
    }
}
