package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class ExtractedMediaMetadata(
    val mediaType: MediaType,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Float,
    val mimeType: String,
    val thumbnailPath: String? = null
)

class MediaMetadataExtractor(private val context: Context) {

    suspend fun extractMetadata(
        projectId: String,
        file: File,
        detectedMime: String
    ): ExtractedMediaMetadata = withContext(Dispatchers.IO) {
        val mediaType = resolveMediaType(file, detectedMime)
        when (mediaType) {
            MediaType.VIDEO -> extractVideoMetadata(projectId, file, detectedMime)
            MediaType.AUDIO -> extractAudioMetadata(file, detectedMime)
            MediaType.PHOTO -> extractPhotoMetadata(file, detectedMime)
        }
    }

    private fun resolveMediaType(file: File, mimeType: String): MediaType {
        if (mimeType.startsWith("video/")) return MediaType.VIDEO
        if (mimeType.startsWith("audio/")) return MediaType.AUDIO
        if (mimeType.startsWith("image/")) return MediaType.PHOTO

        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v" -> MediaType.VIDEO
            "mp3", "wav", "aac", "m4a", "ogg", "flac", "opus" -> MediaType.AUDIO
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> MediaType.PHOTO
            else -> MediaType.VIDEO
        }
    }

    private fun extractVideoMetadata(
        projectId: String,
        file: File,
        mimeType: String
    ): ExtractedMediaMetadata {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var rotation = 0f
        var thumbnailPath: String? = null

        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 1000L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toFloatOrNull() ?: 0f

            // Generate real thumbnail frame
            val frame = try {
                // Seek to 0.5s or start of video
                val targetUs = if (durationMs > 1000) 500000L else 0L
                retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
                null
            }

            if (frame != null) {
                val thumbDir = File(context.filesDir, "projects/$projectId/thumbnails")
                if (!thumbDir.exists()) thumbDir.mkdirs()
                val thumbFile = File(thumbDir, "thumb_${file.nameWithoutExtension}.jpg")
                try {
                    FileOutputStream(thumbFile).use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.flush()
                    }
                    thumbnailPath = thumbFile.absolutePath
                } catch (_: Exception) {
                    // Ignore thumbnail saving error
                } finally {
                    frame.recycle()
                }
            }
        } catch (_: Exception) {
            // If retriever fails, provide safe defaults
            if (durationMs <= 0L) durationMs = 1000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ExtractedMediaMetadata(
            mediaType = MediaType.VIDEO,
            durationMs = if (durationMs > 0) durationMs else 1000L,
            width = width,
            height = height,
            rotation = rotation,
            mimeType = if (mimeType.isNotBlank()) mimeType else "video/mp4",
            thumbnailPath = thumbnailPath ?: file.absolutePath
        )
    }

    private fun extractAudioMetadata(file: File, mimeType: String): ExtractedMediaMetadata {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L

        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 60000L
        } catch (_: Exception) {
            durationMs = 60000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ExtractedMediaMetadata(
            mediaType = MediaType.AUDIO,
            durationMs = if (durationMs > 0) durationMs else 60000L,
            width = 0,
            height = 0,
            rotation = 0f,
            mimeType = if (mimeType.isNotBlank()) mimeType else "audio/mpeg",
            thumbnailPath = null
        )
    }

    private fun extractPhotoMetadata(file: File, mimeType: String): ExtractedMediaMetadata {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight

        var rotation = 0f
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {}

        return ExtractedMediaMetadata(
            mediaType = MediaType.PHOTO,
            durationMs = 5000L, // Default standard photo duration on timeline (5 seconds)
            width = width,
            height = height,
            rotation = rotation,
            mimeType = if (mimeType.isNotBlank()) mimeType else "image/jpeg",
            thumbnailPath = file.absolutePath
        )
    }
}
