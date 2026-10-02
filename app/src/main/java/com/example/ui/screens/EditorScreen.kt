package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.view.ViewGroup
import coil.compose.AsyncImage
import java.io.File
import com.example.data.MockData
import com.example.effect.*
import com.example.model.*
import com.example.overlay.StickerLayer
import com.example.overlay.StickerPresetsRepository
import com.example.overlay.TextOverlayLayer
import com.example.overlay.parseColorSafely
import com.example.player.PlaybackState
import com.example.template.TextTemplate
import com.example.template.TextTemplateRepository
import com.example.transition.TransitionAwareMediaSurface
import com.example.transition.TransitionType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.EditorUiState
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
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val photoVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris)
        }
    }

    // Hardware/gesture back handling
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    BackHandler(enabled = !isFullscreen && uiState.activePanel != ToolPanel.NONE) {
        viewModel.setActivePanel(ToolPanel.NONE)
    }

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
            if (!isFullscreen) {
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
        }
    ) { innerPadding ->
        if (isFullscreen) {
            // MODO TELA CHEIA: Ocupa 100% da tela disponível, controles essenciais em overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Vídeo em tela cheia mantendo aspect ratio sem cortes nem distorções
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val frameRatio = project.aspectRatio.ratio
                    val containerRatio = maxWidth / maxHeight
                    val videoModifier = if (containerRatio > frameRatio) {
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(frameRatio)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(frameRatio)
                    }

                    VideoPreviewContent(
                        project = project,
                        uiState = uiState,
                        playbackState = playbackState,
                        currentClip = currentClip,
                        viewModel = viewModel,
                        modifier = videoModifier.background(Color.Black)
                    )
                }

                // Barra superior flutuante na tela cheia: Sair da Tela Cheia & Título
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { isFullscreen = false },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("exit_fullscreen_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Sair da Tela Cheia",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sair da Tela Cheia",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = project.title,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Barra inferior flutuante na tela cheia: Play/Pause, Scrubber e Tempo
                val displayPosition = if (isScrubbing) scrubPositionMs.toLong() else uiState.currentPositionMs
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Slider(
                            value = displayPosition.toFloat(),
                            onValueChange = {
                                isScrubbing = true
                                scrubPositionMs = it
                            },
                            onValueChangeFinished = {
                                viewModel.seekTo(scrubPositionMs.toLong())
                                isScrubbing = false
                            },
                            valueRange = 0f..maxOf(1L, totalDurationMs).toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = PrimaryPurple,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${formatTime(displayPosition)} / ${formatTime(totalDurationMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // MODO DE EDIÇÃO: 55% Prévia do Vídeo + 45% Controles e Ferramentas
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. ÁREA DE PRÉVIA: Ocupa aproximadamente 55% da área disponível
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Frame de vídeo com restrição de aspect ratio responsiva (UIAspectRatioConstraint)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val frameRatio = project.aspectRatio.ratio
                        val containerRatio = maxWidth / maxHeight
                        val videoModifier = if (containerRatio > frameRatio) {
                            Modifier
                                .fillMaxHeight(0.96f)
                                .aspectRatio(frameRatio)
                        } else {
                            Modifier
                                .fillMaxWidth(0.96f)
                                .aspectRatio(frameRatio)
                        }

                        VideoPreviewContent(
                            project = project,
                            uiState = uiState,
                            playbackState = playbackState,
                            currentClip = currentClip,
                            viewModel = viewModel,
                            modifier = videoModifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111111))
                        )
                    }
                }

                // 2. ÁREA DE CONTROLES E FERRAMENTAS: Ocupa o restante (~45% da área)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .background(BackgroundDark)
                ) {
                    // Barra de Controles de Reprodução
                    val displayPosition = if (isScrubbing) scrubPositionMs.toLong() else uiState.currentPositionMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundDark)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Indicador de Tempo
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

                        // Botão Play / Pause Central
                        Surface(
                            onClick = { viewModel.togglePlayback() },
                            shape = CircleShape,
                            color = if (uiState.isPlaying) SurfaceElevated else PrimaryPurple,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (uiState.isPlaying) BorderSubtle else PrimaryPurpleLight
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(if (uiState.isPlaying) 0.dp else 8.dp, CircleShape, spotColor = PrimaryPurple)
                                .testTag("btn_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Ações da Direita: Dividir, Desfazer, Refazer, Tela Cheia
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
                                onClick = { isFullscreen = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("fullscreen_button")
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

                    // Linha do tempo multi-camadas CapCut adaptável
                    CapCutMultiTrackTimeline(
                        project = project,
                        currentPositionMs = uiState.currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        isPlaying = uiState.isPlaying,
                        selectedClipId = uiState.selectedClipId,
                        selectedAudioId = uiState.selectedAudioTrackId,
                        selectedTextId = uiState.selectedTextId,
                        selectedStickerId = uiState.selectedStickerId,
                        waveforms = uiState.waveforms,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        onSeek = { viewModel.seekTo(it) },
                        onSelectClip = { clipId ->
                            viewModel.selectClip(clipId, seekToClipStart = false)
                            viewModel.setActivePanel(ToolPanel.EDIT_TOOLS)
                        },
                        onSelectAudio = { audioId ->
                            viewModel.selectAudioTrack(audioId)
                            viewModel.setActivePanel(ToolPanel.AUDIO)
                        },
                        onSelectText = { textId ->
                            viewModel.selectTextOverlay(textId)
                            viewModel.setActivePanel(ToolPanel.TEXT)
                        },
                        onSelectSticker = { stickerId ->
                            viewModel.selectSticker(stickerId)
                            viewModel.setActivePanel(ToolPanel.STICKER)
                        },
                        onSplitClip = { viewModel.splitClipAtPlayhead() },
                        onDeleteSelected = {
                            if (uiState.selectedClipId != null) {
                                viewModel.deleteSelectedClip()
                            } else if (uiState.selectedAudioTrackId != null) {
                                viewModel.removeAudioTrack(uiState.selectedAudioTrackId!!)
                            } else if (uiState.selectedTextId != null) {
                                viewModel.removeTextOverlay(uiState.selectedTextId!!)
                            } else if (uiState.selectedStickerId != null) {
                                viewModel.removeSticker(uiState.selectedStickerId!!)
                            }
                        },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onAddMedia = onNavigateToImport,
                        onAddAudio = { viewModel.setActivePanel(ToolPanel.AUDIO) },
                        onAddText = { viewModel.setActivePanel(ToolPanel.TEXT) },
                        onAddEffect = { viewModel.setActivePanel(ToolPanel.VFX) },
                        onOpenTransition = { fromId, _ ->
                            viewModel.selectClip(fromId)
                            viewModel.setActivePanel(ToolPanel.TRANSITION)
                        },
                        onMoveClipLeft = { uiState.selectedClipId?.let { viewModel.moveClipLeft(it) } },
                        onMoveClipRight = { uiState.selectedClipId?.let { viewModel.moveClipRight(it) } },
                        onReorderClip = { from, to -> viewModel.reorderClip(from, to) },
                        onDuplicateSelected = { viewModel.duplicateClip() },
                        onMoveText = { textId, newStart ->
                            val txt = project.texts.find { it.id == textId }
                            if (txt != null) {
                                viewModel.updateTextOverlayTiming(textId, newStart, txt.durationMs)
                            }
                        },
                        onMoveSticker = { stkId, newStart ->
                            val stk = project.stickers.find { it.id == stkId }
                            if (stk != null) {
                                viewModel.updateStickerTiming(stkId, newStart, stk.durationMs)
                            }
                        },
                        onMoveAudio = { audioId, newStart ->
                            viewModel.updateAudioTrackPosition(audioId, newStart)
                        },
                        onAddOverlay = { viewModel.setActivePanel(ToolPanel.STICKER) },
                        onClearSelection = { viewModel.clearAllSelections() },
                        modifier = Modifier.weight(1f)
                    )

                    // Dock de Ferramentas Principal (com aba Arquivos dedicada)
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolButton(icon = Icons.Default.FolderOpen, label = "Arquivos", onClick = { viewModel.setActivePanel(ToolPanel.FILES) })
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

                    // Sub-painel: EDITAR CLIPE
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
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                // Header: '<' Voltar | "Editar Clipe" | 'v' Confirmar
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
                                        modifier = Modifier.size(32.dp).testTag("panel_back_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        text = "Editar Clipe",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.setActivePanel(ToolPanel.NONE)
                                            viewModel.selectClip(null)
                                        },
                                        modifier = Modifier.size(32.dp).testTag("panel_confirm_button")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Confirmar", tint = PrimaryPurpleLight, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

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
                                                .size(width = 80.dp, height = 66.dp)
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
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                // Header unificado com botão de fechar/voltar em todas as ferramentas
                                val panelTitle = when (uiState.activePanel) {
                                    ToolPanel.SPEED -> "Velocidade"
                                    ToolPanel.FILTER -> "Filtros de Cor"
                                    ToolPanel.ADJUST -> "Ajustes de Imagem"
                                    ToolPanel.TRANSFORM -> "Transformação"
                                    ToolPanel.AUDIO -> "Áudio"
                                    ToolPanel.TEXT -> "Texto & Títulos"
                                    ToolPanel.TEMPLATES -> "Modelos & Estilos"
                                    ToolPanel.STICKER -> "Sobreposição & Stickers"
                                    ToolPanel.TRANSITION -> "Transições"
                                    ToolPanel.CANVAS -> "Formato & Proporção"
                                    ToolPanel.VFX -> "Efeitos Visuais"
                                    ToolPanel.TRIM -> "Cortar & Ajustar"
                                    ToolPanel.FILES -> "Arquivos de Mídia"
                                    ToolPanel.CAPTIONS -> "Legendas Automáticas"
                                    else -> "Ferramenta"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { viewModel.setActivePanel(ToolPanel.NONE) },
                                        modifier = Modifier.size(32.dp).testTag("panel_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Voltar",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Text(
                                        text = panelTitle,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    IconButton(
                                        onClick = { viewModel.setActivePanel(ToolPanel.NONE) },
                                        modifier = Modifier.size(32.dp).testTag("panel_confirm_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Concluir",
                                            tint = PrimaryPurpleLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

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
                                    ToolPanel.FILES -> FilesPanel(project, viewModel, onLaunchPicker = {
                                        photoVideoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    })
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewContent(
    project: ProjectItem,
    uiState: EditorUiState,
    playbackState: PlaybackState,
    currentClip: MediaClip?,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
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

        val isAnyOverlaySelected = uiState.selectedTextId != null || uiState.selectedStickerId != null
        val isClipSelected = currentClip != null && uiState.selectedClipId == currentClip.id

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
            onScaleSticker = { id, scale -> viewModel.updateStickerScale(id, scale) },
            onRotateSticker = { id, rot -> viewModel.updateStickerRotation(id, rot) },
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
            onScaleText = { id, scale -> viewModel.updateTextOverlayScale(id, scale) },
            onRotateText = { id, rot -> viewModel.updateTextOverlayRotation(id, rot) },
            onDeleteText = { viewModel.removeTextOverlay(it) },
            modifier = Modifier.fillMaxSize()
        )

        // Direct Preview Manipulation Overlay for Selected Video/Photo Clip
        if (isClipSelected && currentClip != null) {
            ClipDirectManipulationOverlay(
                clip = currentClip,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isAnyOverlaySelected && currentClip != null) {
            // Tap on video preview surface selects the active clip
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.selectClip(currentClip.id)
                    }
            )
        }

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

        // Play / Pause Overlay Button (only shown when no element is selected for manipulation)
        if (!isClipSelected && !isAnyOverlaySelected) {
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
}

@Composable
private fun ClipDirectManipulationOverlay(
    clip: MediaClip,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = clip.scale * (if (clip.flipHorizontal) -1f else 1f)
                scaleY = clip.scale * (if (clip.flipVertical) -1f else 1f)
                rotationZ = clip.rotation
                translationX = clip.positionX
                translationY = clip.positionY
            }
            .pointerInput(clip.id) {
                detectTransformGestures { _, pan, zoom, rotationChange ->
                    var newScale = clip.scale
                    if (zoom != 1.0f) {
                        newScale = (clip.scale * zoom).coerceIn(0.2f, 4.0f)
                    }
                    val newRotation = if (rotationChange != 0f) {
                        (clip.rotation + rotationChange) % 360f
                    } else clip.rotation

                    val newPosX = clip.positionX + pan.x
                    val newPosY = clip.positionY + pan.y

                    viewModel.updateClipTransform(
                        scale = newScale,
                        rotation = newRotation,
                        positionX = newPosX,
                        positionY = newPosY,
                        clipId = clip.id
                    )
                }
            }
            .pointerInput(clip.id, isDragging) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = clip.positionX + dragAmount.x
                        val newY = clip.positionY + dragAmount.y
                        viewModel.updateClipTransform(
                            positionX = newX,
                            positionY = newY,
                            clipId = clip.id
                        )
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
            .border(
                width = 2.dp,
                color = if (isDragging) Color(0xFFFBBF24) else Color(0xFFFBBF24).copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp)
            )
            .testTag("clip_manipulation_overlay_${clip.id}")
    ) {
        // Alça Superior Esquerda: Girar 45°
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFBBF24)),
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.TopStart)
                .offset(x = (-10).dp, y = (-10).dp)
                .clickable {
                    val nextRot = (clip.rotation + 45f) % 360f
                    viewModel.updateClipTransform(rotation = nextRot, clipId = clip.id)
                }
                .testTag("clip_rotate_handle_${clip.id}")
        ) {
            Icon(
                imageVector = Icons.Default.RotateRight,
                contentDescription = "Girar Clipe",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.padding(6.dp)
            )
        }

        // Alça Superior Direita: Fechar/Deselecionar
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF87171)),
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-10).dp)
                .clickable {
                    viewModel.selectClip(null)
                }
                .testTag("clip_close_handle_${clip.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Deselecionar Clipe",
                tint = Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }

        // Alça Inferior Direita: Redimensionar / Escalar com Arraste
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFBBF24)),
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
                .pointerInput(clip.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = (dragAmount.x + dragAmount.y) / 250f
                        val newScale = (clip.scale + delta).coerceIn(0.2f, 4.0f)
                        viewModel.updateClipTransform(scale = newScale, clipId = clip.id)
                    }
                }
                .testTag("clip_scale_handle_${clip.id}")
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Redimensionar Clipe",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.padding(6.dp)
            )
        }

        // Alça Inferior Esquerda: Redefinir Posição / Transformação
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF94A3B8)),
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 10.dp)
                .clickable {
                    viewModel.updateClipTransform(
                        scale = 1.0f,
                        rotation = 0f,
                        positionX = 0f,
                        positionY = 0f,
                        clipId = clip.id
                    )
                }
                .testTag("clip_reset_transform_${clip.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Redefinir Transformação",
                tint = Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }

        // Indicador central durante arraste ou se transformado
        if (isDragging || clip.scale != 1.0f || clip.rotation != 0f || clip.positionX != 0f || clip.positionY != 0f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 8.dp)
            ) {
                Text(
                    text = "${(clip.scale * 100).toInt()}% • ${clip.rotation.toInt()}°",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FilesPanel(
    project: ProjectItem,
    viewModel: EditorViewModel,
    onLaunchPicker: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("Todos", "Vídeos", "Fotos", "Áudio")

    val filteredClips = remember(project.clips, selectedCategory) {
        when (selectedCategory) {
            1 -> project.clips.filter { it.type != MediaType.PHOTO }
            2 -> project.clips.filter { it.type == MediaType.PHOTO }
            else -> project.clips
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Top row: Category chips & Import action button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEachIndexed { index, cat ->
                    val isSelected = selectedCategory == index
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryPurple else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) PrimaryPurpleLight else BorderSubtle
                        ),
                        modifier = Modifier.clickable { selectedCategory = index }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onLaunchPicker,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("files_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Adicionar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedCategory == 3) {
            // Audios list
            if (project.audios.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchPicker() }
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = LayerAudioWave, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum arquivo de áudio importado", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toque em 'Adicionar' para importar músicas ou gravações", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(project.audios, key = { it.id }) { audio ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .width(140.dp)
                                .clickable {
                                    viewModel.selectAudioTrack(audio.id)
                                    viewModel.setActivePanel(ToolPanel.AUDIO)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LayerAudioWave.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text("ÁUDIO", color = LayerAudioWave, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(audio.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${audio.durationMs / 1000}s", color = TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Video / Image Media Grid with proper aspect ratios & badges
            if (filteredClips.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchPicker() }
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PermMedia, contentDescription = null, tint = PrimaryPurpleVariant, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum arquivo nessa categoria", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toque em 'Adicionar' para importar vídeos ou fotos da galeria", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredClips, key = { it.id }) { clip ->
                        val isPhoto = clip.type == MediaType.PHOTO
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .width(120.dp)
                                .clickable {
                                    viewModel.selectClip(clip.id)
                                    viewModel.setFeedback("Clipe selecionado na timeline")
                                }
                        ) {
                            Column {
                                // Thumbnail with AspectRatio 16:9 constraint (UIAspectRatioConstraint)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(Color(0xFF1A1A24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumbModel = when {
                                        clip.thumbnailPath.isNotBlank() -> File(clip.thumbnailPath)
                                        clip.localPath.isNotBlank() -> File(clip.localPath)
                                        else -> clip.uri
                                    }
                                    AsyncImage(
                                        model = thumbModel,
                                        contentDescription = clip.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Type badge (Video vs Photo)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPhoto) SuccessGreen.copy(alpha = 0.85f) else PrimaryPurple.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isPhoto) Icons.Default.Image else Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = if (isPhoto) "FOTO" else "${clip.durationMs / 1000}s",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Title
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Text(
                                        text = clip.title,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
    var selectedVfxId by remember { mutableStateOf(project.activeVFX.firstOrNull()?.id ?: "e_halo_blur") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedVfx = project.activeVFX.find { it.id == selectedVfxId } ?: project.activeVFX.firstOrNull()

    val filteredEffects = remember(searchQuery) {
        if (searchQuery.isBlank()) MockData.effects
        else MockData.effects.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // CapCut Category Tabs: "Efeitos de vídeo" | "Efeitos de corpo"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { selectedTab = 0 },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedTab == 0) Color(0xFF1E293B) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedTab == 0) Color(0xFF38BDF8) else BorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▶ Efeitos de vídeo",
                            color = if (selectedTab == 0) Color(0xFF38BDF8) else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    onClick = { selectedTab = 1 },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedTab == 1) Color(0xFF1E293B) else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedTab == 1) Color(0xFF38BDF8) else BorderSubtle
                    )
                ) {
                    Text(
                        text = "Efeitos de corpo",
                        color = if (selectedTab == 1) Color(0xFF38BDF8) else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (project.activeVFX.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllVFX() }) {
                    Text("Limpar", color = DangerRed, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search bar (CapCut: "Search for effects")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar efeitos...", color = TextTertiary, fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161622),
                unfocusedContainerColor = Color(0xFF161622),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Subtitle
        Text(
            text = "Básico",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Effect Cards Carousel / Grid (CapCut style)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredEffects, key = { it.id }) { effect ->
                val isActiveOnTimeline = project.activeVFX.any { it.id == effect.id }
                val isSelectedCard = selectedVfxId == effect.id

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF161622),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelectedCard) 2.dp else 1.dp,
                        // Prominent Red Highlight Frame matching CapCut user screenshot!
                        color = if (isSelectedCard) Color(0xFFEF4444) else BorderSubtle
                    ),
                    modifier = Modifier
                        .width(88.dp)
                        .clickable {
                            selectedVfxId = effect.id
                            viewModel.toggleVFX(effect)
                        }
                        .testTag("vfx_card_${effect.id}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222230))
                        ) {
                            AsyncImage(
                                model = effect.thumbUrl,
                                contentDescription = effect.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Overlay icon
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isActiveOnTimeline) Icons.Default.Check else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (isActiveOnTimeline) Color(0xFF22C55E) else Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = effect.name,
                            color = if (isSelectedCard) Color.White else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelectedCard) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Intensity slider if active
        if (selectedVfx != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF161622),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intensidade (${selectedVfx.name})", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${selectedVfx.intensity.toInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = selectedVfx.intensity,
                        onValueChange = { viewModel.updateVfxIntensity(selectedVfx.id, it) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7)
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

