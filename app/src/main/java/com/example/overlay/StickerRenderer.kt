package com.example.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.StickerItem
import com.example.ui.theme.DangerRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleVariant
import java.io.File

/**
 * Renderizador de Stickers e GIFs animados reais sobre o vídeo.
 */
@Composable
fun StickerLayer(
    stickers: List<StickerItem>,
    currentPlayheadMs: Long,
    selectedStickerId: String?,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (id: String, newPosX: Float, newPosY: Float) -> Unit,
    onDeleteSticker: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        stickers.forEach { item ->
            if (item.isVisible) {
                val animState = OverlayAnimationEngine.calculateStickerState(
                    playheadMs = currentPlayheadMs,
                    startTimeMs = item.startTimeMs,
                    durationMs = item.durationMs,
                    baseScale = item.scale,
                    baseOpacity = item.opacity,
                    animationIn = item.animationIn,
                    animationOut = item.animationOut,
                    animationDurationMs = item.animationDurationMs
                )

                if (animState.isVisible) {
                    val isSelected = item.id == selectedStickerId

                    val model = when {
                        item.localPath.isNotBlank() -> File(item.localPath)
                        item.uri.isNotBlank() -> item.uri
                        else -> null
                    }
                    val isFileMissing = model is File && !model.exists()

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = ((item.posX - 0.5f) * 280f + animState.translationX).dp,
                                y = ((item.posY - 0.5f) * 400f + animState.translationY).dp
                            )
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = animState.scale
                                scaleY = animState.scale
                                rotationZ = item.rotation
                                alpha = animState.alpha
                            }
                            .clickable { onSelectSticker(item.id) }
                            .pointerInput(item.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaX = dragAmount.x / 280f
                                    val deltaY = dragAmount.y / 400f
                                    val newX = (item.posX + deltaX).coerceIn(0.05f, 0.95f)
                                    val newY = (item.posY + deltaY).coerceIn(0.05f, 0.95f)
                                    onMoveSticker(item.id, newX, newY)
                                }
                            }
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = PrimaryPurpleVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(4.dp)
                                } else Modifier
                            )
                            .testTag("sticker_overlay_${item.id}")
                    ) {
                        if (isFileMissing || model == null) {
                            // Tolerância a arquivo ausente: não trava, mostra placeholder limpo
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.DarkGray.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = "Mídia ausente",
                                        tint = DangerRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = item.name.ifBlank { "Sticker" },
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(model)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Badge de exclusão rápida
                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryPurple,
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .clickable { onDeleteSticker(item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover Sticker",
                                    tint = Color.White,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
