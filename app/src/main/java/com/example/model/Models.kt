package com.example.model

enum class AspectRatio(val label: String, val ratio: Float, val iconLabel: String) {
    RATIO_9_16("9:16", 9f / 16f, "TikTok / Reels"),
    RATIO_16_9("16:9", 16f / 9f, "YouTube"),
    RATIO_1_1("1:1", 1f, "Instagram"),
    RATIO_4_5("4:5", 4f / 5f, "Feed"),
    RATIO_4_3("4:3", 4f / 3f, "Clássico")
}

enum class MediaType {
    VIDEO, PHOTO, AUDIO
}

data class MediaClip(
    val id: String,
    val title: String,
    val uri: String,
    val type: MediaType = MediaType.VIDEO,
    val durationMs: Long = 5000L,
    val originalDurationMs: Long = 5000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val brightness: Float = 0f, // -100 to +100
    val contrast: Float = 0f,   // -100 to +100
    val saturation: Float = 0f, // -100 to +100
    val filter: String = "Original",
    val transition: String? = null,
    val transitionDurationMs: Long = 1000L,
    val cropRatio: String = "Original",
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val isReverse: Boolean = false,
    val isFrozen: Boolean = false
)

data class AudioTrackItem(
    val id: String,
    val name: String,
    val category: String,
    val duration: String,
    val durationMs: Long = 60000L,
    val uri: String = "",
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class TextOverlayItem(
    val id: String,
    val text: String,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L,
    val posX: Float = 0.5f, // 0..1
    val posY: Float = 0.5f, // 0..1
    val fontSizeSp: Float = 24f,
    val colorHex: String = "#FFFFFF",
    val bgHex: String? = null,
    val fontFamily: String = "Inter",
    val animation: String = "Fade"
)

data class SubtitleSegmentItem(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isEnabled: Boolean = true
)

data class SubtitleStyleConfig(
    val fontSizeSp: Float = 18f,
    val textColorHex: String = "#FFFFFF",
    val strokeColorHex: String = "#000000",
    val strokeWidth: Float = 2f,
    val positionYPercent: Float = 0.82f,
    val bgEnabled: Boolean = true,
    val bgColorHex: String = "#000000",
    val bgOpacity: Float = 0.7f
)

data class VideoTemplateItem(
    val id: String,
    val title: String,
    val category: String,
    val clipsCount: Int,
    val duration: String,
    val thumbUrl: String,
    val isPremium: Boolean = false
)

data class VFXEffectItem(
    val id: String,
    val name: String,
    val category: String,
    val thumbUrl: String,
    val intensity: Float = 50f,
    val isPremium: Boolean = false
)

data class TransitionItem(
    val id: String,
    val name: String,
    val iconName: String
)

data class ProjectItem(
    val id: String,
    val title: String,
    val duration: String,
    val date: String,
    val thumbUrl: String,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<MediaClip> = emptyList(),
    val audios: List<AudioTrackItem> = emptyList(),
    val texts: List<TextOverlayItem> = emptyList(),
    val subtitles: List<SubtitleSegmentItem> = emptyList(),
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val activeFilter: String = "Original",
    val activeVFX: List<VFXEffectItem> = emptyList()
)

data class ExportOptions(
    val resolution: String = "1080p (Full HD)",
    val frameRate: Int = 30,
    val quality: String = "Alta",
    val removeWatermark: Boolean = true,
    val format: String = "MP4 (H.264)"
)
