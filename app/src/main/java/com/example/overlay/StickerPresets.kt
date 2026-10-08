package com.example.overlay

import com.example.model.StickerItem
import java.util.UUID

data class StickerPreset(
    val id: String,
    val name: String,
    val category: String,
    val uri: String,
    val isGif: Boolean = false,
    val defaultScale: Float = 1.0f
) {
    fun createStickerItem(
        startTimeMs: Long,
        durationMs: Long = 3000L,
        posX: Float = 0.5f,
        posY: Float = 0.5f,
        animationIn: String = "Zoom",
        animationOut: String = "Fade"
    ): StickerItem {
        return StickerItem(
            id = "stk_" + UUID.randomUUID().toString().take(6),
            name = name,
            uri = uri,
            localPath = "",
            isGif = isGif,
            startTimeMs = startTimeMs,
            durationMs = durationMs,
            posX = posX,
            posY = posY,
            scale = defaultScale,
            rotation = 0f,
            opacity = 1.0f,
            animationIn = animationIn,
            animationOut = animationOut,
            animationDurationMs = 500L,
            isVisible = true
        )
    }
}

object StickerPresetsRepository {

    val presets: List<StickerPreset> = listOf(
        // Reações & Emojis
        StickerPreset("stk_fire", "Fogo", "Reações", "https://images.unsplash.com/photo-1546776310-eef45dd6d63c?w=160&h=160&fit=crop", defaultScale = 1.0f),
        StickerPreset("stk_heart", "Coração Neon", "Reações", "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=160&h=160&fit=crop", defaultScale = 1.0f),
        StickerPreset("stk_star", "Estrela Dourada", "Reações", "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=160&h=160&fit=crop", defaultScale = 1.0f),
        StickerPreset("stk_party", "Festa", "Reações", "https://images.unsplash.com/photo-1513151233558-d860c5398176?w=160&h=160&fit=crop", defaultScale = 1.1f),

        // Badges & Destaque
        StickerPreset("stk_badge_viral", "Viral", "Badges", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=160&h=160&fit=crop", defaultScale = 1.2f),
        StickerPreset("stk_badge_subscribe", "Inscreva-se", "Badges", "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=160&h=160&fit=crop", defaultScale = 1.2f),
        StickerPreset("stk_badge_like", "Curtir", "Badges", "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=160&h=160&fit=crop", defaultScale = 1.0f),
        StickerPreset("stk_badge_boti", "Boti Pro", "Badges", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=160&h=160&fit=crop", defaultScale = 1.0f),

        // GIFs Animados
        StickerPreset("gif_glitch", "Glitch Neon", "GIFs", "https://media.giphy.com/media/3o7aD2saalBwwftBIY/giphy.gif", isGif = true, defaultScale = 1.2f),
        StickerPreset("gif_sparkles", "Brilhos Mágicos", "GIFs", "https://media.giphy.com/media/26AHONQCd8P0g2cUM/giphy.gif", isGif = true, defaultScale = 1.1f),
        StickerPreset("gif_loading", "Carregando", "GIFs", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif", isGif = true, defaultScale = 1.0f),
        StickerPreset("gif_party", "Confete", "GIFs", "https://media.giphy.com/media/26tOZ42Mg6pbTUPHW/giphy.gif", isGif = true, defaultScale = 1.3f)
    )

    val categories: List<String> = listOf("Tudo", "Reações", "Badges", "GIFs")
}
