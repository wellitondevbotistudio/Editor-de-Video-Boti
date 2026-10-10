package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.io.File

/**
 * Utilitário central de alta performance para extração e cache de quadros de vídeo
 * para sobreposições de vídeo, prévias da timeline, seleção de capa e exportador MP4.
 */
object VideoFrameExtractor {

    // Cache em memória de frames decodificados por chave (path_timeMs)
    private val frameCache = object : LruCache<String, Bitmap>(160) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1) // KB
        }
    }

    // Pool estável de MediaMetadataRetriever por fonte para eliminar overhead nativo de criação/destruição contínua
    private val retrieverPool = java.util.concurrent.ConcurrentHashMap<String, MediaMetadataRetriever>()

    private fun getOrCreateRetriever(context: Context, localPath: String?, uri: String?): Pair<MediaMetadataRetriever, String>? {
        val key = localPath?.ifBlank { null } ?: uri?.ifBlank { null } ?: return null
        val existing = retrieverPool[key]
        if (existing != null) return Pair(existing, key)

        val retriever = MediaMetadataRetriever()
        return try {
            val file = if (!localPath.isNullOrBlank()) File(localPath) else null
            if (file != null && file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else if (!uri.isNullOrBlank()) {
                val parsedUri = Uri.parse(uri)
                if (parsedUri.scheme == "file") {
                    retriever.setDataSource(parsedUri.path)
                } else {
                    retriever.setDataSource(context, parsedUri)
                }
            } else {
                return null
            }
            if (retrieverPool.size >= 8) {
                // Remove um antigo para limitar memória
                val oldestKey = retrieverPool.keys.firstOrNull()
                if (oldestKey != null) {
                    try { retrieverPool.remove(oldestKey)?.release() } catch (_: Exception) {}
                }
            }
            retrieverPool[key] = retriever
            Pair(retriever, key)
        } catch (_: Exception) {
            try { retriever.release() } catch (_: Exception) {}
            null
        }
    }

    /**
     * Extrai o quadro real de um vídeo em uma posição temporal específica.
     * @param context Contexto da aplicação
     * @param localPath Caminho local no disco (opcional)
     * @param uri Uri da mídia (opcional)
     * @param timeMs Posição temporal relativa em milissegundos
     */
    fun extractFrame(
        context: Context,
        localPath: String?,
        uri: String?,
        timeMs: Long
    ): Bitmap? {
        val safeTimeMs = timeMs.coerceAtLeast(0L)
        // Cadência fluida de ~30fps (33ms) para eliminar efeito slideshow e reaproveitar frames suavemente
        val quantizedTime = (safeTimeMs / 33) * 33
        val sourceKey = when {
            !localPath.isNullOrBlank() -> localPath
            !uri.isNullOrBlank() -> uri
            else -> return null
        }
        val cacheKey = "${sourceKey}_$quantizedTime"

        frameCache.get(cacheKey)?.let {
            if (!it.isRecycled) return it
        }

        val pair = getOrCreateRetriever(context, localPath, uri) ?: return null
        val retriever = pair.first

        return synchronized(retriever) {
            try {
                val targetUs = safeTimeMs * 1000L
                val bitmap = retriever.getFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.getFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: retriever.getFrameAtTime(targetUs) ?: retriever.frameAtTime

                if (bitmap != null) {
                    frameCache.put(cacheKey, bitmap)
                }
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clearCache() {
        frameCache.evictAll()
        retrieverPool.forEach { (_, r) ->
            try { r.release() } catch (_: Exception) {}
        }
        retrieverPool.clear()
    }
}
