package com.example.export

import com.example.model.*
import com.example.overlay.OverlayAnimationEngine
import com.example.overlay.OverlayTransformState
import com.example.transition.ActiveTransitionInfo
import com.example.transition.TransitionEngine
import com.example.util.ClipTimelineInfo
import com.example.util.TimelineUtils

/**
 * Informações avaliadas de um overlay de texto para um frame específico.
 */
data class EvaluatedTextOverlay(
    val item: TextOverlayItem,
    val animState: OverlayTransformState
)

/**
 * Informações avaliadas de um sticker/GIF para um frame específico.
 */
data class EvaluatedSticker(
    val item: StickerItem,
    val animState: OverlayTransformState
)

/**
 * Snapshot temporal completo dos elementos do projeto em um dado instante `timeMs`.
 */
data class ExportFrameSnapshot(
    val timestampMs: Long,
    val activeTransition: ActiveTransitionInfo?,
    val activeClipInfo: ClipTimelineInfo?,
    val activeVfx: List<VFXEffectItem>,
    val activeTexts: List<EvaluatedTextOverlay>,
    val activeStickers: List<EvaluatedSticker>,
    val activeSubtitles: List<SubtitleSegmentItem>,
    val subtitleStyle: SubtitleStyleConfig
)

/**
 * Camada de linha do tempo determinística para exportação, derivada unicamente de [ProjectItem]
 * e compartilhando as exatas mesmas regras de cálculo de [TimelineUtils] e [TransitionEngine].
 */
class ExportTimeline(
    val project: ProjectItem,
    val config: VideoExportConfig
) {
    val totalDurationMs: Long = TimelineUtils.calculateTotalProjectDuration(
        clips = project.clips,
        audios = project.audios
    ).coerceAtLeast(100L)

    val totalFrames: Long = ((totalDurationMs.toDouble() / 1000.0) * config.fps).toLong().coerceAtLeast(1L)

    /**
     * Retorna o timestamp em milissegundos correspondente ao índice do quadro `frameIndex`.
     */
    fun getTimeMsForFrame(frameIndex: Long): Long {
        return ((frameIndex.toDouble() / config.fps) * 1000.0).toLong().coerceIn(0L, totalDurationMs)
    }

    /**
     * Avalia o estado exato de todos os elementos visuais da linha do tempo no instante `timeMs`.
     */
    fun evaluateAt(timeMs: Long): ExportFrameSnapshot {
        val clampedTime = timeMs.coerceIn(0L, totalDurationMs)

        // 1. Transição ativa entre clips
        val activeTransition = TransitionEngine.findActiveTransition(project.clips, clampedTime)

        // 2. Clipe principal ativo caso não haja transição ou como fallback
        val activeClipInfo = TimelineUtils.findClipAtTimelinePosition(project.clips, clampedTime)

        // 3. Efeitos VFX globais ou associados
        val activeVfx = project.activeVFX.filter { it.isEnabled }

        // 4. Overlays de texto com cálculo de animação temporal determinística
        val activeTexts = project.texts
            .filter { it.isVisible }
            .mapNotNull { textItem ->
                val state = OverlayAnimationEngine.calculateTextState(
                    playheadMs = clampedTime,
                    startTimeMs = textItem.startTimeMs,
                    durationMs = textItem.durationMs,
                    baseScale = textItem.scale,
                    baseOpacity = textItem.opacity,
                    fullText = textItem.text,
                    animationIn = textItem.animationIn,
                    animationOut = textItem.animationOut,
                    animationDurationMs = textItem.animationDurationMs,
                    textAnimationMode = textItem.textAnimationMode
                )
                if (state.isVisible) {
                    EvaluatedTextOverlay(item = textItem, animState = state)
                } else null
            }

        // 5. Stickers e GIFs com animação temporal
        val activeStickers = project.stickers
            .filter { it.isVisible }
            .mapNotNull { stickerItem ->
                val state = OverlayAnimationEngine.calculateStickerState(
                    playheadMs = clampedTime,
                    startTimeMs = stickerItem.startTimeMs,
                    durationMs = stickerItem.durationMs,
                    baseScale = stickerItem.scale,
                    baseOpacity = stickerItem.opacity,
                    animationIn = stickerItem.animationIn,
                    animationOut = stickerItem.animationOut,
                    animationDurationMs = stickerItem.animationDurationMs
                )
                if (state.isVisible) {
                    EvaluatedSticker(item = stickerItem, animState = state)
                } else null
            }

        // 6. Subtítulos/Legendas ativas
        val activeSubtitles = project.subtitles.filter { sub ->
            sub.isEnabled && clampedTime >= sub.startTimeMs && clampedTime <= sub.endTimeMs
        }

        return ExportFrameSnapshot(
            timestampMs = clampedTime,
            activeTransition = activeTransition,
            activeClipInfo = activeClipInfo,
            activeVfx = activeVfx,
            activeTexts = activeTexts,
            activeStickers = activeStickers,
            activeSubtitles = activeSubtitles,
            subtitleStyle = project.subtitleStyle
        )
    }
}
