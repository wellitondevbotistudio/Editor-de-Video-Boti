package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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

sealed class UnifiedOverlayItem {
    abstract val id: String
    abstract val startTimeMs: Long
    abstract val durationMs: Long
    abstract val isSelected: Boolean
    abstract val name: String

    data class StickerOverlay(
        val sticker: StickerItem,
        override val isSelected: Boolean
    ) : UnifiedOverlayItem() {
        override val id = sticker.id
        override val startTimeMs = sticker.startTimeMs
        override val durationMs = sticker.durationMs
        override val name = sticker.name
    }

    data class TextOverlay(
        val textItem: TextOverlayItem,
        override val isSelected: Boolean
    ) : UnifiedOverlayItem() {
        override val id = textItem.id
        override val startTimeMs = textItem.startTimeMs
        override val durationMs = textItem.durationMs
        override val name = textItem.text
    }

    data class VfxOverlay(
        val vfx: com.example.model.VFXEffectItem,
        override val isSelected: Boolean
    ) : UnifiedOverlayItem() {
        override val id = vfx.id
        override val startTimeMs = vfx.startTimeMs
        override val durationMs = vfx.durationMs
        override val name = vfx.name
    }
}

/**
 * Timeline multicamadas real no padrão CapCut:
 * 1. Playhead vertical fixo centralizado: os elementos se deslocam horizontalmente sob a agulha.
 * 2. Camadas dinâmicas organizadas por empacotamento temporal sem colisão (interval graph coloring).
 * 3. Elementos com duração independente e blocos horizontais proporcionais.
 * 4. Arraste direto: centro move início; alça esquerda (<|) faz trim do início; alça direita (|>) faz trim do fim.
 * 5. Coluna de ações rápidas à esquerda: Silenciar áudio, Capa e indicadores visuais.
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
    selectedVfxId: String? = null,
    waveforms: Map<String, List<Float>>,
    canUndo: Boolean,
    canRedo: Boolean,
    onSeek: (Long) -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onSelectClip: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectText: (String) -> Unit,
    onSelectSticker: (String) -> Unit,
    onSelectVfx: ((String) -> Unit)? = null,
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
    onMoveVfx: ((vfxId: String, newStartMs: Long) -> Unit)? = null,
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
    onTrimClip: ((clipId: String, trimStartMs: Long, trimEndMs: Long) -> Unit)? = null,
    onTrimSticker: ((stickerId: String, startMs: Long, durationMs: Long) -> Unit)? = null,
    onTrimText: ((textId: String, startMs: Long, durationMs: Long) -> Unit)? = null,
    onTrimAudio: ((audioId: String, trimStartMs: Long, trimEndMs: Long) -> Unit)? = null,
    onTrimVfx: ((vfxId: String, startMs: Long, durationMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val isTextLocked = project.isTextLocked
    val isTextVisible = project.isTextVisible
    val isVideoLocked = project.isVideoLocked
    val isVideoMuted = project.isVideoMuted
    val isVideoVisible = project.isVideoVisible
    val isOverlayLocked = project.isOverlayLocked
    val isAudioLocked = project.isAudioLocked
    val isAudioMuted = project.isAudioMuted

    val safeTotalMs = max(totalDurationMs, 5000L)
    val dpPerSecond = 50f * zoomScale
    val density = LocalDensity.current

    fun msToDp(ms: Long): Dp {
        return ((ms / 1000f) * dpPerSecond).dp
    }

    // Dynamic Layer Packing for all visual overlays (stickers, photos, videos, text, VFX effects)
    val allOverlays = remember(project.stickers, project.texts, project.activeVFX, selectedStickerId, selectedTextId, selectedVfxId) {
        val list = mutableListOf<UnifiedOverlayItem>()
        project.stickers.forEach { list.add(UnifiedOverlayItem.StickerOverlay(it, it.id == selectedStickerId)) }
        project.texts.forEach { list.add(UnifiedOverlayItem.TextOverlay(it, it.id == selectedTextId)) }
        project.activeVFX.forEach { list.add(UnifiedOverlayItem.VfxOverlay(it, it.id == selectedVfxId)) }
        list
    }

    val packedOverlayLanes = remember(allOverlays) {
        TimelineUtils.packItemsIntoLanes(
            items = allOverlays,
            getStartMs = { it.startTimeMs },
            getDurationMs = { it.durationMs }
        )
    }

    val dynamicOverlayLaneCount = remember(packedOverlayLanes) {
        max(1, (packedOverlayLanes.maxOfOrNull { it.laneIndex } ?: -1) + 1)
    }

    val totalTracksHeight = (72 + (dynamicOverlayLaneCount * 44) + (max(1, project.audios.size) * 44)).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F14))
    ) {
        // 1. Pro Action Toolbar: Scissors (Dividir), Undo, Redo, Excluir, Duplicar, Zoom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131A))
                .border(androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

                if (onDuplicateSelected != null) {
                    IconButton(
                        onClick = onDuplicateSelected,
                        modifier = Modifier.size(30.dp)
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

            // Zoom controls
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.3f).coerceAtMost(3.5f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom +", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 2. Main Timeline Area (Left Action Panel + Center-Playhead Multi-Layer Viewport)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0C0C12))
        ) {
            val totalViewportWidth = maxWidth
            val leftHeaderWidth = 84.dp
            val timelineAreaWidth = totalViewportWidth - leftHeaderWidth
            val centerPlayheadOffsetDp = timelineAreaWidth / 2

            // Auto-scroll timeline under the fixed center playhead during playback
            LaunchedEffect(currentPositionMs, isPlaying) {
                if (isPlaying) {
                    val targetPx = with(density) { (currentPositionMs / 1000f * dpPerSecond).dp.toPx() }
                    horizontalScrollState.scrollTo(targetPx.roundToInt())
                }
            }

            // Sync scroll if position changed externally while paused (and not actively user scrubbing)
            LaunchedEffect(currentPositionMs) {
                if (!isPlaying && !horizontalScrollState.isScrollInProgress) {
                    val targetPx = with(density) { (currentPositionMs / 1000f * dpPerSecond).dp.toPx() }
                    if (Math.abs(horizontalScrollState.value - targetPx) > 15) {
                        horizontalScrollState.scrollTo(targetPx.roundToInt())
                    }
                }
            }

            // Real-time timeline drag/scrub gestures: pause playback when scrolling begins and notify onDragEnd when stopped
            LaunchedEffect(horizontalScrollState.isScrollInProgress) {
                if (horizontalScrollState.isScrollInProgress) {
                    onDragStart?.invoke()
                } else {
                    onDragEnd?.invoke()
                }
            }

            // Continuous real-time bidirectional position update while dragging the timeline
            LaunchedEffect(horizontalScrollState.value) {
                if (horizontalScrollState.isScrollInProgress) {
                    val targetMs = with(density) {
                        ((horizontalScrollState.value.toDp().value / dpPerSecond) * 1000f).toLong().coerceIn(0L, safeTotalMs)
                    }
                    onSeek(targetMs)
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT QUICK ACTION COLUMN (CapCut Style: Silenciar áudio, Capa, Lane Labels)
                Column(
                    modifier = Modifier
                        .width(leftHeaderWidth)
                        .fillMaxHeight()
                        .background(Color(0xFF14141F))
                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)))
                        .verticalScroll(verticalScrollState)
                ) {
                    // Top header corresponding to Time Ruler
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0xFF161622)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Trilhas", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Main Track Quick Action (Mute Audio / Capa)
                    Surface(
                        color = Color(0xFF161622),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF252538)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryPurpleVariant)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Vídeo", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = onAddMedia,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Adicionar vídeo", tint = PrimaryPurpleLight, modifier = Modifier.size(13.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { onToggleVideoMute?.invoke() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isVideoMuted) DangerRed.copy(alpha = 0.2f) else Color(0xFF222233),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isVideoMuted) DangerRed else Color(0xFF333344)),
                                    modifier = Modifier.size(width = 34.dp, height = 28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isVideoMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Silenciar áudio",
                                            tint = if (isVideoMuted) DangerRed else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { onToggleVideoLock?.invoke() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isVideoLocked) GoldPremium.copy(alpha = 0.2f) else Color(0xFF222233),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isVideoLocked) GoldPremium else Color(0xFF333344)),
                                    modifier = Modifier.size(width = 34.dp, height = 28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isVideoLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = "Travar vídeo",
                                            tint = if (isVideoLocked) GoldPremium else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic Overlay Lanes Headers
                    for (lane in 0 until dynamicOverlayLaneCount) {
                        Surface(
                            color = Color(0xFF12121C),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (lane % 2 == 0) LayerOverlayBg else LayerTextBg)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Camada ${lane + 1}", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(
                                    onClick = { onAddOverlay?.invoke() ?: onAddMedia() },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Adicionar sobreposição", tint = AccentPink, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // Audio Track Headers
                    val audioCount = max(1, project.audios.size)
                    for (aIdx in 0 until audioCount) {
                        val track = project.audios.getOrNull(aIdx)
                        Surface(
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = LayerAudioWave, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Áudio ${aIdx + 1}", color = LayerAudioWave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = onAddAudio,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Adicionar áudio", tint = LayerAudioWave, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                // RIGHT TIMELINE VIEWPORT WITH CENTER FIXED PLAYHEAD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val timelineContentWidthDp = msToDp(safeTotalMs)

                    // Scrollable Timeline Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(centerPlayheadOffsetDp + timelineContentWidthDp + centerPlayheadOffsetDp)
                                .fillMaxHeight()
                                .verticalScroll(verticalScrollState)
                        ) {
                            // 1. Time Ruler (Starts exactly at centerPlayheadOffsetDp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color(0xFF13131D))
                                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E1E2A)))
                            ) {
                                Box(modifier = Modifier.offset(x = centerPlayheadOffsetDp)) {
                                    TimeRuler(
                                        totalDurationMs = safeTotalMs,
                                        dpPerSecond = dpPerSecond,
                                        zoomScale = zoomScale,
                                        widthDp = timelineContentWidthDp + 200.dp,
                                        onSeek = onSeek,
                                        onDragStart = onDragStart,
                                        onDragEnd = onDragEnd
                                    )
                                }
                            }

                            // 2. Main Video Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .background(Color(0xFF161622))
                                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF252538)))
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(modifier = Modifier.offset(x = centerPlayheadOffsetDp)) {
                                    if (project.clips.isEmpty()) {
                                        Surface(
                                            onClick = onAddMedia,
                                            shape = RoundedCornerShape(8.dp),
                                            color = SurfaceElevated,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                                            modifier = Modifier
                                                .width(220.dp)
                                                .fillMaxHeight()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryPurpleVariant)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("+ Adicionar vídeo / foto", color = TextSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            project.clips.forEachIndexed { index, clip ->
                                                val isSelected = clip.id == selectedClipId
                                                val clipDurationMs = TimelineUtils.calculateClipTimelineDuration(clip)
                                                val clipWidthDp = max(45f, (clipDurationMs / 1000f) * dpPerSecond).dp

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = LayerVideoBg,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155)
                                                    ),
                                                    modifier = Modifier
                                                        .width(clipWidthDp)
                                                        .fillMaxHeight()
                                                        .clickable { onSelectClip(clip.id) }
                                                        .testTag("track_clip_${clip.id}")
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize()) {
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

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                                                    )
                                                                )
                                                        )

                                                        Column(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                                color = Color.White.copy(alpha = 0.8f),
                                                                fontSize = 9.sp
                                                            )
                                                        }

                                                        // Direct Resize Handles when selected (Left |< and Right >|)
                                                        if (isSelected && !isVideoLocked) {
                                                            // Left trim handle
                                                            var currentTrimStart = clip.trimStartMs
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.CenterStart)
                                                                    .width(14.dp)
                                                                    .fillMaxHeight()
                                                                    .background(Color(0xFFFBBF24).copy(alpha = 0.9f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                                                    .pointerInput(clip.id, dpPerSecond) {
                                                                        detectDragGestures(
                                                                            onDragStart = { currentTrimStart = clip.trimStartMs },
                                                                            onDrag = { change, dragAmount ->
                                                                                change.consume()
                                                                                val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                                if (pxPerSec > 0) {
                                                                                    val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                                                                    val effectiveEnd = if (clip.trimEndMs > 0) clip.trimEndMs else clip.durationMs
                                                                                    currentTrimStart = (currentTrimStart + deltaMs).coerceIn(0L, effectiveEnd - 200L)
                                                                                    onTrimClip?.invoke(clip.id, currentTrimStart, clip.trimEndMs)
                                                                                }
                                                                            }
                                                                        )
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.ChevronLeft, contentDescription = "Trim início", tint = Color.Black, modifier = Modifier.size(12.dp))
                                                            }

                                                            // Right trim handle
                                                            var currentTrimEnd = if (clip.trimEndMs > 0) clip.trimEndMs else clip.durationMs
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.CenterEnd)
                                                                    .width(14.dp)
                                                                    .fillMaxHeight()
                                                                    .background(Color(0xFFFBBF24).copy(alpha = 0.9f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                                                    .pointerInput(clip.id, dpPerSecond) {
                                                                        detectDragGestures(
                                                                            onDragStart = { currentTrimEnd = if (clip.trimEndMs > 0) clip.trimEndMs else clip.durationMs },
                                                                            onDrag = { change, dragAmount ->
                                                                                change.consume()
                                                                                val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                                if (pxPerSec > 0) {
                                                                                    val deltaMs = (dragAmount.x / pxPerSec * 1000f).toLong()
                                                                                    currentTrimEnd = (currentTrimEnd + deltaMs).coerceAtLeast(clip.trimStartMs + 200L)
                                                                                    onTrimClip?.invoke(clip.id, clip.trimStartMs, currentTrimEnd)
                                                                                }
                                                                            }
                                                                        )
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.ChevronRight, contentDescription = "Trim fim", tint = Color.Black, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    }
                                                }

                                                // Transition Marker between clips
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
                                            }

                                            // Add Media (+) button at the end of the main track
                                            Surface(
                                                onClick = onAddMedia,
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .size(width = 44.dp, height = 64.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Add, contentDescription = "Adicionar clipe", tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Dynamic Overlay Lanes (Photos, Videos, Stickers, Texts packed without overlap)
                            for (laneIdx in 0 until dynamicOverlayLaneCount) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(if (laneIdx % 2 == 0) Color(0xFF12121C) else Color(0xFF101018))
                                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222233)))
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(modifier = Modifier.offset(x = centerPlayheadOffsetDp)) {
                                        val itemsInLane = packedOverlayLanes.filter { it.laneIndex == laneIdx }
                                        if (itemsInLane.isEmpty()) {
                                            Surface(
                                                onClick = { onAddOverlay?.invoke() ?: onAddMedia() },
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF1E1E2C).copy(alpha = 0.5f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333348)),
                                                modifier = Modifier
                                                    .width(180.dp)
                                                    .fillMaxHeight()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentPink, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("+ Camada de sobreposição", color = TextTertiary, fontSize = 10.sp)
                                                }
                                            }
                                        } else {
                                            itemsInLane.forEach { packed ->
                                                val overlayItem = packed.item
                                                val startDp = msToDp(packed.startMs)
                                                val widthDp = max(48f, (packed.durationMs / 1000f) * dpPerSecond).dp
                                                val isSelected = overlayItem.isSelected

                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = startDp)
                                                        .width(widthDp)
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            when (overlayItem) {
                                                                is UnifiedOverlayItem.TextOverlay -> LayerTextBg
                                                                is UnifiedOverlayItem.StickerOverlay -> LayerOverlayBg
                                                                is UnifiedOverlayItem.VfxOverlay -> LayerVfxBg
                                                            }
                                                        )
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) Color.White else when (overlayItem) {
                                                                is UnifiedOverlayItem.TextOverlay -> LayerTextBorder
                                                                is UnifiedOverlayItem.StickerOverlay -> AccentPink
                                                                is UnifiedOverlayItem.VfxOverlay -> LayerVfxBorder
                                                            },
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .testTag("overlay_block_${overlayItem.id}")
                                                ) {
                                                    // Center Drag Area (moves entire block along timeline)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = if (isSelected) 14.dp else 6.dp)
                                                            .pointerInput(overlayItem.id) {
                                                                detectTapGestures(
                                                                    onTap = {
                                                                        when (overlayItem) {
                                                                            is UnifiedOverlayItem.TextOverlay -> onSelectText(overlayItem.id)
                                                                            is UnifiedOverlayItem.StickerOverlay -> onSelectSticker(overlayItem.id)
                                                                            is UnifiedOverlayItem.VfxOverlay -> onSelectVfx?.invoke(overlayItem.id)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                            .pointerInput(overlayItem.id, dpPerSecond) {
                                                                var startMs = overlayItem.startTimeMs
                                                                var accumulatedPx = 0f
                                                                detectDragGestures(
                                                                    onDragStart = {
                                                                        startMs = overlayItem.startTimeMs
                                                                        accumulatedPx = 0f
                                                                        when (overlayItem) {
                                                                            is UnifiedOverlayItem.TextOverlay -> onSelectText(overlayItem.id)
                                                                            is UnifiedOverlayItem.StickerOverlay -> onSelectSticker(overlayItem.id)
                                                                            is UnifiedOverlayItem.VfxOverlay -> onSelectVfx?.invoke(overlayItem.id)
                                                                        }
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        accumulatedPx += dragAmount.x
                                                                        val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                        if (pxPerSec > 0) {
                                                                            val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                            val newStart = (startMs + deltaMs).coerceIn(0L, safeTotalMs - 200L)
                                                                            when (overlayItem) {
                                                                                is UnifiedOverlayItem.TextOverlay -> onMoveText?.invoke(overlayItem.id, newStart)
                                                                                is UnifiedOverlayItem.StickerOverlay -> onMoveSticker?.invoke(overlayItem.id, newStart)
                                                                                is UnifiedOverlayItem.VfxOverlay -> onMoveVfx?.invoke(overlayItem.id, newStart)
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                            },
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = when (overlayItem) {
                                                                is UnifiedOverlayItem.TextOverlay -> Icons.Default.TextFields
                                                                is UnifiedOverlayItem.StickerOverlay -> {
                                                                    if (overlayItem.sticker.isVideo) Icons.Default.Movie
                                                                    else if (overlayItem.sticker.isGif) Icons.Default.Gif
                                                                    else Icons.Default.Image
                                                                }
                                                                is UnifiedOverlayItem.VfxOverlay -> Icons.Default.AutoAwesome
                                                            },
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = overlayItem.name,
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    // Left & Right Resize Handles when Selected
                                                    if (isSelected) {
                                                        // Left handle: adjusts start
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterStart)
                                                                .width(13.dp)
                                                                .fillMaxHeight()
                                                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                                .pointerInput(overlayItem.id, dpPerSecond) {
                                                                    var initialStart = overlayItem.startTimeMs
                                                                    var originalEnd = overlayItem.startTimeMs + overlayItem.durationMs
                                                                    var accumulatedPx = 0f
                                                                    detectDragGestures(
                                                                        onDragStart = {
                                                                            initialStart = overlayItem.startTimeMs
                                                                            originalEnd = overlayItem.startTimeMs + overlayItem.durationMs
                                                                            accumulatedPx = 0f
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            accumulatedPx += dragAmount.x
                                                                            val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                            if (pxPerSec > 0) {
                                                                                val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                                val newStart = (initialStart + deltaMs).coerceIn(0L, originalEnd - 300L)
                                                                                val newDur = originalEnd - newStart
                                                                                when (overlayItem) {
                                                                                    is UnifiedOverlayItem.TextOverlay -> onTrimText?.invoke(overlayItem.id, newStart, newDur) ?: onMoveText?.invoke(overlayItem.id, newStart)
                                                                                    is UnifiedOverlayItem.StickerOverlay -> onTrimSticker?.invoke(overlayItem.id, newStart, newDur) ?: onMoveSticker?.invoke(overlayItem.id, newStart)
                                                                                    is UnifiedOverlayItem.VfxOverlay -> onTrimVfx?.invoke(overlayItem.id, newStart, newDur) ?: onMoveVfx?.invoke(overlayItem.id, newStart)
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.ChevronLeft, contentDescription = "Trim início", tint = Color.Black, modifier = Modifier.size(11.dp))
                                                        }

                                                        // Right handle: adjusts duration
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterEnd)
                                                                .width(13.dp)
                                                                .fillMaxHeight()
                                                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                                .pointerInput(overlayItem.id, dpPerSecond) {
                                                                    var initialDur = overlayItem.durationMs
                                                                    var accumulatedPx = 0f
                                                                    detectDragGestures(
                                                                        onDragStart = {
                                                                            initialDur = overlayItem.durationMs
                                                                            accumulatedPx = 0f
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            accumulatedPx += dragAmount.x
                                                                            val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                            if (pxPerSec > 0) {
                                                                                val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                                val newDur = (initialDur + deltaMs).coerceAtLeast(300L)
                                                                                when (overlayItem) {
                                                                                    is UnifiedOverlayItem.TextOverlay -> onTrimText?.invoke(overlayItem.id, overlayItem.startTimeMs, newDur)
                                                                                    is UnifiedOverlayItem.StickerOverlay -> onTrimSticker?.invoke(overlayItem.id, overlayItem.startTimeMs, newDur)
                                                                                    is UnifiedOverlayItem.VfxOverlay -> onTrimVfx?.invoke(overlayItem.id, overlayItem.startTimeMs, newDur)
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.ChevronRight, contentDescription = "Trim fim", tint = Color.Black, modifier = Modifier.size(11.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Audio Tracks with Waveforms & Direct Trimming
                            if (project.audios.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(Color(0xFF0F172A))
                                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B)))
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(modifier = Modifier.offset(x = centerPlayheadOffsetDp)) {
                                        Surface(
                                            onClick = onAddAudio,
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                            modifier = Modifier
                                                .width(190.dp)
                                                .fillMaxHeight()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = LayerAudioWave, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("+ Adicionar trilha de áudio", color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                project.audios.forEachIndexed { aIdx, audio ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(if (aIdx % 2 == 0) Color(0xFF0F172A) else Color(0xFF141E33))
                                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1E293B)))
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Box(modifier = Modifier.offset(x = centerPlayheadOffsetDp)) {
                                            val startDp = msToDp(audio.timelineStartMs)
                                            val effectiveDur = max(TimelineUtils.calculateAudioEffectiveDuration(audio), 1000L)
                                            val widthDp = max(60f, (effectiveDur / 1000f) * dpPerSecond).dp
                                            val isSelected = audio.id == selectedAudioId
                                            val wave = waveforms[audio.id] ?: emptyList()

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = startDp)
                                                    .width(widthDp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(LayerAudioBg)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) Color.White else LayerAudioWave,
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .testTag("track_audio_${audio.id}")
                                            ) {
                                                // Center drag to move audio position
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = if (isSelected) 14.dp else 6.dp)
                                                        .pointerInput(audio.id) {
                                                            detectTapGestures(
                                                                onTap = { onSelectAudio(audio.id) }
                                                            )
                                                        }
                                                        .pointerInput(audio.id, dpPerSecond) {
                                                            var startMs = audio.timelineStartMs
                                                            var accumulatedPx = 0f
                                                            detectDragGestures(
                                                                onDragStart = {
                                                                    startMs = audio.timelineStartMs
                                                                    accumulatedPx = 0f
                                                                    onSelectAudio(audio.id)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    accumulatedPx += dragAmount.x
                                                                    val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                    if (pxPerSec > 0) {
                                                                        val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                        val newStart = (startMs + deltaMs).coerceIn(0L, safeTotalMs - 300L)
                                                                        onMoveAudio?.invoke(audio.id, newStart)
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = audio.name,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.widthIn(max = 80.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    AudioWaveformBar(
                                                        amplitudes = wave,
                                                        isMuted = audio.isMuted || isAudioMuted,
                                                        color = LayerAudioWave,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(0.8f)
                                                    )
                                                }

                                                // Left & Right Trim Handles for Audio
                                                if (isSelected && !isAudioLocked) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(13.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                                            .pointerInput(audio.id, dpPerSecond) {
                                                                var initialTrimStart = audio.trimStartMs
                                                                var accumulatedPx = 0f
                                                                detectDragGestures(
                                                                    onDragStart = {
                                                                        initialTrimStart = audio.trimStartMs
                                                                        accumulatedPx = 0f
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        accumulatedPx += dragAmount.x
                                                                        val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                        if (pxPerSec > 0) {
                                                                            val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                            val newTrimStart = (initialTrimStart + deltaMs).coerceAtLeast(0L)
                                                                            onTrimAudio?.invoke(audio.id, newTrimStart, audio.trimEndMs)
                                                                        }
                                                                    }
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Trim início", tint = Color.Black, modifier = Modifier.size(11.dp))
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(13.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                                            .pointerInput(audio.id, dpPerSecond) {
                                                                val effectiveEnd = if (audio.trimEndMs > 0) audio.trimEndMs else audio.durationMs
                                                                var initialTrimEnd = effectiveEnd
                                                                var accumulatedPx = 0f
                                                                detectDragGestures(
                                                                    onDragStart = {
                                                                        initialTrimEnd = if (audio.trimEndMs > 0) audio.trimEndMs else audio.durationMs
                                                                        accumulatedPx = 0f
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        accumulatedPx += dragAmount.x
                                                                        val pxPerSec = with(density) { dpPerSecond.dp.toPx() }
                                                                        if (pxPerSec > 0) {
                                                                            val deltaMs = (accumulatedPx / pxPerSec * 1000f).toLong()
                                                                            val newTrimEnd = (initialTrimEnd + deltaMs).coerceAtLeast(audio.trimStartMs + 300L)
                                                                            onTrimAudio?.invoke(audio.id, audio.trimStartMs, newTrimEnd)
                                                                        }
                                                                    }
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.ChevronRight, contentDescription = "Trim fim", tint = Color.Black, modifier = Modifier.size(11.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // FIXED CENTER PLAYHEAD NEEDLE (Runs vertically down across the entire timeline viewport at centerPlayheadOffsetDp)
                    Box(
                        modifier = Modifier
                            .offset(x = centerPlayheadOffsetDp - 12.dp)
                            .width(24.dp)
                            .fillMaxHeight()
                            .zIndex(100f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Playhead top triangle/pointer
                        Canvas(
                            modifier = Modifier
                                .size(14.dp, 10.dp)
                                .offset(y = 1.dp)
                        ) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path, color = Color.White)
                        }

                        // Playhead crisp white vertical line through all tracks
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                                .shadow(3.dp, spotColor = Color.White)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Time Ruler with timestamp markers.
 */
@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    dpPerSecond: Float,
    zoomScale: Float,
    widthDp: Dp,
    onSeek: (Long) -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val totalSeconds = (totalDurationMs / 1000).toInt() + 4

    Surface(
        color = Color(0xFF13131D),
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
                detectDragGestures(
                    onDragStart = { onDragStart?.invoke() },
                    onDragEnd = { onDragEnd?.invoke() },
                    onDragCancel = { onDragEnd?.invoke() },
                    onDrag = { change, _ ->
                        change.consume()
                        val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }
                        if (pxPerSecond > 0) {
                            val sec = change.position.x / pxPerSecond
                            val ms = (sec * 1000f).toLong().coerceIn(0L, totalDurationMs)
                            onSeek(ms)
                        }
                    }
                )
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
