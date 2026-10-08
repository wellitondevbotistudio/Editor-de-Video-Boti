package com.example.template

import com.example.model.TextOverlayItem
import java.util.UUID

/**
 * Modelo de template de estilo de texto reutilizável e customizável.
 */
data class TextTemplate(
    val id: String,
    val name: String,
    val category: String,
    val sampleText: String,
    val fontSizeSp: Float,
    val colorHex: String,
    val bgHex: String?,
    val strokeColorHex: String?,
    val strokeWidth: Float,
    val shadowColorHex: String?,
    val shadowRadius: Float,
    val alignment: String,
    val fontFamily: String,
    val animationIn: String,
    val animationOut: String,
    val textAnimationMode: String
) {
    /**
     * Instancia um TextOverlayItem real e totalmente editável a partir deste template.
     */
    fun createOverlayItem(
        customText: String? = null,
        startTimeMs: Long = 0L,
        durationMs: Long = 4000L,
        posX: Float = 0.5f,
        posY: Float = 0.5f
    ): TextOverlayItem {
        return TextOverlayItem(
            id = "txt_" + UUID.randomUUID().toString().take(6),
            text = customText ?: sampleText,
            startTimeMs = startTimeMs,
            durationMs = durationMs,
            posX = posX,
            posY = posY,
            scale = 1.0f,
            rotation = 0f,
            opacity = 1.0f,
            fontSizeSp = fontSizeSp,
            colorHex = colorHex,
            bgHex = bgHex,
            strokeColorHex = strokeColorHex,
            strokeWidth = strokeWidth,
            shadowColorHex = shadowColorHex,
            shadowRadius = shadowRadius,
            alignment = alignment,
            fontFamily = fontFamily,
            animationIn = animationIn,
            animationOut = animationOut,
            animationDurationMs = 500L,
            textAnimationMode = textAnimationMode,
            styleTemplateId = id,
            isVisible = true
        )
    }
}

object TextTemplateRepository {

    val templates: List<TextTemplate> = listOf(
        TextTemplate(
            id = "tpl_title_center",
            name = "Título Central",
            category = "Títulos",
            sampleText = "TÍTULO DO VÍDEO",
            fontSizeSp = 28f,
            colorHex = "#FFFFFF",
            bgHex = "#99000000",
            strokeColorHex = null,
            strokeWidth = 0f,
            shadowColorHex = "#000000",
            shadowRadius = 8f,
            alignment = "Center",
            fontFamily = "Inter",
            animationIn = "Zoom",
            animationOut = "Fade",
            textAnimationMode = "Full"
        ),
        TextTemplate(
            id = "tpl_impact",
            name = "Impacto",
            category = "Destaque",
            sampleText = "EXPLORE O MUNDO",
            fontSizeSp = 32f,
            colorHex = "#FACC15", // Amarelo vibrante
            bgHex = null,
            strokeColorHex = "#000000",
            strokeWidth = 3f,
            shadowColorHex = "#000000",
            shadowRadius = 12f,
            alignment = "Center",
            fontFamily = "Inter",
            animationIn = "Slide Down",
            animationOut = "Slide Up",
            textAnimationMode = "Word"
        ),
        TextTemplate(
            id = "tpl_minimal_caption",
            name = "Legenda Minimal",
            category = "Legendas",
            sampleText = "Momentos inesquecíveis...",
            fontSizeSp = 20f,
            colorHex = "#FFFFFF",
            bgHex = "#66000000",
            strokeColorHex = null,
            strokeWidth = 0f,
            shadowColorHex = null,
            shadowRadius = 0f,
            alignment = "Center",
            fontFamily = "Inter",
            animationIn = "Fade",
            animationOut = "Fade",
            textAnimationMode = "Letter"
        ),
        TextTemplate(
            id = "tpl_neon_purple",
            name = "Neon Boti",
            category = "Moderno",
            sampleText = "#BOTIEDUX",
            fontSizeSp = 26f,
            colorHex = "#A855F7", // Roxo neon
            bgHex = "#1F1235",
            strokeColorHex = "#C084FC",
            strokeWidth = 1.5f,
            shadowColorHex = "#7C3AED",
            shadowRadius = 16f,
            alignment = "Center",
            fontFamily = "Inter",
            animationIn = "Zoom",
            animationOut = "Fade",
            textAnimationMode = "Full"
        ),
        TextTemplate(
            id = "tpl_vhs_camcorder",
            name = "VHS 90s",
            category = "Retrô",
            sampleText = "REC ● 00:04:20",
            fontSizeSp = 22f,
            colorHex = "#22C55E", // Verde fosforescente
            bgHex = "#B3000000",
            strokeColorHex = null,
            strokeWidth = 0f,
            shadowColorHex = "#15803D",
            shadowRadius = 6f,
            alignment = "Left",
            fontFamily = "Monospace",
            animationIn = "Fade",
            animationOut = "Fade",
            textAnimationMode = "Full"
        ),
        TextTemplate(
            id = "tpl_social_badge",
            name = "Badge Redes",
            category = "Social",
            sampleText = "@boti_creator",
            fontSizeSp = 18f,
            colorHex = "#FFFFFF",
            bgHex = "#7C3AED", // Roxo primário
            strokeColorHex = null,
            strokeWidth = 0f,
            shadowColorHex = null,
            shadowRadius = 0f,
            alignment = "Center",
            fontFamily = "Inter",
            animationIn = "Slide Right",
            animationOut = "Slide Left",
            textAnimationMode = "Full"
        )
    )
}
