package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun PlayerScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.currentProject
    val totalMs = viewModel.getTotalDurationMs()
    var isLooping by remember { mutableStateOf(true) }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Video Display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.togglePlayback() },
                contentAlignment = Alignment.Center
            ) {
                val ratio = project?.aspectRatio?.ratio ?: (9f / 16f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = project?.thumbUrl,
                        contentDescription = "Player",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Filter tint
                    when (project?.activeFilter) {
                        "P&B" -> Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        "Vívido" -> Box(modifier = Modifier.fillMaxSize().background(PrimaryPurple.copy(alpha = 0.15f)))
                        "Quente" -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFF9800).copy(alpha = 0.15f)))
                        else -> {}
                    }

                    // Watermark indicator if not pro
                    if (!uiState.isPremiumUser) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Boti Editor",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Text(
                    text = project?.title ?: "Reprodução",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                IconButton(
                    onClick = { viewModel.setFeedback("Link do vídeo copiado para compartilhar") },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        tint = Color.White
                    )
                }
            }

            // Bottom Player Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Scrubber Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(uiState.currentPositionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = uiState.currentPositionMs.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..totalMs.toFloat().coerceAtLeast(1000f),
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryPurpleVariant,
                            activeTrackColor = PrimaryPurpleVariant,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Text(
                        text = formatTime(totalMs),
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isLooping = !isLooping }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repetir",
                            tint = if (isLooping) PrimaryPurpleVariant else TextTertiary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.seekTo(uiState.currentPositionMs - 5000L) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay5,
                            contentDescription = "Voltar 5s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Big Play/Pause
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(PrimaryPurple, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.seekTo(uiState.currentPositionMs + 5000L) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward5,
                            contentDescription = "Avançar 5s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setFeedback("Tela cheia alternada") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Tela Cheia",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
