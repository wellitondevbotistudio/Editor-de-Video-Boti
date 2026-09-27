package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MockData
import com.example.model.*
import com.example.ui.components.GradientPrimary
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
    val project = uiState.currentProject

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
    val currentClip = project.clips.find { it.id == uiState.selectedClipId } ?: project.clips.firstOrNull()

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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = project.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${formatTime(uiState.currentPositionMs)} / ${formatTime(totalDurationMs)}",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setFeedback("Ação desfeita") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Desfazer",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setFeedback("Ação refeita") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Refazer",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onNavigateToExport,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    // Current Frame Media
                    AsyncImage(
                        model = currentClip?.uri ?: project.thumbUrl,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Filter tint simulator
                    when (currentClip?.filter ?: project.activeFilter) {
                        "P&B" -> Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        "Vívido" -> Box(modifier = Modifier.fillMaxSize().background(PrimaryPurple.copy(alpha = 0.15f)))
                        "Quente" -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFF9800).copy(alpha = 0.15f)))
                        "Frio" -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2196F3).copy(alpha = 0.15f)))
                        "Cyberpunk" -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE91E63).copy(alpha = 0.2f)))
                        else -> {}
                    }

                    // Text Overlays
                    project.texts.forEach { textItem ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(
                                    y = ((textItem.posY - 0.5f) * 200).dp
                                )
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = textItem.text,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

            // Timeline Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(uiState.currentPositionMs),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Slider(
                    value = uiState.currentPositionMs.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1000f),
                    colors = SliderDefaults.colors(
                        thumbColor = TimelinePlayhead,
                        activeTrackColor = TimelinePlayhead,
                        inactiveTrackColor = TimelineTrackBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("timeline_slider")
                )

                Text(
                    text = formatTime(totalDurationMs),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            // Multi-Track Visual Timeline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundElevated)
                    .padding(vertical = 8.dp)
            ) {
                // Media Track (Clips)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(project.clips) { index, clip ->
                        val isSelected = clip.id == uiState.selectedClipId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            modifier = Modifier
                                .width((clip.durationMs / 400).coerceIn(80L, 180L).toInt().dp)
                                .fillMaxHeight()
                                .clickable { viewModel.selectClip(clip.id) }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) PrimaryPurpleVariant else BorderStrong,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Box {
                                AsyncImage(
                                    model = clip.uri,
                                    contentDescription = clip.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                                Text(
                                    text = clip.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                )
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

                    // Add Clip Button
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            modifier = Modifier
                                .size(58.dp)
                                .clickable(onClick = onNavigateToImport)
                                .border(1.dp, BorderStrong, RoundedCornerShape(8.dp))
                                .testTag("timeline_add_clip")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Adicionar", tint = TextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Audio Track Strip
                if (project.audios.isNotEmpty()) {
                    val audio = project.audios.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(26.dp)
                            .background(SurfaceElevated, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryPurpleVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(audio.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${(audio.volume * 100).toInt()}%", color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }

            // Bottom Tool Dock
            Surface(
                color = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ToolButton(icon = Icons.Default.ContentCut, label = "Dividir", onClick = { viewModel.splitClipAtPlayhead() })
                    ToolButton(icon = Icons.Default.Crop, label = "Cortar", isSelected = uiState.activePanel == ToolPanel.TRIM, onClick = { viewModel.setActivePanel(ToolPanel.TRIM) })
                    ToolButton(icon = Icons.Default.Speed, label = "Velocidade", isSelected = uiState.activePanel == ToolPanel.SPEED, onClick = { viewModel.setActivePanel(ToolPanel.SPEED) })
                    ToolButton(icon = Icons.Default.Filter, label = "Filtros", isSelected = uiState.activePanel == ToolPanel.FILTER, onClick = { viewModel.setActivePanel(ToolPanel.FILTER) })
                    ToolButton(icon = Icons.Default.Tune, label = "Ajustes", isSelected = uiState.activePanel == ToolPanel.ADJUST, onClick = { viewModel.setActivePanel(ToolPanel.ADJUST) })
                    ToolButton(icon = Icons.Default.MusicNote, label = "Áudio", isSelected = uiState.activePanel == ToolPanel.AUDIO, onClick = { viewModel.setActivePanel(ToolPanel.AUDIO) })
                    ToolButton(icon = Icons.Default.TextFields, label = "Texto", isSelected = uiState.activePanel == ToolPanel.TEXT, onClick = { viewModel.setActivePanel(ToolPanel.TEXT) })
                    ToolButton(icon = Icons.Default.Subtitles, label = "Legendas", onClick = onNavigateToCaptions)
                    ToolButton(icon = Icons.Default.AutoAwesome, label = "Efeitos", isSelected = uiState.activePanel == ToolPanel.VFX, onClick = { viewModel.setActivePanel(ToolPanel.VFX) })
                    ToolButton(icon = Icons.Default.Shuffle, label = "Transição", isSelected = uiState.activePanel == ToolPanel.TRANSITION, onClick = { viewModel.setActivePanel(ToolPanel.TRANSITION) })
                    ToolButton(icon = Icons.Default.AspectRatio, label = "Formato", isSelected = uiState.activePanel == ToolPanel.CANVAS, onClick = { viewModel.setActivePanel(ToolPanel.CANVAS) })
                    ToolButton(icon = Icons.Default.Delete, label = "Excluir", tint = DangerRed, onClick = { viewModel.deleteSelectedClip() })
                }
            }

            // Tool Adjustment Sheet
            if (uiState.activePanel != ToolPanel.NONE) {
                Surface(
                    color = SurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        when (uiState.activePanel) {
                            ToolPanel.SPEED -> SpeedPanel(currentClip, viewModel)
                            ToolPanel.FILTER -> FilterPanel(currentClip, viewModel)
                            ToolPanel.ADJUST -> AdjustPanel(currentClip, viewModel)
                            ToolPanel.AUDIO -> AudioPanel(viewModel)
                            ToolPanel.TEXT -> TextPanel(viewModel)
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
            .padding(horizontal = 4.dp)
            .testTag("tool_$label")
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) PrimaryPurple else SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isSelected) PrimaryPurpleVariant else TextSecondary
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
    Column {
        Text("Filtros de Cor", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    modifier = Modifier
                        .clickable { viewModel.updateClipFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSel) PrimaryPurpleVariant else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    var brightness by remember(clip) { mutableStateOf(clip?.brightness ?: 0f) }
    var contrast by remember(clip) { mutableStateOf(clip?.contrast ?: 0f) }
    var saturation by remember(clip) { mutableStateOf(clip?.saturation ?: 0f) }

    Column {
        Text("Ajustes de Imagem", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Brilho: ${brightness.toInt()}", color = TextSecondary, fontSize = 12.sp)
        Slider(
            value = brightness,
            onValueChange = {
                brightness = it
                viewModel.updateClipAdjustments(brightness, contrast, saturation)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )
        Text("Contraste: ${contrast.toInt()}", color = TextSecondary, fontSize = 12.sp)
        Slider(
            value = contrast,
            onValueChange = {
                contrast = it
                viewModel.updateClipAdjustments(brightness, contrast, saturation)
            },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
        )
    }
}

@Composable
private fun AudioPanel(viewModel: EditorViewModel) {
    Column {
        Text("Trilha Sonora", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MockData.audioTracks) { track ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.clickable { viewModel.addAudioTrack(track) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(track.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${track.category} • ${track.duration}", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPanel(viewModel: EditorViewModel) {
    var textInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FFFFFF") }

    Column {
        Text("Adicionar Texto", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.addTextOverlay(textInput, selectedColor)
                        textInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Inserir")
            }
        }
    }
}

@Composable
private fun TransitionPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    Column {
        Text("Transição para o Próximo Clipe", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (clip?.transition == null) PrimaryPurple else SurfaceDark,
                    modifier = Modifier.clickable { viewModel.updateClipTransition(null) }
                ) {
                    Text("Nenhuma", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                }
            }
            items(MockData.transitions) { tr ->
                val isSel = clip?.transition == tr.name
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) PrimaryPurple else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.clickable { viewModel.updateClipTransition(tr.name) }
                ) {
                    Text(tr.name, color = if (isSel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                }
            }
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
    Column {
        Text("Efeitos Visuais (VFX)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MockData.effects) { effect ->
                val isAct = project.activeVFX.any { it.id == effect.id }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAct) PrimaryPurpleSoft else SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAct) PrimaryPurple else BorderStrong),
                    modifier = Modifier.clickable { viewModel.toggleVFX(effect) }
                ) {
                    Text(
                        effect.name,
                        color = if (isAct) PrimaryPurpleVariant else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isAct) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimPanel(clip: MediaClip?, viewModel: EditorViewModel) {
    val durationSec = ((clip?.durationMs ?: 5000L) / 1000).toInt()
    Column {
        Text("Cortar Clipe (Duração atual: ${durationSec}s)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Ajuste as bordas do clipe na linha do tempo para refinar os pontos de entrada e saída.", color = TextSecondary, fontSize = 12.sp)
    }
}
