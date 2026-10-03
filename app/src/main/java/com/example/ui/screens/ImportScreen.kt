package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun ImportScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Vídeos", "Fotos", "Áudio")

    // Launcher do seletor nativo de fotos e vídeos (PhotoPicker)
    val photoVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris)
        }
    }

    // Launcher de áudios locais via SAF
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris)
        }
    }

    val currentProject = uiState.currentProject
    val currentClips = currentProject?.clips ?: emptyList()
    val currentAudios = currentProject?.audios ?: emptyList()

    // Itens selecionados para adição / gerenciamento
    val selectedItemIds = remember { mutableStateListOf<String>() }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header com 'X' na esquerda, Pills centrais e 'X' na direita (Tela 4 da referência)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("import_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White
                        )
                    }

                    // Seletor de categorias em pills: [ Vídeos | Fotos | Áudio ]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceDark)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEachIndexed { index, label ->
                            val isSelected = selectedCategoryIndex == index
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) PrimaryPurple else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        selectedCategoryIndex = index
                                        if (index == 0) {
                                            photoVideoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        } else if (index == 1) {
                                            photoVideoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } else {
                                            audioPickerLauncher.launch(arrayOf("audio/*"))
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sub-barra: Dropdown "Recente v" à esquerda e "Selecionar" à direita
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            // Alternar pasta / seletor
                            photoVideoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    ) {
                        Text(
                            text = "Recente",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Selecionar tudo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryPurpleVariant,
                        modifier = Modifier.clickable {
                            if (selectedItemIds.size == currentClips.size) {
                                selectedItemIds.clear()
                            } else {
                                selectedItemIds.clear()
                                selectedItemIds.addAll(currentClips.map { it.id })
                            }
                        }
                    )
                }

                // Barra de progresso durante importação
                if (uiState.isImportingMedia) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.importStatusMessage ?: "Processando arquivo...",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${(uiState.importProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryPurpleVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { uiState.importProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = PrimaryPurple,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Botão flutuante na parte inferior: "Adicionar (N)" (Tela 4 da referência)
            Surface(
                color = SurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    val count = if (selectedItemIds.isNotEmpty()) selectedItemIds.size else (currentClips.size + currentAudios.size)
                    Button(
                        onClick = {
                            if (currentClips.isEmpty()) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("done_import_button")
                            .testTag("pick_photos_videos_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(
                            text = if (count > 0) "Adicionar ($count)" else "Abrir Galeria",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            if (currentClips.isEmpty()) {
                // Estado de galeria vazia com atalho pro PhotoPicker
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            photoVideoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = PrimaryPurpleVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Selecione vídeos ou fotos",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque aqui para abrir a galeria do dispositivo",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Grid responsivo de mídias (adapta de 3 colunas em phones a mais em tablets)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(currentClips, key = { it.id }) { clip ->
                        val isSelected = selectedItemIds.contains(clip.id) || selectedItemIds.isEmpty()

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) PrimaryPurple else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (selectedItemIds.contains(clip.id)) {
                                        selectedItemIds.remove(clip.id)
                                    } else {
                                        selectedItemIds.add(clip.id)
                                    }
                                }
                        ) {
                            // Imagem / Vídeo Thumbnail
                            if (clip.thumbnailPath.isNotBlank()) {
                                AsyncImage(
                                    model = clip.thumbnailPath,
                                    contentDescription = clip.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (clip.type == MediaType.VIDEO) Icons.Default.PlayCircle else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = PrimaryPurpleVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Badge de duração no canto inferior direito ("00:12")
                            if (clip.type == MediaType.VIDEO) {
                                val seconds = (clip.durationMs / 1000).toInt()
                                val durationText = String.format("%02d:%02d", seconds / 60, seconds % 60)
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = durationText,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Indicador de seleção circular no canto superior direito
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryPurple else Color.Black.copy(alpha = 0.4f))
                                    .border(1.5.dp, if (isSelected) PrimaryPurpleVariant else Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
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
