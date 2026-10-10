package com.example.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.ProjectItem
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Gerenciador principal de exportação do aplicativo Boti Video Editor.
 * Coordena todo o ciclo de vida da geração de arquivos MP4 reais.
 */
class VideoExportManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "VideoExportManager"
    }

    /**
     * Exporta o projeto de edição para um arquivo MP4 real de alta definição.
     */
    suspend fun exportProject(
        project: ProjectItem,
        config: VideoExportConfig,
        onProgress: (VideoExportProgress) -> Unit
    ): VideoExportResult = withContext(Dispatchers.Default) {

        onProgress(
            VideoExportProgress(
                phase = ExportPhase.INITIALIZING,
                progress = 0.02f,
                message = "Iniciando exportador Boti..."
            )
        )

        // 1. Cria diretório de exportações
        val exportDir = File(context.filesDir, "exports").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Boti_Video" }
        val fileName = "${safeTitle}_${timestamp}.mp4"
        val outputFile = config.customOutputFile ?: File(exportDir, fileName)

        // 2. Prepara linha do tempo e renderizadores
        onProgress(
            VideoExportProgress(
                phase = ExportPhase.PREPARING_TIMELINE,
                progress = 0.05f,
                message = "Compilando timeline e efeitos..."
            )
        )

        val timeline = ExportTimeline(project, config)
        val frameRenderer = VideoFrameRenderer(context, config)
        val audioRenderer = AudioExportRenderer(context, config)
        val pipeline = Mp4EncoderPipeline(context, config)

        var isExportCompletedSuccessfully = false
        try {
            val result = pipeline.encode(
                timeline = timeline,
                outputFile = outputFile,
                frameRenderer = frameRenderer,
                audioRenderer = audioRenderer,
                onProgress = onProgress
            )

            // 3. Salva/Registra na Galeria/MediaStore do dispositivo para abertura externa
            val mediaStoreUri = registerVideoInMediaStore(context, outputFile, project.title)

            onProgress(
                VideoExportProgress(
                    phase = ExportPhase.COMPLETED,
                    progress = 1.0f,
                    currentFrame = timeline.totalFrames,
                    totalFrames = timeline.totalFrames,
                    totalDurationMs = timeline.totalDurationMs,
                    message = "Vídeo MP4 exportado com sucesso!"
                )
            )

            isExportCompletedSuccessfully = result.success
            result.copy(mediaStoreUri = mediaStoreUri?.toString())

        } finally {
            frameRenderer.close()
            audioRenderer.clearCache()
            if (!isExportCompletedSuccessfully) {
                withContext(NonCancellable) {
                    try {
                        if (outputFile.exists()) {
                            val deleted = outputFile.delete()
                            if (deleted) {
                                AppLogger.i("VideoExport", "Arquivo temporário cancelado removido com sucesso: ${outputFile.name}")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("VideoExport", "Falha ao remover arquivo temporário após cancelamento/erro: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Registra o arquivo exportado no MediaStore do Android para que players externos e a galeria
     * possam reproduzir e compartilhar o vídeo.
     */
    private fun registerVideoInMediaStore(context: Context, file: File, title: String): Uri? {
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, title.ifBlank { "Vídeo Boti" })
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.SIZE, file.length())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BotiVideoEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: SecurityException) {
            Log.e(TAG, "Falha de segurança/permissão ao inserir no MediaStore: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao registrar no MediaStore: ${e.message}", e)
            null
        }

        if (uri != null) {
            var success = false
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IOException("Não foi possível abrir o OutputStream para a URI: $uri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                success = true
                Log.i(TAG, "Vídeo registrado com sucesso no MediaStore Scoped Storage: $uri")
            } catch (e: IOException) {
                Log.e(TAG, "Erro de I/O ao copiar arquivo exportado para o MediaStore: ${e.message}", e)
                try {
                    resolver.delete(uri, null, null)
                } catch (delEx: Exception) {
                    Log.w(TAG, "Falha ao remover URI corrompida após erro de I/O: ${delEx.message}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permissão negada ao gravar dados no MediaStore: ${e.message}", e)
                try {
                    resolver.delete(uri, null, null)
                } catch (delEx: Exception) {
                    Log.w(TAG, "Falha ao remover URI após SecurityException: ${delEx.message}")
                }
            }
            if (!success) {
                return null
            }
        }

        return uri
    }
}
