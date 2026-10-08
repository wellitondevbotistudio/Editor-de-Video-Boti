package com.example.transition

import com.example.model.MediaClip
import com.example.util.TimelineUtils
import kotlin.math.min

/**
 * Estado geométrico e visual de um clipe durante uma transição.
 */
data class ClipTransitionTransform(
    val alpha: Float = 1.0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1.0f,
    val wipeProgress: Float = 1.0f // 1.0f = totalmente visível
)

/**
 * Informações ativas de uma transição ocorrendo no playhead atual.
 */
data class ActiveTransitionInfo(
    val clipAIndex: Int,
    val clipBIndex: Int,
    val clipA: MediaClip,
    val clipB: MediaClip,
    val type: TransitionType,
    val progress: Float, // 0.0f (início) .. 1.0f (fim)
    val transformA: ClipTransitionTransform,
    val transformB: ClipTransitionTransform
)

object TransitionEngine {

    /**
     * Limita com segurança a duração da transição com base na duração dos clipes envolvidos.
     */
    fun clampTransitionDuration(
        clipADurationMs: Long,
        clipBDurationMs: Long,
        requestedDurationMs: Long
    ): Long {
        val maxAllowed = min(clipADurationMs, clipBDurationMs) / 2
        return requestedDurationMs.coerceIn(100L, maxAllowed.coerceAtLeast(100L))
    }

    /**
     * Descobre se o playhead atual está dentro da janela de uma transição entre dois clipes consecutivos.
     */
    fun findActiveTransition(
        clips: List<MediaClip>,
        playheadMs: Long
    ): ActiveTransitionInfo? {
        if (clips.size < 2) return null

        for (i in 0 until clips.size - 1) {
            val clipA = clips[i]
            val clipB = clips[i + 1]

            val transitionName = clipA.transition
            if (transitionName.isNullOrBlank() || transitionName.equals("nenhuma", ignoreCase = true) || transitionName.equals("corte seco", ignoreCase = true)) {
                continue
            }

            val type = TransitionType.fromName(transitionName)
            if (type == TransitionType.CUT) continue

            val clipAStart = TimelineUtils.getClipStartTimelineMs(clips, i)
            val clipADuration = TimelineUtils.calculateClipTimelineDuration(clipA)
            val clipBDuration = TimelineUtils.calculateClipTimelineDuration(clipB)

            val splitPoint = clipAStart + clipADuration
            val effectiveDuration = clampTransitionDuration(
                clipADurationMs = clipADuration,
                clipBDurationMs = clipBDuration,
                requestedDurationMs = clipA.transitionDurationMs
            )

            val halfDuration = effectiveDuration / 2
            val transitionStart = splitPoint - halfDuration
            val transitionEnd = splitPoint + halfDuration

            if (playheadMs in transitionStart until transitionEnd) {
                val progress = ((playheadMs - transitionStart).toFloat() / effectiveDuration).coerceIn(0f, 1f)
                val (transformA, transformB) = calculateTransforms(type, progress)

                return ActiveTransitionInfo(
                    clipAIndex = i,
                    clipBIndex = i + 1,
                    clipA = clipA,
                    clipB = clipB,
                    type = type,
                    progress = progress,
                    transformA = transformA,
                    transformB = transformB
                )
            }
        }

        return null
    }

    /**
     * Calcula as transformações geométricas e alfas de Clip A e Clip B com base no tipo e progresso.
     */
    fun calculateTransforms(
        type: TransitionType,
        progress: Float
    ): Pair<ClipTransitionTransform, ClipTransitionTransform> {
        val p = progress.coerceIn(0f, 1f)

        return when (type) {
            TransitionType.CUT -> {
                if (p < 0.5f) {
                    ClipTransitionTransform(alpha = 1f) to ClipTransitionTransform(alpha = 0f)
                } else {
                    ClipTransitionTransform(alpha = 0f) to ClipTransitionTransform(alpha = 1f)
                }
            }

            TransitionType.DISSOLVE -> {
                // Crossfade suave
                val a = ClipTransitionTransform(alpha = 1f - p)
                val b = ClipTransitionTransform(alpha = p)
                a to b
            }

            TransitionType.FADE -> {
                // Dip to black no meio (p = 0.5f)
                val alphaA = if (p < 0.5f) (1f - p * 2f).coerceIn(0f, 1f) else 0f
                val alphaB = if (p >= 0.5f) ((p - 0.5f) * 2f).coerceIn(0f, 1f) else 0f
                ClipTransitionTransform(alpha = alphaA) to ClipTransitionTransform(alpha = alphaB)
            }

            TransitionType.SLIDE_LEFT -> {
                // A sai pela esquerda, B entra pela direita
                val a = ClipTransitionTransform(translationX = -p * 350f, alpha = 1f)
                val b = ClipTransitionTransform(translationX = (1f - p) * 350f, alpha = 1f)
                a to b
            }

            TransitionType.SLIDE_RIGHT -> {
                // A sai pela direita, B entra pela esquerda
                val a = ClipTransitionTransform(translationX = p * 350f, alpha = 1f)
                val b = ClipTransitionTransform(translationX = -(1f - p) * 350f, alpha = 1f)
                a to b
            }

            TransitionType.SLIDE_UP -> {
                // A sobe, B entra por baixo
                val a = ClipTransitionTransform(translationY = -p * 500f, alpha = 1f)
                val b = ClipTransitionTransform(translationY = (1f - p) * 500f, alpha = 1f)
                a to b
            }

            TransitionType.SLIDE_DOWN -> {
                // A desce, B entra por cima
                val a = ClipTransitionTransform(translationY = p * 500f, alpha = 1f)
                val b = ClipTransitionTransform(translationY = -(1f - p) * 500f, alpha = 1f)
                a to b
            }

            TransitionType.ZOOM -> {
                // A dá zoom in e some, B dá zoom in a partir de menor escala
                val scaleA = 1f + (p * 0.4f)
                val scaleB = 0.6f + (p * 0.4f)
                val a = ClipTransitionTransform(scale = scaleA, alpha = 1f - p)
                val b = ClipTransitionTransform(scale = scaleB, alpha = p)
                a to b
            }

            TransitionType.WIPE -> {
                // Revelação de cortina horizontal (Wipe progress)
                val a = ClipTransitionTransform(alpha = 1f - (p * 0.3f), wipeProgress = 1f - p)
                val b = ClipTransitionTransform(alpha = p, wipeProgress = p)
                a to b
            }
        }
    }
}
