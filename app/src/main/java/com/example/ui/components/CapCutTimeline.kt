package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.TimelineUtils
import java.io.File
import kotlin.math.max

// Pro CapCut-style Multi-Layer Colors
val LayerTextBg = Color(0xFFD9534F) // Terracotta Coral
val LayerTextBorder = Color(0xFFFF7675)
val LayerVfxBg = Color(0xFF0F766E) // Deep Teal
val LayerVfxBorder = Color(0xFF2DD4BF)
val LayerVideoBg = Color(0xFF1E293B) // Dark Slate
val LayerOverlayBg = Color(0xFF6B21A8) // Deep Purple
val LayerAudioBg = Color(0xFF0369A1) // Sky Blue
val LayerAudioWave = Color(0xFF38BDF8)

/**
 * CapCut-Style Multi-Track Timeline with synchronized layers for:
 * 1. Texts / Subtitles Layer
 * 2. Visual Effects / VFX Layer
 * 3. Main Video / Photos Layer (with filmstrip thumbnails and transition markers)
 * 4. Overlay / Stickers / PiP Layer
 * 5. Audio Tracks Layer (with waveforms)
 * Plus Track Headers (Lock, Mute, Visibility) and unified Playhead scrubbing.
 */
@Composable
fun CapCutMultiTrackTimeline(
    project: ProjectItem,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    selectedClipId: String?,
    selectedAudioId: String?,
    selectedTextId: String?,
    selectedStickerId: String?,
    waveforms: Map<String, List<Float>>,
    canUndo: Boolean,
    canRedo: Boolean,
    onSeek: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectText: (String) -> Unit,
    onSelectSticker: (String) -> Unit,
    onSplitClip: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddMedia: () -> Unit,
    onAddAudio: () -> Unit,
    onAddText: () -> Unit,
    onAddEffect: () -> Unit,
    onOpenTransition: ((fromClipId: String, toClipId: String) -> Unit)? = null,
    onMoveClipLeft: (() -> Unit)? = null,
    onMoveClipRight: (() -> Unit)? = null,
    onDuplicateSelected: (() -> Unit)? = null,
    onMoveText: ((textId: String, newStartMs: Long) -> Unit)? = null,
    onMoveSticker: ((stickerId: String, newStartMs: Long) -> Unit)? = null,
    onMoveAudio: ((audioId: String, newStartMs: Long) -> Unit)? = null,
    onAddOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) } // 0.6x to 2.5x
    val scrollState = rememberScrollState()

    // Track states: Lock, Mute, Visibility
    var isTextLocked by remember { mutableStateOf(false) }
    var isTextVisible by remember { mutableStateOf(true) }
    var isVfxLocked by remember { mutableStateOf(false) }
    var isVfxVisible by remember { mutableStateOf(true) }
    var isVideoLocked by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }
    var isVideoVisible by remember { mutableStateOf(true) }
    var isOverlayLocked by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(true) }
    var isAudioLocked by remember { mutableStateOf(false) }
    var isAudioMuted by remember { mutableStateOf(false) }

    // Calculated timeline width based on duration & zoom
    val safeTotalMs = max(totalDurationMs, 5000L)
    // 50dp per second at zoom 1.0f
    val dpPerSecond = 50f * zoomScale
    val timelineWidthDp = max(400f, (safeTotalMs / 1000f) * dpPerSecond).dp

    fun msToDp(ms: Long): Dp {
        return ((ms / 1000f) * dpPerSecond).dp
    }

    fun offsetToMs(offsetX: Float, density: androidx.compose.ui.unit.Density): Long {
        val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }
        if (pxPerSecond <= 0) return 0L
        val seconds = offsetX / pxPerSecond
        return (seconds * 1000f).toLong().coerceIn(0L, safeTotalMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F14))
    ) {
        // 1. Pro Action Toolbar (Scissors, Undo, Redo, Delete, Mic, Zoom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131A))
                .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Action Tools: Split, Delete, Undo, Redo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = { onSplitClip() },
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Dividir",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dividir", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Desfazer",
                        tint = if (canUndo) Color.White else TextTertiary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Refazer",
                        tint = if (canRedo) Color.White else TextTertiary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteSelected,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Excluir",
                        tint = DangerRed,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Move left / right & duplicate if a clip is selected
                if (selectedClipId != null && onMoveClipLeft != null && onMoveClipRight != null) {
                    IconButton(
                        onClick = onMoveClipLeft,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Mover para Esquerda",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveClipRight,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Mover para Direita",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (selectedClipId != null && onDuplicateSelected != null) {
                    IconButton(
                        onClick = onDuplicateSelected,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicar",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Right Action Tools: Quick Add Layers & Zoom
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Zoom Out
                IconButton(
                    onClick = { zoomScale = (zoomScale - 0.2f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom -", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }

                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )

                // Zoom In
                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.2f).coerceAtMost(2.5f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 2. Timeline Tracks Body (Left Column: Track Headers | Right Column: Synchronized Layers Canvas)
        val audioTrackCount = max(1, project.audios.size)
        val audioHeaderHeight = (42 * audioTrackCount).dp
        val totalTracksHeight = (220 + 42 * audioTrackCount).dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalTracksHeight)
        ) {
            // LEFT COLUMN: Fixed Track Control Headers
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF111118))
                    .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle))
            ) {
                // Header space matching Time Ruler height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Color(0xFF161622)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camadas", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // 1. Text Track Header
                TrackHeaderItem(
                    trackName = "Texto",
                    icon = Icons.Default.TextFields,
                    color = LayerTextBorder,
                    height = 36.dp,
                    isLocked = isTextLocked,
                    isVisible = isTextVisible,
                    onToggleLock = { isTextLocked = !isTextLocked },
                    onToggleVisibility = { isTextVisible = !isTextVisible },
                    onAdd = onAddText
                )

                // 2. Effects / VFX Track Header
                TrackHeaderItem(
                    trackName = "Efeitos",
                    icon = Icons.Default.AutoAwesome,
                    color = LayerVfxBorder,
                    height = 36.dp,
                    isLocked = isVfxLocked,
                    isVisible = isVfxVisible,
                    onToggleLock = { isVfxLocked = !isVfxLocked },
                    onToggleVisibility = { isVfxVisible = !isVfxVisible },
                    onAdd = onAddEffect
                )

                // 3. Main Video Track Header
                TrackHeaderItem(
                    trackName = "Vídeo",
                    icon = Icons.Default.Movie,
                    color = PrimaryPurpleVariant,
                    height = 68.dp,
                    isLocked = isVideoLocked,
                    isVisible = isVideoVisible,
                    isMuted = isVideoMuted,
                    showMute = true,
                    onToggleLock = { isVideoLocked = !isVideoLocked },
                    onToggleVisibility = { isVideoVisible = !isVideoVisible },
                    onToggleMute = { isVideoMuted = !isVideoMuted },
                    onAdd = onAddMedia
                )

                // 4. Overlay / Stickers Track Header
                TrackHeaderItem(
                    trackName = "Sobrepor",
                    icon = Icons.Default.Layers,
                    color = AccentPink,
                    height = 36.dp,
                    isLocked = isOverlayLocked,
                    isVisible = isOverlayVisible,
                    onToggleLock = { isOverlayLocked = !isOverlayLocked },
                    onToggleVisibility = { isOverlayVisible = !isOverlayVisible },
                    onAdd = { onAddOverlay?.invoke() ?: onAddMedia() }
                )

                // 5. Audio Track Header
                TrackHeaderItem(
                    trackName = "Áudio",
                    icon = Icons.Default.Audiotrack,
                    color = LayerAudioWave,
                    height = audioHeaderHeight,
                    isLocked = isAudioLocked,
                    isVisible = true,
                    isMuted = isAudioMuted,
                    showMute = true,
                    onToggleLock = { isAudioLocked = !isAudioLocked },
                    onToggleVisibility = {},
                    onToggleMute = { isAudioMuted = !isAudioMuted },
                    onAdd = onAddAudio
                )
            }

            // RIGHT COLUMN: Synchronized Timeline Canvas
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                // Tracks Stack
                Column(
                    modifier = Modifier
                        .width(timelineWidthDp + 150.dp)
                        .fillMaxHeight()
                ) {
                    // Time Ruler at the top
                    TimeRuler(
                        totalDurationMs = safeTotalMs,
                        dpPerSecond = dpPerSecond,
                        widthDp = timelineWidthDp + 150.dp,
                        onSeek = onSeek
                    )

                    // 1. Text & Subtitles Layer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF13131D))
                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)))
                            .padding(vertical = 2.dp)
                    ) {
                        // Render texts
                        project.texts.forEach { textItem ->
                            val startDp = msToDp(textItem.startTimeMs)
                            val widthDp = max(50f, (textItem.durationMs / 1000f) * dpPerSecond).dp
                            val isSelected = textItem.id == selectedTextId

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = LayerTextBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else LayerTextBorder
                                ),
                                modifier = Modifier
                                    .padding(start = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .pointerInput(textItem.id, dpPerSecond) {
                                        detectDragGestures(
                                            onDragStart = { onSelectText(textItem.id) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                if (pxPerSec > 0) {
                                                    val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                                    val newStart = (textItem.startTimeMs + deltaMs).coerceIn(0L, safeTotalMs - 200L)
                                                    onMoveText?.invoke(textItem.id, newStart)
                                                }
                                            }
                                        )
                                    }
                                    .clickable { onSelectText(textItem.id) }
                                    .testTag("track_text_${textItem.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = textItem.text,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Render subtitles if present
                        project.subtitles.forEach { subItem ->
                            val startDp = msToDp(subItem.startTimeMs)
                            val duration = max(subItem.endTimeMs - subItem.startTimeMs, 500L)
                            val widthDp = max(40f, (duration / 1000f) * dpPerSecond).dp

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFB91C1C),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171)),
                                modifier = Modifier
                                    .padding(start = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight(0.85f)
                                    .align(Alignment.CenterStart)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CC: ${subItem.text}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 2. Visual Effects (VFX) Layer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF11111A))
                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)))
                            .padding(vertical = 2.dp)
                    ) {
                        project.activeVFX.forEachIndexed { idx, vfx ->
                            val startMs = (idx * 2000L).coerceAtMost(safeTotalMs)
                            val startDp = msToDp(startMs)
                            val widthDp = (3500L / 1000f * dpPerSecond).dp

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = LayerVfxBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, LayerVfxBorder),
                                modifier = Modifier
                                    .padding(start = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .clickable { onAddEffect() }
                                    .testTag("track_vfx_${vfx.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "★ ${vfx.name}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 3. Main Video & Photos Track (Filmstrip & Transitions)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .background(Color(0xFF161622))
                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF252538)))
                            .padding(vertical = 4.dp)
                    ) {
                        if (project.clips.isEmpty()) {
                            Surface(
                                onClick = onAddMedia,
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryPurpleVariant)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Adicionar vídeo / foto à trilha", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var accumulatedMs = 0L
                                project.clips.forEachIndexed { index, clip ->
                                    val isSelected = clip.id == selectedClipId
                                    val clipDurationMs = TimelineUtils.calculateClipTimelineDuration(clip)
                                    val clipWidthDp = max(60f, (clipDurationMs / 1000f) * dpPerSecond).dp

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = LayerVideoBg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155) // Golden border when selected
                                        ),
                                        modifier = Modifier
                                            .width(clipWidthDp)
                                            .fillMaxHeight()
                                            .clickable { onSelectClip(clip.id) }
                                            .testTag("track_clip_${clip.id}")
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Thumbnail image
                                            val clipThumbModel = when {
                                                clip.thumbnailPath.isNotBlank() -> File(clip.thumbnailPath)
                                                clip.localPath.isNotBlank() -> File(clip.localPath)
                                                else -> clip.uri
                                            }
                                            AsyncImage(
                                                model = clipThumbModel,
                                                contentDescription = clip.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            // Top & bottom sprocket perforations (Filmstrip aesthetic)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Black.copy(alpha = 0.6f),
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.7f)
                                                            )
                                                        )
                                                    )
                                            )

                                            // Title & duration
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = clip.title,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${String.format("%.1f", clipDurationMs / 1000f)}s",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 9.sp
                                                )
                                            }

                                            // Tactile grip handles if selected
                                            if (isSelected) {
                                                // Left handle
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .width(8.dp)
                                                        .fillMaxHeight()
                                                        .background(Color(0xFFFBBF24), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                                )
                                                // Right handle
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .width(8.dp)
                                                        .fillMaxHeight()
                                                        .background(Color(0xFFFBBF24), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                                )
                                            }
                                        }
                                    }

                                    // Interactive Transition Marker between clips
                                    if (index < project.clips.lastIndex) {
                                        val nextClip = project.clips[index + 1]
                                        Surface(
                                            onClick = { onOpenTransition?.invoke(clip.id, nextClip.id) },
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (clip.transition != null) PrimaryPurple else Color(0xFF2E2E3E),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A62)),
                                            modifier = Modifier
                                                .padding(horizontal = 2.dp)
                                                .size(width = 16.dp, height = 22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "Transição",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                    accumulatedMs += clipDurationMs
                                }
                            }
                        }
                    }

                    // 4. Overlay & Stickers Track (PiP / Foto sobreposta)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF12121A))
                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)))
                            .padding(vertical = 2.dp)
                    ) {
                        project.stickers.forEach { stk ->
                            val startDp = msToDp(stk.startTimeMs)
                            val widthDp = max(45f, (stk.durationMs / 1000f) * dpPerSecond).dp
                            val isSelected = stk.id == selectedStickerId

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = LayerOverlayBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color.White else AccentPink
                                ),
                                modifier = Modifier
                                    .padding(start = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .pointerInput(stk.id, dpPerSecond) {
                                        detectDragGestures(
                                            onDragStart = { onSelectSticker(stk.id) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                if (pxPerSec > 0) {
                                                    val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                                    val newStart = (stk.startTimeMs + deltaMs).coerceIn(0L, safeTotalMs - 200L)
                                                    onMoveSticker?.invoke(stk.id, newStart)
                                                }
                                            }
                                        )
                                    }
                                    .clickable { onSelectSticker(stk.id) }
                                    .testTag("track_sticker_${stk.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (stk.isGif) Icons.Default.Gif else Icons.Default.EmojiEmotions,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stk.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 5. Audio Waveform Tracks (Multi-lane)
                    if (project.audios.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(Color(0xFF0F172A))
                                .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B)))
                                .padding(vertical = 2.dp)
                        ) {
                            Surface(
                                onClick = onAddAudio,
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = LayerAudioWave, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Adicionar trilha de áudio", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        project.audios.forEachIndexed { audioIndex, audio ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .background(if (audioIndex % 2 == 0) Color(0xFF0F172A) else Color(0xFF141E33))
                                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B)))
                                    .padding(vertical = 2.dp)
                            ) {
                                val startDp = msToDp(audio.timelineStartMs)
                                val duration = max(audio.durationMs, 2000L)
                                val widthDp = max(60f, (duration / 1000f) * dpPerSecond).dp
                                val isSelected = audio.id == selectedAudioId
                                val audioWave = waveforms[audio.id] ?: emptyList()

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LayerAudioBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else LayerAudioWave
                                    ),
                                    modifier = Modifier
                                        .padding(start = startDp)
                                        .width(widthDp)
                                        .fillMaxHeight()
                                        .pointerInput(audio.id, dpPerSecond) {
                                            detectDragGestures(
                                                onDragStart = { onSelectAudio(audio.id) },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                    if (pxPerSec > 0) {
                                                        val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                                        val newStart = (audio.timelineStartMs + deltaMs).coerceIn(0L, safeTotalMs - 500L)
                                                        onMoveAudio?.invoke(audio.id, newStart)
                                                    }
                                                }
                                            )
                                        }
                                        .clickable { onSelectAudio(audio.id) }
                                        .testTag("track_audio_${audio.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "A${audioIndex + 1}: ${audio.name}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 90.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Waveform Bars visualizer
                                        AudioWaveformBar(
                                            amplitudes = audioWave,
                                            isMuted = audio.isMuted || isAudioMuted,
                                            color = LayerAudioWave,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // UNIFIED PLAYHEAD: Vertical glowing line spanning all layers
                val playheadOffsetDp = msToDp(currentPositionMs)
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .shadow(4.dp, spotColor = Color.White)
                )

                // Interactive Scrubber Handle on Playhead
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp - 14.dp)
                        .width(28.dp)
                        .height(30.dp)
                        .pointerInput(safeTotalMs, dpPerSecond) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                if (pxPerSec > 0) {
                                    val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                    val newPos = (currentPositionMs + deltaMs).coerceIn(0L, safeTotalMs)
                                    onSeek(newPos)
                                }
                            }
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(16.dp)
                            .offset(y = (-2).dp)
                    ) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2f, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Single Track Control Header item in the left column
 */
@Composable
private fun TrackHeaderItem(
    trackName: String,
    icon: ImageVector,
    color: Color,
    height: Dp,
    isLocked: Boolean,
    isVisible: Boolean,
    isMuted: Boolean = false,
    showMute: Boolean = false,
    onToggleLock: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleMute: (() -> Unit)? = null,
    onAdd: () -> Unit
) {
    Surface(
        color = Color(0xFF13131D),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = trackName,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = trackName,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Add button for this layer
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar $trackName",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Quick Track Toggles: Lock, Eye, Mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Lock toggle
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Travar",
                        tint = if (isLocked) GoldPremium else TextTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                }

                // Visibility toggle
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Visibilidade",
                        tint = if (isVisible) TextSecondary else TextTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                }

                // Mute toggle (if supported by track)
                if (showMute && onToggleMute != null) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mudo",
                            tint = if (isMuted) DangerRed else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Time Ruler with timestamp markers and interactive seek on tap/drag.
 */
@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    dpPerSecond: Float,
    widthDp: Dp,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    val totalSeconds = (totalDurationMs / 1000).toInt() + 4

    Surface(
        color = Color(0xFF161624),
        modifier = Modifier
            .width(widthDp)
            .height(28.dp)
            .pointerInput(totalDurationMs, dpPerSecond) {
                detectTapGestures { offset ->
                    val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }
                    if (pxPerSecond > 0) {
                        val sec = offset.x / pxPerSecond
                        val ms = (sec * 1000f).toLong().coerceIn(0L, totalDurationMs)
                        onSeek(ms)
                    }
                }
            }
            .pointerInput(totalDurationMs, dpPerSecond) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }
                    if (pxPerSecond > 0) {
                        val sec = change.position.x / pxPerSecond
                        val ms = (sec * 1000f).toLong().coerceIn(0L, totalDurationMs)
                        onSeek(ms)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerSec = dpPerSecond.dp.toPx()

            for (sec in 0..totalSeconds) {
                val x = sec * pxPerSec

                // Major second tick
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(x, size.height - 10f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f
                )

                // Half second tick
                val halfX = x + (pxPerSec / 2f)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(halfX, size.height - 5f),
                    end = Offset(halfX, size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Timestamp labels (00:00, 00:01, 00:02...)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            for (sec in 0..totalSeconds) {
                val min = sec / 60
                val s = sec % 60
                val timeLabel = String.format("%02d:%02d", min, s)

                Text(
                    text = timeLabel,
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .width(dpPerSecond.dp)
                        .padding(start = 2.dp, top = 2.dp)
                )
            }
        }
    }
}
