package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ExportOptions
import com.example.ui.components.GradientPrimary
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun ExportScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.currentProject

    var selectedResolution by remember { mutableStateOf("1080p") }
    var selectedFps by remember { mutableStateOf(30) }
    var selectedQuality by remember { mutableStateOf("Alta") }
    var removeWatermark by remember { mutableStateOf(uiState.isPremiumUser) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exportar Vídeo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Project Preview Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = project?.thumbUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = project?.title ?: "Projeto",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Duração: ${project?.duration ?: "00:15"} • ${project?.aspectRatio?.label}",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tamanho estimado: ~${if (selectedResolution == "4K") "185" else if (selectedResolution == "1080p") "54" else "26"} MB",
                            color = PrimaryPurpleVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Resolution Options
            Text("Resolução", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("720p", "1080p", "4K").forEach { res ->
                    val isSel = selectedResolution == res
                    val is4k = res == "4K"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) PrimaryPurpleSoft else SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurple else BorderStrong),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (is4k && !uiState.isPremiumUser) {
                                    onNavigateToPremium()
                                } else {
                                    selectedResolution = res
                                }
                            }
                            .testTag("res_$res")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = res,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSel) PrimaryPurpleVariant else TextPrimary
                                )
                                if (is4k) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = GoldPremium,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (res == "4K") "Ultra HD" else if (res == "1080p") "Full HD" else "HD",
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Frame Rate (FPS)
            Text("Taxa de Quadros (FPS)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(24, 30, 60).forEach { fps ->
                    val isSel = selectedFps == fps
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) PrimaryPurpleSoft else SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurple else BorderStrong),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedFps = fps }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$fps FPS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSel) PrimaryPurpleVariant else TextPrimary
                            )
                            Text(
                                text = if (fps == 60) "Super Fluido" else if (fps == 30) "Padrão" else "Cinema",
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Remove Watermark Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remover Marca d'Água", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (!uiState.isPremiumUser) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = GoldPremium) {
                                    Text("PRO", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text("Exportação limpa sem o selo Boti", color = TextTertiary, fontSize = 11.sp)
                    }

                    Switch(
                        checked = removeWatermark,
                        onCheckedChange = {
                            if (!uiState.isPremiumUser) {
                                onNavigateToPremium()
                            } else {
                                removeWatermark = it
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple, checkedTrackColor = PrimaryPurpleSoft)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Export CTA Button
            PrimaryGradientButton(
                text = "Iniciar Exportação",
                onClick = {
                    viewModel.startExport(
                        ExportOptions(
                            resolution = selectedResolution,
                            frameRate = selectedFps,
                            quality = selectedQuality,
                            removeWatermark = removeWatermark
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.FileUpload,
                testTag = "start_export_button"
            )
        }
    }

    // Exporting Progress Dialog
    if (uiState.isExporting) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = SurfaceDark,
            title = {
                Text("Exportando Vídeo", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.exportProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryPurple,
                        trackColor = BorderStrong
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${(uiState.exportProgress * 100).toInt()}% concluído",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.exportProgress < 0.4f) "Renderizando quadros de vídeo..."
                        else if (uiState.exportProgress < 0.8f) "Codificando áudio e efeitos..."
                        else "Finalizando arquivo MP4...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelExport() }) {
                    Text("Cancelar", color = DangerRed)
                }
            }
        )
    }

    // Export Success Dialog
    if (uiState.exportSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.resetExportState() },
            containerColor = SurfaceDark,
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text("Exportação Concluída!", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Seu vídeo foi salvo em alta qualidade:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.lastExportedFile ?: "video.mp4",
                        color = PrimaryPurpleVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetExportState()
                        onNavigateToPlayer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier.testTag("open_in_player_button")
                ) {
                    Text("Ver no Player")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetExportState()
                    onNavigateBack()
                }) {
                    Text("Concluir", color = TextSecondary)
                }
            }
        )
    }
}
