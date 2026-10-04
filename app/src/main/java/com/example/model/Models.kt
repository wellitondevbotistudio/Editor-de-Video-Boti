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
    val localPath: String = "",
    val thumbnailPath: String = "",
    val originalName: String = "",
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 5000L,
    val originalDurationMs: Long = 0L,
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
    val scale: Float = 1.0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val opacity: Float = 1.0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val isReverse: Boolean = false,
    val isFrozen: Boolean = false,
    val isVisible: Boolean = true,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false
)

data class AudioTrackItem(
    val id: String,
    val name: String,
    val category: String,
    val duration: String,
    val durationMs: Long = 60000L,
    val uri: String = "",
    val localPath: String = "",
    val originalName: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val timelineStartMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
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
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val fontSizeSp: Float = 24f,
    val colorHex: String = "#FFFFFF",
    val bgHex: String? = null,
    val strokeColorHex: String? = null,
    val strokeWidth: Float = 0f,
    val shadowColorHex: String? = null,
    val shadowRadius: Float = 0f,
    val alignment: String = "Center", // Left, Center, Right
    val fontFamily: String = "Inter",
    val animation: String = "Fade",
    val animationIn: String = "Fade", // None, Fade, Slide Left, Slide Right, Slide Up, Slide Down, Zoom
    val animationOut: String = "Fade", // None, Fade, Slide Left, Slide Right, Slide Up, Slide Down, Zoom
    val animationDurationMs: Long = 500L,
    val textAnimationMode: String = "Full", // Full, Word, Letter
    val styleTemplateId: String? = null,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)

data class StickerItem(
    val id: String,
    val uri: String,
    val localPath: String = "",
    val name: String = "Sticker",
    val isGif: Boolean = false,
    val isVideo: Boolean = false,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3000L,
    val posX: Float = 0.5f, // 0..1
    val posY: Float = 0.5f, // 0..1
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val animationIn: String = "Fade",
    val animationOut: String = "Fade",
    val animationDurationMs: Long = 500L,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
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
    val isPremium: Boolean = false,
    val clipId: String? = null,
    val isEnabled: Boolean = true
)

data class TransitionItem(
    val id: String,
    val fromClipId: String = "",
    val toClipId: String = "",
    val name: String,
    val iconName: String = "shuffle",
    val durationMs: Long = 1000L,
    val isEnabled: Boolean = true
)

data class ProjectItem(
    val id: String,
    val title: String,
    val duration: String = "00:00",
    val date: String = "Hoje",
    val thumbUrl: String = "",
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<MediaClip> = emptyList(),
    val audios: List<AudioTrackItem> = emptyList(),
    val texts: List<TextOverlayItem> = emptyList(),
    val stickers: List<StickerItem> = emptyList(),
    val subtitles: List<SubtitleSegmentItem> = emptyList(),
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val activeFilter: String = "Original",
    val activeVFX: List<VFXEffectItem> = emptyList(),
    val transitions: List<TransitionItem> = emptyList(),
    // Controles de Camadas / Faixas
    val isTextVisible: Boolean = true,
    val isTextLocked: Boolean = false,
    val isVfxVisible: Boolean = true,
    val isVfxLocked: Boolean = false,
    val isVideoVisible: Boolean = true,
    val isVideoMuted: Boolean = false,
    val isVideoLocked: Boolean = false,
    val isOverlayVisible: Boolean = true,
    val isOverlayLocked: Boolean = false,
    val isAudioMuted: Boolean = false,
    val isAudioLocked: Boolean = false
)

data class ExportOptions(
    val resolution: String = "1080p (Full HD)",
    val frameRate: Int = 30,
    val quality: String = "Alta",
    val removeWatermark: Boolean = true,
    val format: String = "MP4 (H.264)"
)
