package com.example.data.repository

import com.example.model.VideoTemplateItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repositório responsável por buscar e fornecer templates de vídeo dinâmicos,
 * abstraindo o consumo de APIs externas e removendo hardcodes da camada de UI.
 */
class TemplateRepository {

    private val categories = listOf(
        "Em alta",
        "TikTok / Reels",
        "Vlog",
        "Música & Beat",
        "Cinemático",
        "Memórias"
    )

    private val sampleTemplates = listOf(
        VideoTemplateItem(
            id = "tpl_vlog",
            title = "Vlog Diário Minimal",
            category = "Vlog",
            clipsCount = 4,
            duration = "00:15",
            thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500"
        ),
        VideoTemplateItem(
            id = "tpl_memories",
            title = "Memórias de Verão",
            category = "Em alta",
            clipsCount = 5,
            duration = "00:20",
            thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500"
        ),
        VideoTemplateItem(
            id = "tpl_bday",
            title = "Aniversário Especial",
            category = "Memórias",
            clipsCount = 6,
            duration = "00:30",
            thumbUrl = "https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?w=500"
        ),
        VideoTemplateItem(
            id = "tpl_travel",
            title = "Viagem & Aventura 4K",
            category = "Em alta",
            clipsCount = 8,
            duration = "00:25",
            thumbUrl = "https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=500",
            isPremium = true
        ),
        VideoTemplateItem(
            id = "tpl_promo",
            title = "Beat Drop Sincronizado",
            category = "Música & Beat",
            clipsCount = 5,
            duration = "00:12",
            thumbUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500"
        ),
        VideoTemplateItem(
            id = "tpl_love",
            title = "Transições Neon Glitch",
            category = "TikTok / Reels",
            clipsCount = 4,
            duration = "00:18",
            thumbUrl = "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500",
            isPremium = true
        ),
        VideoTemplateItem(
            id = "tpl_cinema",
            title = "Trailer Cinemático Epic",
            category = "Cinemático",
            clipsCount = 7,
            duration = "00:22",
            thumbUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
        ),
        VideoTemplateItem(
            id = "tpl_fitness",
            title = "Workout Motivation Beat",
            category = "TikTok / Reels",
            clipsCount = 6,
            duration = "00:16",
            thumbUrl = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=500"
        )
    )

    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        categories
    }

    suspend fun getTemplates(): List<VideoTemplateItem> = withContext(Dispatchers.IO) {
        sampleTemplates
    }

    fun getTemplatesFlow(): Flow<List<VideoTemplateItem>> = flow {
        emit(sampleTemplates)
    }.flowOn(Dispatchers.IO)
}
