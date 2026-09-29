package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.view.ViewGroup
import coil.compose.AsyncImage
import com.example.data.MockData
import com.example.effect.*
import com.example.model.*
import com.example.overlay.StickerLayer
import com.example.overlay.StickerPresetsRepository
import com.example.overlay.TextOverlayLayer
import com.example.overlay.parseColorSafely
import com.example.template.TextTemplate
import com.example.template.TextTemplateRepository
import com.example.transition.TransitionAwareMediaSurface
import com.example.transition.TransitionType
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.ToolPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToCaptions: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()
    val project = uiState.currentProject

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.pausePlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pausePlayback()
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhum projeto selecionado", color = TextSecondary)
        }
        return
    }

    val totalDurationMs = viewModel.getTotalDurationMs()
    val clipAtPlayhead = com.example.util.TimelineUtils.findClipAtTimelinePosition(project.clips, uiState.currentPositionMs)?.clip
    val currentClip = project.clips.find { it.id == uiState.selectedClipId } ?: clipAtPlayhead ?: project.clips.firstOrNull()

    // Formatter for time
    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val tenths = (ms % 1000) / 100
        return String.format("%02d:%02d.%01d", min, sec, tenths)
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onNavigateBack,
                    shape = CircleShape,
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Project Title with Autosave indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = project.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Autosalvo",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Resolution Badge "1080P v"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable { onNavigateToExport() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1080P",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botão Exportar (Vibrant purple pill com gradiente e ícone)
                    Surface(
                        onClick = onNavigateToExport,
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .height(34.dp)
                            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = PrimaryPurple.copy(alpha = 0.5f))
                            .testTag("export_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(GradientPrimary)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.IosShare,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Video Canvas / Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Video Frame with Aspect Ratio
                val frameRatio = project.aspectRatio.ratio
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(frameRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111111)),
                    contentAlignment = Alignment.Center
                ) {
                    // Real Media3 Player / Photo Display with Real Effects & Transformations
                    if (project.clips.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum clipe na Timeline",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        TransitionAwareMediaSurface(
                            clips = project.clips,
                            activeVFX = project.activeVFX,
                            currentPlayheadMs = uiState.currentPositionMs,
                            activeClip = currentClip,
                            isPhotoActive = playbackState.isPhotoActive,
                            activePhotoPath = playbackState.activePhotoPath,
                            exoPlayer = viewModel.playerManager.exoPlayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Buffering Indicator
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            color = PrimaryPurpleVariant,
                            modifier = Modifier.size(36.dp).align(Alignment.Center)
                        )
                    }

                    // Error Message overlay if missing media or decode failure
                    if (playbackState.errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DangerRed.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = playbackState.errorMessage ?: "",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Real Stickers and Animated GIFs Layer
                    StickerLayer(
                        stickers = project.stickers,
                        currentPlayheadMs = uiState.currentPositionMs,
                        selectedStickerId = uiState.selectedStickerId,
                        onSelectSticker = {
                            viewModel.selectSticker(it)
                            if (it != null) viewModel.setActivePanel(ToolPanel.STICKER)
                        },
                        onMoveSticker = { id, newX, newY -> viewModel.updateStickerPosition(id, newX, newY) },
                        onDeleteSticker = { viewModel.removeSticker(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Real Dynamic & Animated Text Overlays Layer
                    TextOverlayLayer(
                        texts = project.texts,
                        currentPlayheadMs = uiState.currentPositionMs,
                        selectedTextId = uiState.selectedTextId,
                        onSelectText = {
                            viewModel.selectTextOverlay(it)
                            if (it != null) viewModel.setActivePanel(ToolPanel.TEXT)
                        },
                        onMoveText = { id, newX, newY -> viewModel.updateTextOverlayPosition(id, newX, newY) },
                        onDeleteText = { viewModel.removeTextOverlay(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtitles overlay
                    val activeSubtitle = project.subtitles.find {
                        uiState.currentPositionMs in it.startTimeMs..it.endTimeMs && it.isEnabled
                    }
                    if (activeSubtitle != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = activeSubtitle.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Watermark indicator (if not premium)
                    if (!uiState.isPremiumUser) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Boti Editor",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Play / Pause Overlay Button
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .align(Alignment.Center)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Player Controls Bar (Tela 5 de layout app.png)
            val displayPosition = if (isScrubbing) scrubPositionMs.toLong() else uiState.currentPositionMs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Indicador de Tempo em capsule moderna
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "${formatTime(displayPosition)} / ${formatTime(totalDurationMs)}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Play / Pause Central Hero Button
                Surface(
                    onClick = { viewModel.togglePlayback() },
                    shape = CircleShape,
                    color = if (uiState.isPlaying) SurfaceElevated else PrimaryPurple,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (uiState.isPlaying) BorderSubtle else PrimaryPurpleLight
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(if (uiState.isPlaying) 0.dp else 10.dp, CircleShape, spotColor = PrimaryPurple)
                        .testTag("btn_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Ações da Direita: Dividir Rápido, Desfazer, Refazer, Tela Cheia
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.splitClipAtPlayhead() },
                        enabled = currentClip != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Dividir",
                            tint = if (currentClip != null) PrimaryPurpleLight else TextTertiary.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndo,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("editor_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Desfazer",
                            tint = if (uiState.canUndo) Color.White else TextTertiary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("editor_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Refazer",
                            tint = if (uiState.canRedo) Color.White else TextTertiary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setFeedback("Modo de visualização expandida") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Tela Cheia",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Multi-Track Visual Timeline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundElevated)
                    .padding(vertical = 8.dp)
            ) {
                // Media Track (Clips)
                if (project.clips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = onNavigateToImport)
                            .background(SurfaceElevated, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Adicionar vídeos e fotos para começar",
                            color = PrimaryPurpleVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(project.clips, key = { _, clip -> clip.id }) { index, clip ->
                            val isSelected = clip.id == uiState.selectedClipId
                            val clipDurationMs = com.example.util.TimelineUtils.calculateClipTimelineDuration(clip)
                            val clipWidthDp = (clipDurationMs / 300).coerceIn(85L, 240L).toInt().dp

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceElevated,
                                modifier = Modifier
                                    .width(clipWidthDp)
                                    .fillMaxHeight()
                                    .clickable { viewModel.selectClip(clip.id, seekToClipStart = (clip.id != uiState.selectedClipId)) }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) PrimaryPurpleVariant else BorderStrong,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Box {
                                    val clipThumbModel = when {
                                        clip.thumbnailPath.isNotBlank() -> java.io.File(clip.thumbnailPath)
                                        clip.localPath.isNotBlank() -> java.io.File(clip.localPath)
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
                                            .background(Color.Black.copy(alpha = 0.35f))
                                    )

                                    // Top row: Reorder arrows if selected
                                    if (isSelected) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        ) {
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { viewModel.moveClipLeft(clip.id) },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronLeft,
                                                        contentDescription = "Mover para esquerda",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (index < project.clips.lastIndex) {
                                                IconButton(
                                                    onClick = { viewModel.moveClipRight(clip.id) },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = "Mover para direita",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Bottom row: Title & Duration
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
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${String.format("%.1f", clipDurationMs / 1000f)}s${if (clip.speed != 1.0f) " • ${clip.speed}x" else ""}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 9.sp
                                        )
                                    }

                                    if (clip.transition != null) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PrimaryPurple,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Shuffle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Floating round purple (+) button matching Screen 5 of layout app.png
                        item {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryPurple)
                                    .clickable(onClick = onNavigateToImport)
                                    .testTag("timeline_add_clip"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Adicionar Mídia",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Multi-Track Audio Section with Waveforms
                if (project.audios.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        project.audios.forEach { audio ->
                            val isSelected = audio.id == uiState.selectedAudioTrackId
                            val waveformSamples = uiState.waveforms[audio.id] ?: emptyList()

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) SurfaceElevated else SurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PrimaryPurpleVariant else BorderStrong
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clickable {
                                        viewModel.selectAudioTrack(audio.id)
                                        viewModel.setActivePanel(ToolPanel.AUDIO)
                                    }
                                    .testTag("audio_track_${audio.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (audio.isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (audio.isMuted) TextTertiary else PrimaryPurpleVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = audio.name,
                                        color = if (audio.isMuted) TextTertiary else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier.widthIn(max = 110.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Real Waveform visualization in timeline
                                    AudioWaveformBar(
                                        amplitudes = waveformSamples,
                                        isMuted = audio.isMuted,
                                        color = if (isSelected) PrimaryPurple else PrimaryPurpleVariant.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (audio.isMuted) "Mudo" else "${(audio.volume * 100).toInt()}%",
                                        color = TextTertiary,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { viewModel.toggleAudioTrackMute(audio.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (audio.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute/Unmute",
                                            tint = if (audio.isMuted) TextTertiary else TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceDark.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(26.dp)
                            .clickable { viewModel.setActivePanel(ToolPanel.AUDIO) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Adicionar trilha de áudio ou música", color = TextTertiary, fontSize = 10.sp)
                        }
                    }
                }

                // Timeline Overlays Tracks (Texts & Stickers)
                if (project.texts.isNotEmpty() || project.stickers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (project.texts.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(project.texts) { txt ->
                                    val isSel = txt.id == uiState.selectedTextId
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSel) PrimaryPurpleSoft else SurfaceDark,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSel) PrimaryPurple else BorderStrong
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.selectTextOverlay(txt.id)
                                                viewModel.setActivePanel(ToolPanel.TEXT)
                                            }
                                            .testTag("timeline_text_${txt.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.TextFields, contentDescription = null, tint = if (isSel) PrimaryPurpleVariant else TextSecondary, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = txt.text.take(14),
                                                color = if (isSel) TextPrimary else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (project.stickers.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(project.stickers) { stk ->
                                    val isSel = stk.id == uiState.selectedStickerId
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSel) PrimaryPurpleSoft else SurfaceDark,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSel) PrimaryPurple else BorderStrong
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.selectSticker(stk.id)
                                                viewModel.setActivePanel(ToolPanel.STICKER)
                                            }
                                            .testTag("timeline_sticker_${stk.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(if (stk.isGif) Icons.Default.Gif else Icons.Default.EmojiEmotions, contentDescription = null, tint = if (isSel) PrimaryPurpleVariant else TextSecondary, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stk.name.take(14),
                                                color = if (isSel) TextPrimary else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Bottom Dock (Tela 5 de layout app.png)
            if (uiState.activePanel == ToolPanel.NONE && uiState.selectedClipId == null) {
                Surface(
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButton(icon = Icons.Default.ContentCut, label = "Editar", onClick = { viewModel.setActivePanel(ToolPanel.EDIT_TOOLS) })
                        ToolButton(icon = Icons.Default.MusicNote, label = "Áudio", onClick = { viewModel.setActivePanel(ToolPanel.AUDIO) })
                        ToolButton(icon = Icons.Default.TextFields, label = "Texto", onClick = { viewModel.setActivePanel(ToolPanel.TEXT) })
                        ToolButton(icon = Icons.Default.Layers, label = "Sobrepor", onClick = { viewModel.setActivePanel(ToolPanel.STICKER) })
                        ToolButton(icon = Icons.Default.AutoAwesome, label = "Efeitos", onClick = { viewModel.setActivePanel(ToolPanel.VFX) })
                        ToolButton(icon = Icons.Default.Shuffle, label = "Transição", onClick = { viewModel.setActivePanel(ToolPanel.TRANSITION) })
                        ToolButton(icon = Icons.Default.FilterFrames, label = "Filtros", onClick = { viewModel.setActivePanel(ToolPanel.FILTER) })
                        ToolButton(icon = Icons.Default.Tune, label = "Ajustes", onClick = { viewModel.setActivePanel(ToolPanel.ADJUST) })
                        ToolButton(icon = Icons.Default.AspectRatio, label = "Formato", onClick = { viewModel.setActivePanel(ToolPanel.CANVAS) })
                    }
                }
            }

            // Sub-painel: Tela 6 (FERRAMENTAS - EDITAR) ou ferramentas específicas
            val showEditTools = uiState.activePanel == ToolPanel.EDIT_TOOLS || (uiState.selectedClipId != null && uiState.activePanel == ToolPanel.NONE)

            if (showEditTools) {
                Surface(
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Header: '<' Voltar | "Editar" | 'v' Confirmar (Tela 6 da referência)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.setActivePanel(ToolPanel.NONE)
                                    viewModel.selectClip(null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Editar",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = {
                                    viewModel.setActivePanel(ToolPanel.NONE)
                                    viewModel.selectClip(null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Confirmar", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grid 3 colunas x 4 linhas de Ferramentas de Edição (Tela 6)
                        val editTools = listOf(
                            Triple("Cortar", Icons.Default.ContentCut) { viewModel.setActivePanel(ToolPanel.TRIM) },
                            Triple("Dividir", Icons.Default.CallSplit) { viewModel.splitClipAtPlayhead() },
                            Triple("Excluir", Icons.Default.Delete) { viewModel.deleteSelectedClip(); viewModel.setActivePanel(ToolPanel.NONE) },
                            Triple("Velocidade", Icons.Default.Speed) { viewModel.setActivePanel(ToolPanel.SPEED) },
                            Triple("Volume", Icons.Default.VolumeUp) { viewModel.setActivePanel(ToolPanel.AUDIO) },
                            Triple("Ajustes", Icons.Default.Tune) { viewModel.setActivePanel(ToolPanel.ADJUST) },
                            Triple("Duplicar", Icons.Default.ContentCopy) { viewModel.duplicateClip() },
                            Triple("Inverter", Icons.Default.Refresh) { viewModel.setFeedback("Efeito reverso aplicado ao clipe.") },
                            Triple("Congelar", Icons.Default.AcUnit) { viewModel.setFeedback("Quadro congelado criado na linha do tempo.") },
                            Triple("Recortar", Icons.Default.Crop) { viewModel.setActivePanel(ToolPanel.CANVAS) },
                            Triple("Substituir", Icons.Default.SwapHoriz) { onNavigateToImport() },
                            Triple("Opacidade", Icons.Default.Opacity) { viewModel.setActivePanel(ToolPanel.TRANSFORM) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            editTools.forEach { (label, icon, action) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 70.dp)
                                        .clickable(onClick = action)
                                        .testTag("edit_tool_$label")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (label == "Excluir") DangerRed else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (label == "Excluir") DangerRed else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (uiState.activePanel != ToolPanel.NONE) {
                Surface(
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(14.dp)
                    ) {
                        when (uiState.activePanel) {
                            ToolPanel.SPEED -> SpeedPanel(currentClip, viewModel)
                            ToolPanel.FILTER -> FilterPanel(currentClip, viewModel)
                            ToolPanel.ADJUST -> AdjustPanel(currentClip, viewModel)
                            ToolPanel.TRANSFORM -> TransformPanel(currentClip, viewModel)
                            ToolPanel.AUDIO -> AudioPanel(viewModel)
                            ToolPanel.TEXT -> TextPanel(project, uiState.selectedTextId, viewModel)
                            ToolPanel.TEMPLATES -> TemplatesPanel(project, viewModel)
                            ToolPanel.STICKER -> StickerPanel(project, uiState.selectedStickerId, viewModel)
                            ToolPanel.TRANSITION -> TransitionPanel(currentClip, viewModel)
                            ToolPanel.CANVAS -> CanvasPanel(project, viewModel)
                            ToolPanel.VFX -> VFXPanel(project, viewModel)
                            ToolPanel.TRIM -> TrimPanel(currentClip, viewModel)
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp)
            .testTag("tool_$label")
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) PrimaryPurple else SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSelected) PrimaryPurpleLight else BorderSubtle
            ),
            modifier = Modifier
                .size(46.dp)
                .shadow(if (isSelected) 8.dp else 0.dp, RoundedCornerShape(14.dp), spotColor = PrimaryPurple)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PrimaryPurpleLight else TextSecondary
        )
    }
}

@Composable
private fun SpeedPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    val speed = clip?.speed ?: 1.0f
    Column {
        Text("Velocidade do Clipe: ${speed}x", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.2f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { s ->
                val isSel = speed == s
                Button(
                    onClick = { viewModel.updateClipSpeed(s) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSel) PrimaryPurple else SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("${s}x", fontSize = 12.sp, color = if (isSel) Color.White else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    val currentFilter = clip?.filter ?: "Original"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filtros de Cor", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (currentFilter != "Original") {
                TextButton(onClick = { viewModel.removeClipFilter(clip?.id) }) {
                    Text("Remover Filtro", color = DangerRed, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MockData.filtersList.forEach { filter ->
                val isSel = currentFilter == filter
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) PrimaryPurpleSoft else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurple else BorderStrong),
                    modifier = Modifier.clickable { viewModel.updateClipFilter(filter) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSel) PrimaryPurpleVariant else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSel && filter != "Original") {
                            Text("Ativo", color = PrimaryPurpleVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    if (clip == null) {
        Text("Selecione um clipe na timeline para ajustar a imagem.", color = TextSecondary, fontSize = 13.sp)
        return
    }

    var brightness by remember(clip.id, clip.brightness) { mutableFloatStateOf(clip.brightness) }
    var contrast by remember(clip.id, clip.contrast) { mutableFloatStateOf(clip.contrast) }
    var saturation by remember(clip.id, clip.saturation) { mutableFloatStateOf(clip.saturation) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ajustes de Imagem",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            TextButton(
                onClick = {
                    brightness = 0f
                    contrast = 0f
                    saturation = 0f
                    viewModel.resetClipAdjustments(clip.id)
                }
            ) {
                Text("Redefinir Tudo", color = DangerRed, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Brilho
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Brilho", color = TextSecondary, fontSize = 12.sp)
            Text("${brightness.toInt()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = brightness,
            onValueChange = {
                brightness = it
                viewModel.updateClipAdjustments(brightness, contrast, saturation)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Contraste
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Contraste", color = TextSecondary, fontSize = 12.sp)
            Text("${contrast.toInt()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = contrast,
            onValueChange = {
                contrast = it
                viewModel.updateClipAdjustments(brightness, contrast, saturation)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Saturação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Saturação", color = TextSecondary, fontSize = 12.sp)
            Text("${saturation.toInt()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = saturation,
            onValueChange = {
                saturation = it
                viewModel.updateClipAdjustments(brightness, contrast, saturation)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )
    }
}

@Composable
private fun TransformPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    if (clip == null) {
        Text("Selecione um clipe na timeline para transformar.", color = TextSecondary, fontSize = 13.sp)
        return
    }

    var scale by remember(clip.id, clip.scale) { mutableFloatStateOf(clip.scale) }
    var rotation by remember(clip.id, clip.rotation) { mutableFloatStateOf(clip.rotation) }
    var opacity by remember(clip.id, clip.opacity) { mutableFloatStateOf(clip.opacity) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transformações do Clipe",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            TextButton(
                onClick = {
                    scale = 1.0f
                    rotation = 0f
                    opacity = 1.0f
                    viewModel.resetClipTransform(clip.id)
                }
            ) {
                Text("Redefinir", color = DangerRed, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Flip & Rotate Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Flip Horizontal
            OutlinedButton(
                onClick = { viewModel.toggleFlipHorizontal(clip.id) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (clip.flipHorizontal) PrimaryPurpleSoft else Color.Transparent,
                    contentColor = if (clip.flipHorizontal) PrimaryPurpleVariant else TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (clip.flipHorizontal) PrimaryPurple else BorderStrong
                ),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip H", fontSize = 12.sp)
            }

            // Flip Vertical
            OutlinedButton(
                onClick = { viewModel.toggleFlipVertical(clip.id) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (clip.flipVertical) PrimaryPurpleSoft else Color.Transparent,
                    contentColor = if (clip.flipVertical) PrimaryPurpleVariant else TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (clip.flipVertical) PrimaryPurple else BorderStrong
                ),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip V", fontSize = 12.sp)
            }

            // Rotate -90
            OutlinedButton(
                onClick = { viewModel.rotateClip90(clockwise = false, clipId = clip.id) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("-90°", fontSize = 12.sp)
            }

            // Rotate +90
            OutlinedButton(
                onClick = { viewModel.rotateClip90(clockwise = true, clipId = clip.id) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+90°", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scale / Zoom Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Zoom / Escala", color = TextSecondary, fontSize = 12.sp)
            Text("${String.format("%.2f", scale)}x", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = scale,
            onValueChange = {
                scale = it
                viewModel.updateClipTransform(scale = scale, clipId = clip.id)
            },
            valueRange = 0.5f..3.0f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )

        // Rotation Slider (-180..+180)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Rotação Livre", color = TextSecondary, fontSize = 12.sp)
            Text("${rotation.toInt()}°", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = rotation,
            onValueChange = {
                rotation = it
                viewModel.updateClipTransform(rotation = rotation, clipId = clip.id)
            },
            valueRange = -180f..180f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )

        // Opacity Slider (0..1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Opacidade", color = TextSecondary, fontSize = 12.sp)
            Text("${(opacity * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = opacity,
            onValueChange = {
                opacity = it
                viewModel.updateClipTransform(opacity = opacity, clipId = clip.id)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )
    }
}

@Composable
private fun AudioPanel(viewModel: EditorViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.currentProject ?: return

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris)
        }
    }

    val selectedTrack = project.audios.find { it.id == uiState.selectedAudioTrackId }
        ?: project.audios.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Áudio & Trilha Sonora",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Button(
                onClick = { audioPickerLauncher.launch("audio/*") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("import_audio_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Importar Áudio", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tracks Selector Chips (if any exist)
        if (project.audios.isNotEmpty()) {
            Text(
                text = "Faixas no Projeto (${project.audios.size})",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(project.audios) { track ->
                    val isSel = track.id == selectedTrack?.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) PrimaryPurpleSoft else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSel) PrimaryPurple else BorderStrong
                        ),
                        modifier = Modifier.clickable { viewModel.selectAudioTrack(track.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isSel) PrimaryPurpleVariant else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = track.name,
                                color = if (isSel) PrimaryPurpleVariant else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { viewModel.removeAudioTrack(track.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover",
                                    tint = DangerRed,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Track Controls
            if (selectedTrack != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Title & Quick Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedTrack.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                val effectiveDur = com.example.util.TimelineUtils.calculateAudioEffectiveDuration(selectedTrack)
                                Text(
                                    text = "Duração: ${String.format("%.1f", effectiveDur / 1000f)}s • Início: ${String.format("%.1f", selectedTrack.timelineStartMs / 1000f)}s",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.splitAudioTrackAtPlayhead(selectedTrack.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurpleVariant)
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dividir", fontSize = 11.sp)
                                }

                                IconButton(
                                    onClick = { viewModel.removeAudioTrack(selectedTrack.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = DangerRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Waveform Visualizer for Selected Track
                        val trackSamples = uiState.waveforms[selectedTrack.id] ?: emptyList()
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            AudioWaveformBar(
                                amplitudes = trackSamples,
                                isMuted = selectedTrack.isMuted,
                                color = PrimaryPurpleVariant,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Volume Control Slider + Mute Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleAudioTrackMute(selectedTrack.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedTrack.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (selectedTrack.isMuted) DangerRed else PrimaryPurpleVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Volume: ${(selectedTrack.volume * 100).toInt()}%",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(85.dp)
                            )
                            Slider(
                                value = selectedTrack.volume,
                                onValueChange = { viewModel.updateAudioTrackVolume(selectedTrack.id, it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = PrimaryPurple,
                                    activeTrackColor = PrimaryPurple
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Timeline Position Slider
                        val totalDuration = viewModel.getTotalDurationMs().coerceAtLeast(30000L)
                        var localTimelineStart by remember(selectedTrack.id, selectedTrack.timelineStartMs) {
                            mutableFloatStateOf(selectedTrack.timelineStartMs.toFloat())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Posição na Timeline", color = TextSecondary, fontSize = 12.sp)
                            Text("${String.format("%.1f", localTimelineStart / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = localTimelineStart,
                            onValueChange = { localTimelineStart = it },
                            onValueChangeFinished = {
                                viewModel.updateAudioTrackPosition(selectedTrack.id, localTimelineStart.toLong())
                            },
                            valueRange = 0f..totalDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryPurpleVariant,
                                activeTrackColor = PrimaryPurple
                            )
                        )

                        // Trim Start / End Controls
                        val maxAudioDur = selectedTrack.durationMs.coerceAtLeast(1000L).toFloat()
                        var localTrimStart by remember(selectedTrack.id, selectedTrack.trimStartMs) {
                            mutableFloatStateOf(selectedTrack.trimStartMs.toFloat())
                        }
                        var localTrimEnd by remember(selectedTrack.id, selectedTrack.trimEndMs) {
                            mutableFloatStateOf(
                                if (selectedTrack.trimEndMs > selectedTrack.trimStartMs) {
                                    selectedTrack.trimEndMs.toFloat()
                                } else {
                                    maxAudioDur
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Trim Início", color = TextSecondary, fontSize = 12.sp)
                            Text("${String.format("%.1f", localTrimStart / 1000f)}s", color = TextPrimary, fontSize = 12.sp)
                        }
                        Slider(
                            value = localTrimStart,
                            onValueChange = { localTrimStart = it },
                            onValueChangeFinished = {
                                viewModel.updateAudioTrackTrim(selectedTrack.id, localTrimStart.toLong(), localTrimEnd.toLong())
                            },
                            valueRange = 0f..(localTrimEnd - 200f).coerceAtLeast(0f),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryPurpleVariant,
                                activeTrackColor = PrimaryPurple
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Trim Fim", color = TextSecondary, fontSize = 12.sp)
                            Text("${String.format("%.1f", localTrimEnd / 1000f)}s", color = TextPrimary, fontSize = 12.sp)
                        }
                        Slider(
                            value = localTrimEnd,
                            onValueChange = { localTrimEnd = it },
                            onValueChangeFinished = {
                                viewModel.updateAudioTrackTrim(selectedTrack.id, localTrimStart.toLong(), localTrimEnd.toLong())
                            },
                            valueRange = (localTrimStart + 200f).coerceAtMost(maxAudioDur)..maxAudioDur,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryPurpleVariant,
                                activeTrackColor = PrimaryPurple
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Preset Audio Library
        Text(
            text = "Biblioteca de Áudio",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MockData.audioTracks) { track ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.clickable {
                        val newTrack = track.copy(
                            id = "audio_" + java.util.UUID.randomUUID().toString().take(6),
                            timelineStartMs = uiState.currentPositionMs
                        )
                        viewModel.addAudioTrack(newTrack)
                    }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryPurpleVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(track.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text("${track.category} • ${track.duration}", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPanel(
    project: ProjectItem,
    selectedTextId: String?,
    viewModel: EditorViewModel
) {
    val selectedText = project.texts.find { it.id == selectedTextId }

    if (selectedText != null) {
        // Modo de Edição do Texto Selecionado
        var contentText by remember(selectedText.id) { mutableStateOf(selectedText.text) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Editar Texto", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.selectTextOverlay(null) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar seleção", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = { viewModel.removeTextOverlay(selectedText.id) },
                    modifier = Modifier.size(28.dp).testTag("text_delete_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir texto", tint = DangerRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Campo de edição de conteúdo
            OutlinedTextField(
                value = contentText,
                onValueChange = {
                    contentText = it
                    viewModel.updateTextOverlayContent(selectedText.id, it)
                },
                placeholder = { Text("Conteúdo do texto...", color = TextTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = BorderStrong
                ),
                singleLine = false,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("text_content_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tamanho da Fonte
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tamanho da Fonte", color = TextSecondary, fontSize = 12.sp)
                Text("${selectedText.fontSizeSp.toInt()} sp", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedText.fontSizeSp,
                onValueChange = { viewModel.updateTextOverlayStyle(selectedText.id, fontSizeSp = it) },
                valueRange = 12f..56f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple),
                modifier = Modifier.testTag("text_font_size_slider")
            )

            // Cores do Texto
            Text("Cor do Texto", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MockData.textColors) { colorHex ->
                    val color = parseColorSafely(colorHex, Color.White)
                    val isSel = selectedText.colorHex.equals(colorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) PrimaryPurpleVariant else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { viewModel.updateTextOverlayStyle(selectedText.id, colorHex = colorHex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animação de Entrada e Saída
            val animations = listOf("None", "Fade", "Zoom", "Slide Left", "Slide Right", "Slide Up", "Slide Down")
            Text("Animação de Entrada", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(animations) { anim ->
                    val isSel = selectedText.animationIn == anim
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                        modifier = Modifier.clickable {
                            viewModel.updateTextOverlayAnimation(selectedText.id, animationIn = anim)
                        }
                    ) {
                        Text(anim, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Animação de Saída", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(animations) { anim ->
                    val isSel = selectedText.animationOut == anim
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                        modifier = Modifier.clickable {
                            viewModel.updateTextOverlayAnimation(selectedText.id, animationOut = anim)
                        }
                    ) {
                        Text(anim, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            // Modo de animação de texto (Full, Word, Letter)
            Text("Modo de Animação de Texto", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Full" to "Completo", "Word" to "Palavra", "Letter" to "Máquina/Letra").forEach { (mode, label) ->
                    val isSel = selectedText.textAnimationMode.equals(mode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                        modifier = Modifier.clickable {
                            viewModel.updateTextOverlayAnimation(selectedText.id, textAnimationMode = mode)
                        }
                    ) {
                        Text(label, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ajuste Temporal: Início e Duração
            val totalDur = viewModel.getTotalDurationMs().coerceAtLeast(30000L).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Início na Timeline", color = TextSecondary, fontSize = 12.sp)
                Text("${String.format("%.1f", selectedText.startTimeMs / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedText.startTimeMs.toFloat(),
                onValueChange = {
                    viewModel.updateTextOverlayTiming(selectedText.id, it.toLong(), selectedText.durationMs)
                },
                valueRange = 0f..totalDur,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duração", color = TextSecondary, fontSize = 12.sp)
                Text("${String.format("%.1f", selectedText.durationMs / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedText.durationMs.toFloat(),
                onValueChange = {
                    viewModel.updateTextOverlayTiming(selectedText.id, selectedText.startTimeMs, it.toLong())
                },
                valueRange = 500f..15000f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple)
            )
        }
    } else {
        // Modo de Adição Rápida de Texto
        var textInput by remember { mutableStateOf("") }
        var selectedColor by remember { mutableStateOf("#FFFFFF") }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Adicionar Texto ao Vídeo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Digite o texto...", color = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = BorderStrong
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("text_quick_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.addTextOverlay(textInput, selectedColor)
                            textInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier.testTag("text_add_button")
                ) {
                    Text("Adicionar")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Paleta de Cores Rápida
            Text("Cor Padrão", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MockData.textColors) { colorHex ->
                    val color = parseColorSafely(colorHex, Color.White)
                    val isSel = selectedColor.equals(colorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) PrimaryPurpleVariant else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = colorHex }
                    )
                }
            }

            // Atalho para Templates
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ou use um Estilo Pronto:", color = TextSecondary, fontSize = 12.sp)
                TextButton(onClick = { viewModel.setActivePanel(ToolPanel.TEMPLATES) }) {
                    Text("Ver Todos", color = PrimaryPurpleVariant, fontSize = 11.sp)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TextTemplateRepository.templates.take(3)) { tpl ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                        modifier = Modifier.clickable { viewModel.applyTextTemplate(tpl) }
                    ) {
                        Text(
                            text = tpl.name,
                            color = PrimaryPurpleVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesPanel(
    project: ProjectItem,
    viewModel: EditorViewModel
) {
    var selectedCategory by remember { mutableStateOf("Tudo") }
    val categories = listOf("Tudo", "Títulos", "Destaque", "Legendas", "Moderno", "Retrô", "Social")
    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "Tudo") TextTemplateRepository.templates
        else TextTemplateRepository.templates.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Templates de Texto Estilizados", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Estilos pré-configurados com fontes, sombras e animações", color = TextTertiary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Categorias
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                val isSel = cat == selectedCategory
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSel) PrimaryPurple else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(cat, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid/Lista de templates
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredTemplates) { tpl ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderStrong),
                    modifier = Modifier
                        .clickable { viewModel.applyTextTemplate(tpl) }
                        .testTag("template_${tpl.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tpl.bgHex?.let { parseColorSafely(it, Color.Transparent) } ?: Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tpl.sampleText.take(16),
                                color = parseColorSafely(tpl.colorHex, Color.White),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tpl.name,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${tpl.animationIn} • ${tpl.textAnimationMode}",
                            color = TextTertiary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerPanel(
    project: ProjectItem,
    selectedStickerId: String?,
    viewModel: EditorViewModel
) {
    val selectedSticker = project.stickers.find { it.id == selectedStickerId }

    if (selectedSticker != null) {
        // Modo de Edição do Sticker Selecionado
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Editar ${if (selectedSticker.isGif) "GIF" else "Sticker"}: ${selectedSticker.name}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.selectSticker(null) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar seleção", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = { viewModel.removeSticker(selectedSticker.id) },
                    modifier = Modifier.size(28.dp).testTag("sticker_delete_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir sticker", tint = DangerRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Escala
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Escala / Zoom", color = TextSecondary, fontSize = 12.sp)
                Text("${String.format("%.2f", selectedSticker.scale)}x", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedSticker.scale,
                onValueChange = { viewModel.updateStickerScale(selectedSticker.id, it) },
                valueRange = 0.3f..3.0f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple),
                modifier = Modifier.testTag("sticker_scale_slider")
            )

            // Rotação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rotação", color = TextSecondary, fontSize = 12.sp)
                Text("${selectedSticker.rotation.toInt()}°", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedSticker.rotation,
                onValueChange = { viewModel.updateStickerRotation(selectedSticker.id, it) },
                valueRange = -180f..180f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple),
                modifier = Modifier.testTag("sticker_rotation_slider")
            )

            // Animação de Entrada
            val anims = listOf("None", "Fade", "Zoom", "Slide Up", "Slide Down")
            Text("Animação de Entrada", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(anims) { anim ->
                    val isSel = selectedSticker.animationIn == anim
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                        modifier = Modifier.clickable {
                            viewModel.updateStickerAnimation(selectedSticker.id, animationIn = anim)
                        }
                    ) {
                        Text(anim, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ajuste Temporal
            val totalDur = viewModel.getTotalDurationMs().coerceAtLeast(30000L).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Início na Timeline", color = TextSecondary, fontSize = 12.sp)
                Text("${String.format("%.1f", selectedSticker.startTimeMs / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedSticker.startTimeMs.toFloat(),
                onValueChange = {
                    viewModel.updateStickerTiming(selectedSticker.id, it.toLong(), selectedSticker.durationMs)
                },
                valueRange = 0f..totalDur,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duração", color = TextSecondary, fontSize = 12.sp)
                Text("${String.format("%.1f", selectedSticker.durationMs / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = selectedSticker.durationMs.toFloat(),
                onValueChange = {
                    viewModel.updateStickerTiming(selectedSticker.id, selectedSticker.startTimeMs, it.toLong())
                },
                valueRange = 500f..15000f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple)
            )
        }
    } else {
        // Modo de Seleção / Adição de Novo Sticker ou GIF
        var selectedCategory by remember { mutableStateOf("Tudo") }
        val filteredPresets = remember(selectedCategory) {
            if (selectedCategory == "Tudo") StickerPresetsRepository.presets
            else StickerPresetsRepository.presets.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Adicionar Sticker ou GIF Animado", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Toque para inserir no playhead atual", color = TextTertiary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Categorias
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StickerPresetsRepository.categories) { cat ->
                    val isSel = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(cat, color = if (isSel) Color.White else TextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets Grid
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredPresets) { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                        modifier = Modifier
                            .clickable {
                                val item = preset.createStickerItem(
                                    startTimeMs = viewModel.uiState.value.currentPositionMs,
                                    durationMs = 3500L
                                )
                                viewModel.addSticker(item)
                            }
                            .testTag("preset_${preset.id}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = preset.uri,
                                    contentDescription = preset.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                if (preset.isGif) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = PrimaryPurple,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                                    ) {
                                        Text("GIF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(preset.name, color = TextPrimary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    if (clip == null) {
        Text("Selecione um clipe na timeline para definir a transição.", color = TextSecondary, fontSize = 13.sp)
        return
    }

    val transitionTypes = listOf(
        "Nenhuma" to null,
        "Dissolver" to "Dissolver",
        "Fade" to "Fade",
        "Deslizar Esquerda" to "Deslizar Esquerda",
        "Deslizar Direita" to "Deslizar Direita",
        "Deslizar Cima" to "Deslizar Cima",
        "Deslizar Baixo" to "Deslizar Baixo",
        "Zoom" to "Zoom",
        "Cortina" to "Cortina"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transição para o Próximo Clipe", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = clip.transition ?: "Nenhuma",
                color = if (clip.transition != null) PrimaryPurpleVariant else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text("A transição atua entre este clipe e o próximo na timeline", color = TextTertiary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transitionTypes) { (label, name) ->
                val isSel = (clip.transition == null && name == null) ||
                        (clip.transition != null && clip.transition.equals(name, ignoreCase = true))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) PrimaryPurple else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurpleVariant else BorderStrong),
                    modifier = Modifier
                        .clickable { viewModel.updateClipTransition(name, clipId = clip.id) }
                        .testTag("transition_chip_$label")
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.White else TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Duração da transição se uma transição estiver ativa
        if (clip.transition != null && !clip.transition.equals("nenhuma", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duração da Transição", color = TextSecondary, fontSize = 12.sp)
                Text(
                    text = "${String.format("%.1f", clip.transitionDurationMs / 1000f)}s",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = clip.transitionDurationMs.toFloat(),
                onValueChange = { viewModel.updateClipTransitionDuration(it.toLong(), clipId = clip.id) },
                valueRange = 200f..2500f,
                colors = SliderDefaults.colors(thumbColor = PrimaryPurpleVariant, activeTrackColor = PrimaryPurple),
                modifier = Modifier.testTag("transition_duration_slider")
            )
        }
    }
}

@Composable
private fun CanvasPanel(project: ProjectItem, viewModel: EditorViewModel) {
    Column {
        Text("Proporção do Vídeo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.entries.forEach { ratio ->
                val isSel = project.aspectRatio == ratio
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) PrimaryPurple else SurfaceDark,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setAspectRatio(ratio) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(ratio.label, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VFXPanel(project: ProjectItem, viewModel: EditorViewModel) {
    var selectedVfxId by remember { mutableStateOf(project.activeVFX.firstOrNull()?.id) }
    val selectedVfx = project.activeVFX.find { it.id == selectedVfxId } ?: project.activeVFX.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Efeitos Visuais (VFX)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (project.activeVFX.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllVFX() }) {
                    Text("Desativar Todos", color = DangerRed, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MockData.effects) { effect ->
                val activeInstance = project.activeVFX.find { it.id == effect.id }
                val isAct = activeInstance != null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAct) PrimaryPurpleSoft else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAct) PrimaryPurple else BorderStrong),
                    modifier = Modifier.clickable {
                        viewModel.toggleVFX(effect)
                        selectedVfxId = effect.id
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            effect.name,
                            color = if (isAct) PrimaryPurpleVariant else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isAct) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isAct) {
                            Text(
                                text = "${activeInstance?.intensity?.toInt() ?: 50}%",
                                color = PrimaryPurpleVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Intensity slider if any VFX is active
        if (selectedVfx != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intensidade (${selectedVfx.name})", color = TextSecondary, fontSize = 12.sp)
                        Text("${selectedVfx.intensity.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = selectedVfx.intensity,
                        onValueChange = { viewModel.updateVfxIntensity(selectedVfx.id, it) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryPurpleVariant,
                            activeTrackColor = PrimaryPurple
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    if (clip == null) {
        Text("Selecione um clipe na timeline para cortar.", color = TextSecondary, fontSize = 13.sp)
        return
    }

    val maxDuration = (if (clip.originalDurationMs > 0L) clip.originalDurationMs else clip.durationMs.coerceAtLeast(1000L)).toFloat()
    val initialStart = clip.trimStartMs.toFloat().coerceIn(0f, maxDuration - 100f)
    val initialEnd = com.example.util.TimelineUtils.getEffectiveTrimEnd(clip).toFloat().coerceIn(initialStart + 100f, maxDuration)

    var localTrimStart by remember(clip.id, clip.trimStartMs) { mutableStateOf(initialStart) }
    var localTrimEnd by remember(clip.id, clip.trimEndMs) { mutableStateOf(initialEnd) }

    val resultingDurationMs = ((localTrimEnd - localTrimStart) / com.example.util.TimelineUtils.getSafeSpeed(clip)).toLong()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Aparar / Cortar Clipe",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Duração na Timeline: ${String.format("%.2f", resultingDurationMs / 1000f)}s",
                    color = PrimaryPurpleVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = {
                    viewModel.commitTrim(clip.id, 0L, maxDuration.toLong())
                }
            ) {
                Text("Redefinir", color = DangerRed, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trim Start
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Início (Trim Start)", color = TextSecondary, fontSize = 12.sp)
            Text("${String.format("%.2f", localTrimStart / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = localTrimStart,
            onValueChange = { localTrimStart = it },
            onValueChangeFinished = {
                viewModel.commitTrim(clip.id, localTrimStart.toLong(), localTrimEnd.toLong())
            },
            valueRange = 0f..(localTrimEnd - 100f).coerceAtLeast(0f),
            colors = SliderDefaults.colors(
                thumbColor = PrimaryPurpleVariant,
                activeTrackColor = PrimaryPurple
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Trim End
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fim (Trim End)", color = TextSecondary, fontSize = 12.sp)
            Text("${String.format("%.2f", localTrimEnd / 1000f)}s / ${String.format("%.2f", maxDuration / 1000f)}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = localTrimEnd,
            onValueChange = { localTrimEnd = it },
            onValueChangeFinished = {
                viewModel.commitTrim(clip.id, localTrimStart.toLong(), localTrimEnd.toLong())
            },
            valueRange = (localTrimStart + 100f).coerceAtMost(maxDuration)..maxDuration,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryPurpleVariant,
                activeTrackColor = PrimaryPurple
            )
        )
    }
}

@Composable
fun AudioWaveformBar(
    amplitudes: List<Float>,
    isMuted: Boolean = false,
    color: Color = PrimaryPurpleVariant,
    modifier: Modifier = Modifier
) {
    val barColor = if (isMuted) Color.Gray.copy(alpha = 0.45f) else color
    Canvas(modifier = modifier) {
        val count = if (amplitudes.isEmpty()) 40 else amplitudes.size
        val barWidth = (size.width / (count * 1.5f)).coerceIn(2f, 8f)
        val spacing = barWidth * 0.5f
        val totalBarSpace = barWidth + spacing

        for (i in 0 until count) {
            val x = i * totalBarSpace
            if (x + barWidth > size.width) break
            val amp = amplitudes.getOrNull(i) ?: 0.35f
            val barHeight = (size.height * amp * 0.9f).coerceAtLeast(3f)
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
