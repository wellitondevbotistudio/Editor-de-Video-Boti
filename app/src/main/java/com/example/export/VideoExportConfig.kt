package com.example.export

import com.example.model.AspectRatio
import java.io.File

/**
 * Resoluções suportadas para exportação.
 */
enum class ExportResolution(val label: String, val baseDimension: Int) {
    RES_720P("720p", 720),
    RES_1080P("1080p", 1080),
    RES_4K("4K", 2160);

    companion object {
        fun fromLabel(label: String): ExportResolution {
            return when {
                label.contains("4k", ignoreCase = true) || label.contains("2160") -> RES_4K
                label.contains("720") -> RES_720P
                else -> RES_1080P
            }
        }
    }
}

/**
 * Configuração completa da exportação de vídeo.
 */
data class VideoExportConfig(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val fps: Int = 30,
    val quality: String = "Alta", // Normal, Alta, Máxima
    val removeWatermark: Boolean = false,
    val customOutputFile: File? = null
) {
    /**
     * Calcula as dimensões exatas de largura e altura baseadas na resolução e proporção.
     */
    val dimensions: Pair<Int, Int>
        get() {
            return when (aspectRatio) {
                AspectRatio.RATIO_16_9 -> when (resolution) {
                    ExportResolution.RES_720P -> 1280 to 720
                    ExportResolution.RES_1080P -> 1920 to 1080
                    ExportResolution.RES_4K -> 3840 to 2160
                }
                AspectRatio.RATIO_9_16 -> when (resolution) {
                    ExportResolution.RES_720P -> 720 to 1280
                    ExportResolution.RES_1080P -> 1080 to 1920
                    ExportResolution.RES_4K -> 2160 to 3840
                }
                AspectRatio.RATIO_1_1 -> when (resolution) {
                    ExportResolution.RES_720P -> 720 to 720
                    ExportResolution.RES_1080P -> 1080 to 1080
                    ExportResolution.RES_4K -> 2160 to 2160
                }
                AspectRatio.RATIO_4_5 -> when (resolution) {
                    ExportResolution.RES_720P -> 720 to 900
                    ExportResolution.RES_1080P -> 1080 to 1350
                    ExportResolution.RES_4K -> 2160 to 2700
                }
                AspectRatio.RATIO_4_3 -> when (resolution) {
                    ExportResolution.RES_720P -> 960 to 720
                    ExportResolution.RES_1080P -> 1440 to 1080
                    ExportResolution.RES_4K -> 2880 to 2160
                }
            }
        }

    val width: Int get() = dimensions.first
    val height: Int get() = dimensions.second

    /**
     * Taxa de bits de vídeo aproximada em bps baseada na resolução e FPS.
     */
    val videoBitrate: Int
        get() {
            val baseBitrate = when (resolution) {
                ExportResolution.RES_720P -> 4_000_000
                ExportResolution.RES_1080P -> 8_500_000
                ExportResolution.RES_4K -> 25_000_000
            }
            val fpsFactor = if (fps >= 60) 1.5f else 1.0f
            val qualityFactor = when (quality.lowercase()) {
                "máxima", "maxima", "ultra" -> 1.3f
                "normal" -> 0.8f
                else -> 1.0f
            }
            return (baseBitrate * fpsFactor * qualityFactor).toInt()
        }

    val audioBitrate: Int = 128_000
    val audioSampleRate: Int = 44_100
    val audioChannels: Int = 2
}
