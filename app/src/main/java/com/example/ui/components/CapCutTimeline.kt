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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.TimelineUtils
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

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
    onReorderClip: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onAddOverlay: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onToggleTextLock: (() -> Unit)? = null,
    onToggleTextVisibility: (() -> Unit)? = null,
    onToggleVfxLock: (() -> Unit)? = null,
    onToggleVfxVisibility: (() -> Unit)? = null,
    onToggleVideoLock: (() -> Unit)? = null,
    onToggleVideoVisibility: (() -> Unit)? = null,
    onToggleVideoMute: (() -> Unit)? = null,
    onToggleOverlayLock: (() -> Unit)? = null,
    onToggleOverlayVisibility: (() -> Unit)? = null,
    onToggleAudioLock: (() -> Unit)? = null,
    onToggleAudioMute: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) } // 0.6x to 3.5x
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    // Real Track states from project model
    val isTextLocked = project.isTextLocked
    val isTextVisible = project.isTextVisible
    val isVfxLocked = project.isVfxLocked
    val isVfxVisible = project.isVfxVisible
    val isVideoLocked = project.isVideoLocked
    val isVideoMuted = project.isVideoMuted
    val isVideoVisible = project.isVideoVisible
    val isOverlayLocked = project.isOverlayLocked
    val isOverlayVisible = project.isOverlayVisible
    val isAudioLocked = project.isAudioLocked
    val isAudioMuted = project.isAudioMuted

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
                    onClick = { zoomScale = (zoomScale - 0.3f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom -", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }

                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )

                // Zoom In
                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.3f).coerceAtMost(3.5f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 2. FIXED TIME RULER & HEADER (Permanece sempre fixo no topo, sem subir ao rolar as camadas)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131D))
                .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E1E2A)))
        ) {
            // Cabeçalho fixo esquerdo correspondente às faixas (76.dp)
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(28.dp)
                    .background(Color(0xFF161622)),
                contentAlignment = Alignment.Center
            ) {
                Text("Camadas", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            // Régua temporal fixa correspondente ao canvas de faixas (sincronizada horizontalmente)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .horizontalScroll(horizontalScrollState)
            ) {
                TimeRuler(
                    totalDurationMs = safeTotalMs,
                    dpPerSecond = dpPerSecond,
                    zoomScale = zoomScale,
                    widthDp = timelineWidthDp + 150.dp,
                    onSeek = onSeek
                )

                // Agulha do Playhead na Régua Superior com arraste contínuo
                val needleOffsetDp = msToDp(currentPositionMs)
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset(x = needleOffsetDp - 12.dp)
                        .width(24.dp)
                        .fillMaxHeight()
                        .pointerInput(safeTotalMs, dpPerSecond) {
                            var dragNeedleMs = 0L
                            detectDragGestures(
                                onDragStart = { dragNeedleMs = currentPositionMs },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                    if (pxPerSec > 0) {
                                        val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                        dragNeedleMs = (dragNeedleMs + deltaMs).coerceIn(0L, safeTotalMs)
                                        onSeek(dragNeedleMs)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(modifier = Modifier.size(12.dp, 8.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }

        // 3. Timeline Tracks Body (Left Column: Track Headers | Right Column: Synchronized Layers Canvas)
        val audioTrackCount = max(1, project.audios.size)
        val audioHeaderHeight = (42 * audioTrackCount).dp
        val totalTracksHeight = (180 + 42 * audioTrackCount).dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .heightIn(min = 90.dp, max = totalTracksHeight)
                .verticalScroll(verticalScrollState)
        ) {
            // LEFT COLUMN: Fixed Track Control Headers
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .height(totalTracksHeight)
                    .background(Color(0xFF111118))
                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E1E2A)))
            ) {
                // 1. Text Track Header
                TrackHeaderItem(
                    trackName = "Texto",
                    icon = Icons.Default.TextFields,
                    color = LayerTextBorder,
                    height = 36.dp,
                    isLocked = isTextLocked,
                    isVisible = isTextVisible,
                    onToggleLock = { onToggleTextLock?.invoke() },
                    onToggleVisibility = { onToggleTextVisibility?.invoke() },
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
                    onToggleLock = { onToggleVfxLock?.invoke() },
                    onToggleVisibility = { onToggleVfxVisibility?.invoke() },
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
                    onToggleLock = { onToggleVideoLock?.invoke() },
                    onToggleVisibility = { onToggleVideoVisibility?.invoke() },
                    onToggleMute = { onToggleVideoMute?.invoke() },
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
                    onToggleLock = { onToggleOverlayLock?.invoke() },
                    onToggleVisibility = { onToggleOverlayVisibility?.invoke() },
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
                    onToggleLock = { onToggleAudioLock?.invoke() },
                    onToggleVisibility = {},
                    onToggleMute = { onToggleAudioMute?.invoke() },
                    onAdd = onAddAudio
                )
            }

            // RIGHT COLUMN: Synchronized Timeline Canvas
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalTracksHeight)
                    .horizontalScroll(horizontalScrollState)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onClearSelection?.invoke()
                        }
                    }
            ) {
                // Tracks Stack
                Column(
                    modifier = Modifier
                        .width(timelineWidthDp + 150.dp)
                        .fillMaxHeight()
                ) {
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
                                    .then(
                                        if (!isTextLocked) {
                                            Modifier.pointerInput(textItem.id, dpPerSecond) {
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
                                        } else Modifier
                                    )
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
                                var dragClipId by remember { mutableStateOf<String?>(null) }
                                var dragOffsetPx by remember { mutableFloatStateOf(0f) }

                                var accumulatedMs = 0L
                                project.clips.forEachIndexed { index, clip ->
                                    val isSelected = clip.id == selectedClipId
                                    val isBeingDragged = clip.id == dragClipId
                                    val clipDurationMs = TimelineUtils.calculateClipTimelineDuration(clip)
                                    val clipWidthDp = max(60f, (clipDurationMs / 1000f) * dpPerSecond).dp

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = LayerVideoBg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected || isBeingDragged) 2.dp else 1.dp,
                                            color = if (isSelected || isBeingDragged) Color(0xFFFBBF24) else Color(0xFF334155) // Golden border when selected
                                        ),
                                        modifier = Modifier
                                            .width(clipWidthDp)
                                            .fillMaxHeight()
                                            .offset {
                                                if (isBeingDragged) IntOffset(dragOffsetPx.roundToInt(), 0)
                                                else IntOffset.Zero
                                            }
                                            .zIndex(if (isBeingDragged) 10f else 1f)
                                            .shadow(if (isBeingDragged) 10.dp else 0.dp, RoundedCornerShape(8.dp))
                                            .then(
                                                if (!isVideoLocked) {
                                                    Modifier.pointerInput(clip.id, clipWidthDp) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                dragClipId = clip.id
                                                                dragOffsetPx = 0f
                                                                onSelectClip(clip.id)
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffsetPx += dragAmount.x
                                                            },
                                                            onDragEnd = {
                                                                val clipWpx = with(density) { clipWidthDp.toPx() }
                                                                if (clipWpx > 0 && Math.abs(dragOffsetPx) > clipWpx * 0.4f) {
                                                                    val shift = if (dragOffsetPx > 0) 1 else -1
                                                                    val targetIdx = (index + shift).coerceIn(0, project.clips.lastIndex)
                                                                    if (targetIdx != index) {
                                                                        onReorderClip?.invoke(index, targetIdx)
                                                                    }
                                                                }
                                                                dragClipId = null
                                                                dragOffsetPx = 0f
                                                            },
                                                            onDragCancel = {
                                                                dragClipId = null
                                                                dragOffsetPx = 0f
                                                            }
                                                        )
                                                    }
                                                } else Modifier
                                            )
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
                                    .then(
                                        if (!isOverlayLocked) {
                                            Modifier.pointerInput(stk.id, dpPerSecond) {
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
                                        } else Modifier
                                    )
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
                                        .then(
                                            if (!isAudioLocked) {
                                                Modifier.pointerInput(audio.id, dpPerSecond) {
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
                                            } else Modifier
                                        )
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

                // UNIFIED PLAYHEAD: Vertical glowing line spanning all layers with full-height touch drag
                val playheadOffsetDp = msToDp(currentPositionMs)
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp - 18.dp)
                        .width(36.dp)
                        .fillMaxHeight()
                        .pointerInput(safeTotalMs, dpPerSecond) {
                            var dragTrackMs = 0L
                            detectDragGestures(
                                onDragStart = { dragTrackMs = currentPositionMs },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                    if (pxPerSec > 0) {
                                        val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                        dragTrackMs = (dragTrackMs + deltaMs).coerceIn(0L, safeTotalMs)
                                        onSeek(dragTrackMs)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Vertical glowing indicator line
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .shadow(4.dp, spotColor = Color.White)
                    )
                    // Top handle indicator
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
 * Single Track Control Header item in the left column - simplified, clean and responsive
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
        color = Color(0xFF13131A),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E1E2A)),
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
            // Top Row: Colored indicator dot + Track name + Clean Add Button (+)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = trackName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Sleek, minimal Add action button
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar $trackName",
                        tint = color.copy(alpha = 0.9f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Bottom Row: Simplified grouped toggles (unobtrusive until toggled)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Visibility / Mute toggle
                if (showMute && onToggleMute != null) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Ativar som" else "Mutar",
                            tint = if (isMuted) DangerRed else Color(0xFF6B7280),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isVisible) "Ocultar" else "Mostrar",
                            tint = if (isVisible) Color(0xFF6B7280) else DangerRed,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Lock toggle
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Destravar" else "Travar",
                        tint = if (isLocked) GoldPremium else Color(0xFF6B7280),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/**
 * Top Time Ruler with timestamp markers (seconds and milliseconds) and interactive seek on tap/drag.
 */
@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    dpPerSecond: Float,
    zoomScale: Float,
    widthDp: Dp,
    onSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    val totalSeconds = (totalDurationMs / 1000).toInt() + 4
    val showMillis = zoomScale >= 2.0f

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

            if (showMillis) {
                // Ticks a cada 200ms ou 100ms em zoom alto
                val stepMs = if (zoomScale >= 2.8f) 100L else 200L
                val pxPerMs = pxPerSec / 1000f
                val totalSteps = (totalDurationMs / stepMs).toInt() + 8

                for (step in 0..totalSteps) {
                    val ms = step * stepMs
                    val x = ms * pxPerMs
                    val isSecond = (ms % 1000L) == 0L
                    val isHalf = (ms % 500L) == 0L

                    drawLine(
                        color = if (isSecond) Color.White.copy(alpha = 0.8f) else if (isHalf) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                        start = Offset(x, size.height - (if (isSecond) 12f else if (isHalf) 8f else 5f)),
                        end = Offset(x, size.height),
                        strokeWidth = if (isSecond) 2f else 1f
                    )
                }
            } else {
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
        }

        // Timestamp labels (com milissegundos em zoom alto)
        if (showMillis) {
            val stepMs = if (zoomScale >= 2.8f) 200L else 500L
            val totalSteps = (totalDurationMs / stepMs).toInt() + 4

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                for (step in 0..totalSteps) {
                    val ms = step * stepMs
                    val min = (ms / 60000).toInt()
                    val s = ((ms % 60000) / 1000).toInt()
                    val frac = (ms % 1000).toInt()
                    val timeLabel = String.format("%02d:%02d.%03d", min, s, frac)

                    Text(
                        text = timeLabel,
                        color = PrimaryPurpleLight,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width((stepMs * dpPerSecond / 1000f).dp)
                            .padding(start = 2.dp, top = 2.dp)
                    )
                }
            }
        } else {
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
}
