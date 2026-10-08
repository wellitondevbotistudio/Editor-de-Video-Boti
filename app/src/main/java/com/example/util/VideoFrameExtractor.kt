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
    private val frameCache = object : LruCache<String, Bitmap>(40) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1) // KB
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
        // Quantiza em janelas de 100ms para reaproveitamento eficiente durante scrub e reprodução
        val quantizedTime = (safeTimeMs / 100) * 100
        val sourceKey = when {
            !localPath.isNullOrBlank() -> localPath
            !uri.isNullOrBlank() -> uri
            else -> return null
        }
        val cacheKey = "${sourceKey}_$quantizedTime"

        frameCache.get(cacheKey)?.let {
            if (!it.isRecycled) return it
        }

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

            val targetUs = safeTimeMs * 1000L
            val bitmap = retriever.getFrameAtTime(
                targetUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(targetUs) ?: retriever.frameAtTime

            if (bitmap != null) {
                frameCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun clearCache() {
        frameCache.evictAll()
    }
}
