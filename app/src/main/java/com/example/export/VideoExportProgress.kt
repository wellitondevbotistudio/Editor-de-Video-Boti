package com.example.export

import java.io.File

/**
 * Fase atual do pipeline de exportação.
 */
enum class ExportPhase(val description: String) {
    INITIALIZING("Inicializando exportação..."),
    PREPARING_TIMELINE("Preparando linha do tempo e recursos..."),
    RENDERING_FRAMES("Renderizando quadros de vídeo..."),
    PROCESSING_AUDIO("Processando e mixando áudio..."),
    MUXING_MP4("Codificando e finalizando arquivo MP4..."),
    COMPLETED("Exportação concluída com sucesso!"),
    FAILED("Erro durante a exportação.")
}

/**
 * Progresso detalhado da exportação emitido para o ViewModel/UI.
 */
data class VideoExportProgress(
    val phase: ExportPhase = ExportPhase.INITIALIZING,
    val progress: Float = 0f, // 0.0f .. 1.0f
    val currentFrame: Long = 0L,
    val totalFrames: Long = 0L,
    val currentTimestampMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val message: String = phase.description
)

/**
 * Resultado final da exportação de vídeo.
 */
data class VideoExportResult(
    val success: Boolean,
    val outputFile: File?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val mediaStoreUri: String? = null,
    val errorMessage: String? = null
)

/**
 * Exceção específica gerada pelo subsistema de exportação.
 */
class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
