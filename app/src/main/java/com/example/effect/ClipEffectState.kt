package com.example.effect

import com.example.model.MediaClip
import com.example.model.VFXEffectItem

/**
 * Representação unificada do estado de efeitos, ajustes de cor, filtros
 * e transformações espaciais de um clipe no Boti Video Editor.
 */
data class ClipEffectState(
    val clipId: String,
    val brightness: Float = 0f,    // -100f .. +100f (0 é neutro)
    val contrast: Float = 0f,      // -100f .. +100f (0 é neutro)
    val saturation: Float = 0f,    // -100f .. +100f (0 é neutro)
    val filter: String = "Original",
    val activeVfx: List<VFXEffectItem> = emptyList(),
    val scale: Float = 1.0f,       // 0.5f .. 3.0f (1.0f é neutro)
    val rotation: Float = 0f,      // graus (-180f .. +180f ou múltiplos de 90)
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val opacity: Float = 1.0f,     // 0.0f .. 1.0f (1.0f é opaco)
    val positionX: Float = 0f,     // deslocamento horizontal X
    val positionY: Float = 0f,     // deslocamento vertical Y
    val cropRatio: String = "Original" // Proporção de corte do elemento
) {
    val hasAdjustments: Boolean
        get() = brightness != 0f || contrast != 0f || saturation != 0f

    val hasFilter: Boolean
        get() = filter != "Original" && filter.isNotBlank()

    val hasTransform: Boolean
        get() = scale != 1.0f || rotation != 0f || flipHorizontal || flipVertical || opacity != 1.0f || positionX != 0f || positionY != 0f || cropRatio != "Original"

    companion object {
        fun fromClip(clip: MediaClip?, activeVfx: List<VFXEffectItem> = emptyList()): ClipEffectState {
            if (clip == null) return ClipEffectState(clipId = "")
            return ClipEffectState(
                clipId = clip.id,
                brightness = clip.brightness,
                contrast = clip.contrast,
                saturation = clip.saturation,
                filter = clip.filter,
                activeVfx = activeVfx.filter { it.isEnabled && (it.clipId == null || it.clipId == clip.id) },
                scale = if (clip.scale <= 0f) 1.0f else clip.scale,
                rotation = clip.rotation,
                flipHorizontal = clip.flipHorizontal,
                flipVertical = clip.flipVertical,
                opacity = clip.opacity.coerceIn(0f, 1f),
                positionX = clip.positionX,
                positionY = clip.positionY,
                cropRatio = clip.cropRatio
            )
        }
    }
}
