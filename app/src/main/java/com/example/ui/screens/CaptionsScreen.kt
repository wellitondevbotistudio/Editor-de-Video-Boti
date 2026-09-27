package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MockData
import com.example.model.SubtitleSegmentItem
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun CaptionsScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.currentProject
    var selectedLanguage by remember { mutableStateOf("Português (BR)") }
    var isGenerating by remember { mutableStateOf(false) }

    fun formatMs(ms: Long): String {
        val sec = ms / 1000
        val frac = (ms % 1000) / 100
        return "${sec}.${frac}s"
    }

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
                    text = "Legendas Automáticas com IA",
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
                .padding(horizontal = 20.dp)
        ) {
            // Language Selection
            Text(
                text = "Idioma do Áudio",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MockData.captionLanguages.forEach { lang ->
                    val isSel = lang == selectedLanguage
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) PrimaryPurpleSoft else SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PrimaryPurple else BorderStrong),
                        modifier = Modifier.clickable { selectedLanguage = lang }
                    ) {
                        Text(
                            text = lang,
                            color = if (isSel) PrimaryPurpleVariant else TextPrimary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate Button
            PrimaryGradientButton(
                text = if (isGenerating) "Transcrevendo com IA..." else "Gerar Legendas Automáticas",
                onClick = {
                    isGenerating = true
                    viewModel.generateCaptions(selectedLanguage)
                    isGenerating = false
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.AutoAwesome,
                testTag = "generate_captions_button",
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitles list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trechos Legendados (${project?.subtitles?.size ?: 0})",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (project?.subtitles.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhuma legenda gerada ainda",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Toque acima para transcrever o áudio automaticamente com IA",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(project?.subtitles ?: emptyList(), key = { it.id }) { item ->
                        SubtitleRowCard(
                            item = item,
                            formatMs = ::formatMs,
                            onUpdateText = { newText -> viewModel.updateSubtitle(item.id, newText) },
                            onDelete = { viewModel.deleteSubtitle(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleRowCard(
    item: SubtitleSegmentItem,
    formatMs: (Long) -> String,
    onUpdateText: (String) -> Unit,
    onDelete: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf(item.text) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PrimaryPurpleSoft
                ) {
                    Text(
                        text = "${formatMs(item.startTimeMs)} → ${formatMs(item.endTimeMs)}",
                        color = PrimaryPurpleVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row {
                    IconButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = currentText,
                    onValueChange = {
                        currentText = it
                        onUpdateText(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = BorderStrong
                    ),
                    singleLine = true
                )
            } else {
                Text(
                    text = item.text,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
