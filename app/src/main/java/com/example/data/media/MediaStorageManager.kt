package com.example.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

data class StoredMediaResult(
    val file: File,
    val originalName: String,
    val sizeBytes: Long,
    val mimeType: String
)

class MediaStorageManager(private val context: Context) {

    fun getProjectMediaDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId/media")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getProjectThumbnailsDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId/thumbnails")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getOriginalFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and fallback
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "media_${System.currentTimeMillis()}"
        }
        return sanitizeFileName(name!!)
    }

    fun getMimeType(uri: Uri): String {
        var mime = context.contentResolver.getType(uri)
        if (mime.isNullOrBlank()) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (!extension.isNullOrBlank()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            }
        }
        return mime ?: "application/octet-stream"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(100)
    }

    suspend fun copyUriToProjectMedia(projectId: String, sourceUri: Uri): StoredMediaResult = withContext(Dispatchers.IO) {
        val originalName = getOriginalFileName(sourceUri)
        val mimeType = getMimeType(sourceUri)
        val extension = getExtensionForMime(mimeType, originalName)

        val projectMediaDir = getProjectMediaDir(projectId)

        // Pre-check usable space: Ensure at least 30 MB is free on device
        val minUsableSpaceBytes = 30L * 1024 * 1024
        if (context.filesDir.usableSpace < minUsableSpaceBytes) {
            throw IOException("Espaço de armazenamento insuficiente no dispositivo para copiar a mídia.")
        }

        val uniqueBase = UUID.randomUUID().toString().replace("-", "")
        val tempFile = File(projectMediaDir, "tmp_$uniqueBase.tmp")
        val finalFile = File(projectMediaDir, "${uniqueBase}.$extension")

        var totalBytesCopied: Long = 0

        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Não foi possível abrir o arquivo para leitura: $sourceUri")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024) // 64 KB buffer streaming
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead
                    }
                    output.flush()
                }
            }

            if (totalBytesCopied == 0L) {
                tempFile.delete()
                throw IOException("O arquivo selecionado está vazio (0 bytes).")
            }

            // Move temp file to final file
            if (!tempFile.renameTo(finalFile)) {
                // If renameTo fails, fallback to manual copy & delete
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            StoredMediaResult(
                file = finalFile,
                originalName = originalName,
                sizeBytes = totalBytesCopied,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            throw e
        }
    }

    private fun getExtensionForMime(mimeType: String, originalName: String): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!fromMime.isNullOrBlank()) {
            return fromMime
        }
        val dotIndex = originalName.lastIndexOf('.')
        if (dotIndex != -1 && dotIndex < originalName.length - 1) {
            return originalName.substring(dotIndex + 1).lowercase(Locale.ROOT)
        }
        return when {
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("audio/") -> "mp3"
            mimeType.startsWith("image/") -> "jpg"
            else -> "bin"
        }
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun deleteProjectMedia(projectId: String): Boolean {
        return try {
            val mediaDir = File(context.filesDir, "projects/$projectId")
            mediaDir.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }
}
