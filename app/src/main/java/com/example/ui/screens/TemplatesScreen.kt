package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoTemplateItem
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

/**
 * Tela de MODELOS - Refinada com layout moderno estilo TikTok / CapCut.
 */
@Composable
fun TemplatesScreen(
    viewModel: EditorViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val categories = listOf("Em alta", "TikTok / Reels", "Vlog", "Música & Beat", "Cinemático", "Memórias")
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val templateList = remember {
        listOf(
            VideoTemplateItem(id = "tpl_vlog", title = "Vlog Diário Minimal", category = "Vlog", clipsCount = 4, duration = "00:15", thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500"),
            VideoTemplateItem(id = "tpl_memories", title = "Memórias de Verão", category = "Em alta", clipsCount = 5, duration = "00:20", thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500"),
            VideoTemplateItem(id = "tpl_bday", title = "Aniversário Especial", category = "Memórias", clipsCount = 6, duration = "00:30", thumbUrl = "https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?w=500"),
            VideoTemplateItem(id = "tpl_travel", title = "Viagem & Aventura 4K", category = "Em alta", clipsCount = 8, duration = "00:25", thumbUrl = "https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=500"),
            VideoTemplateItem(id = "tpl_promo", title = "Beat Drop Sincronizado", category = "Música & Beat", clipsCount = 5, duration = "00:12", thumbUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500"),
            VideoTemplateItem(id = "tpl_love", title = "Transições Neon Glitch", category = "TikTok / Reels", clipsCount = 4, duration = "00:18", thumbUrl = "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500")
        )
    }

    val filteredTemplates = remember(selectedCategoryIndex) {
        if (selectedCategoryIndex == 0) templateList
        else {
            val selectedCat = categories[selectedCategoryIndex]
            templateList.filter { it.category.equals(selectedCat, ignoreCase = true) || it.category == "Em alta" }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header com '<' Voltar e "Modelos em Alta"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onNavigateToEditor,
                            shape = CircleShape,
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Modelos",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Vídeos prontos para editar em 1 clique",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // VIP Badge
                    Surface(
                        onClick = onNavigateToPremium,
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF261D0E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPremium.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldPremium,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO",
                                color = GoldPremiumLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, cat ->
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            onClick = { selectedCategoryIndex = index },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PrimaryPurple else SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryPurpleVariant else BorderSubtle
                            ),
                            modifier = Modifier.testTag("template_cat_$cat")
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            items(filteredTemplates, key = { it.id }) { template ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = PrimaryPurple.copy(alpha = 0.2f))
                        .clickable {
                            viewModel.createFromTemplate(template)
                            onNavigateToEditor()
                        }
                        .testTag("template_card_${template.id}")
                ) {
                    Column {
                        // Thumbnail with aspect ratio 9:16 feel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(SurfaceElevated)
                        ) {
                            AsyncImage(
                                model = template.thumbUrl,
                                contentDescription = template.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Gradient Vignette
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                        )
                                    )
                            )

                            // Category badge top-left
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = template.category,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Duration badge top-right
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = template.duration,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Center Play overlay circle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Views count bottom-start
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "142k",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Bottom card content
                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text(
                                text = template.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // "Usar Modelo" Pill Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PrimaryPurple,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Usar Modelo",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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
