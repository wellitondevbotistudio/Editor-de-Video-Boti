package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.MediaLibraryEntity
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel
import java.io.File

@Composable
fun ImportScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val mediaLibrary by viewModel.mediaLibrary.collectAsState()

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Vídeos", "Fotos", "Áudio")

    // Controle persistente de permissões na primeira utilização
    val prefs = remember { context.getSharedPreferences("boti_prefs", Context.MODE_PRIVATE) }
    var hasRequestedPermissions by remember {
        mutableStateOf(prefs.getBoolean("has_requested_media_permission", false))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        prefs.edit().putBoolean("has_requested_media_permission", true).apply()
        hasRequestedPermissions = true
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedPermissions) {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Launcher do seletor nativo de fotos e vídeos (PhotoPicker com persistência de URI)
    val photoVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.importMediaUris(uris)
        }
    }

    // Launcher de áudios locais via SAF
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.importMediaUris(uris)
        }
    }

    val currentProject = uiState.currentProject
    val currentClips = currentProject?.clips ?: emptyList()
    val currentAudios = currentProject?.audios ?: emptyList()

    // Filtra a biblioteca global conforme a aba ativa
    val filteredLibraryMedia = remember(mediaLibrary, selectedCategoryIndex) {
        when (selectedCategoryIndex) {
            0 -> mediaLibrary.filter { it.mediaType == "VIDEO" }
            1 -> mediaLibrary.filter { it.mediaType == "PHOTO" }
            2 -> mediaLibrary.filter { it.mediaType == "AUDIO" }
            else -> mediaLibrary
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header com 'X' na esquerda, Pills centrais e botão importar à direita
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
                                    .clickable { selectedCategoryIndex = index }
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

                    // Botão rápido para abrir o seletor do sistema
                    IconButton(
                        onClick = {
                            if (selectedCategoryIndex == 0) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else if (selectedCategoryIndex == 1) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            }
                        },
                        modifier = Modifier.testTag("pick_photos_videos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Importar do dispositivo",
                            tint = PrimaryPurpleVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-barra: Título da biblioteca e ação de novo arquivo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Biblioteca de Mídias (${filteredLibraryMedia.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Surface(
                        onClick = {
                            if (selectedCategoryIndex == 0) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else if (selectedCategoryIndex == 1) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryPurpleLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Novo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
                    val count = currentClips.size + currentAudios.size
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("done_import_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(
                            text = if (count > 0) "Concluir ($count itens)" else "Voltar ao Editor",
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
            if (filteredLibraryMedia.isEmpty()) {
                // Estado vazio para categoria com ação direta para abrir seletor
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (selectedCategoryIndex == 0) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else if (selectedCategoryIndex == 1) {
                                photoVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            }
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
                                    imageVector = if (selectedCategoryIndex == 2) Icons.Default.Audiotrack else Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = PrimaryPurpleVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhuma mídia em ${categories[selectedCategoryIndex]}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque aqui para importar arquivos do dispositivo",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else if (selectedCategoryIndex == 2) {
                // Lista de Áudios da biblioteca
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredLibraryMedia, key = { it.id }) { media ->
                        val isFileExisting = media.localPath.isBlank() || File(media.localPath).exists()
                        val isAlreadyInProject = currentAudios.any { it.localPath == media.localPath || it.uri == media.uri }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAlreadyInProject) SurfaceDark else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAlreadyInProject) PrimaryPurple else BorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isFileExisting) {
                                        viewModel.addMediaFromLibrary(media)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentTeal.copy(alpha = 0.2f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = media.displayName,
                                            color = if (isFileExisting) TextPrimary else TextTertiary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val totalSec = media.durationMs / 1000
                                        Text(
                                            text = String.format("%02d:%02d", totalSec / 60, totalSec % 60),
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (!isFileExisting) {
                                    Text("Indisponível", color = DangerRed, fontSize = 11.sp)
                                } else if (isAlreadyInProject) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = PrimaryPurple.copy(alpha = 0.25f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurple)
                                    ) {
                                        Text("Adicionado", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.addMediaFromLibrary(media) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("+ Adicionar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Grid de Vídeos e Fotos da biblioteca
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredLibraryMedia, key = { it.id }) { media ->
                        val isFileExisting = media.localPath.isBlank() || File(media.localPath).exists()
                        val isAlreadyInProject = currentClips.any { it.localPath == media.localPath || it.uri == media.uri }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(
                                    width = if (isAlreadyInProject) 2.dp else 1.dp,
                                    color = if (isAlreadyInProject) PrimaryPurple else BorderSubtle,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isFileExisting) {
                                        viewModel.addMediaFromLibrary(media)
                                    }
                                }
                                .testTag("library_media_${media.id}")
                        ) {
                            val thumbModel = when {
                                media.thumbnailPath?.isNotBlank() == true -> File(media.thumbnailPath)
                                media.localPath.isNotBlank() -> File(media.localPath)
                                else -> media.uri
                            }

                            if (isFileExisting) {
                                AsyncImage(
                                    model = thumbModel,
                                    contentDescription = media.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                                        Text("Arquivo apagado", color = Color.White, fontSize = 8.sp)
                                    }
                                }
                            }

                            // Badge de duração para vídeos
                            if (media.mediaType == "VIDEO" && media.durationMs > 0) {
                                val seconds = (media.durationMs / 1000).toInt()
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

                            // Badge de "Adicionado" se já está no projeto
                            if (isAlreadyInProject) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryPurple),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Adicionado",
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
