package com.example.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.platform.LocalDensity
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
 * Renderizador de Stickers e GIFs animados reais sobre o vídeo com manipulação direta.
 */
@Composable
fun StickerLayer(
    stickers: List<StickerItem>,
    currentPlayheadMs: Long,
    selectedStickerId: String?,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (id: String, newPosX: Float, newPosY: Float) -> Unit,
    onScaleSticker: ((id: String, newScale: Float) -> Unit)? = null,
    onRotateSticker: ((id: String, newRotation: Float) -> Unit)? = null,
    onDeleteSticker: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        val canvasWpx = with(density) { canvasWidth.toPx() }
        val canvasHpx = with(density) { canvasHeight.toPx() }

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

                    val offsetX = ((item.posX - 0.5f) * canvasWidth.value).dp + animState.translationX.dp
                    val offsetY = ((item.posY - 0.5f) * canvasHeight.value).dp + animState.translationY.dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = offsetX, y = offsetY)
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = animState.scale
                                scaleY = animState.scale
                                rotationZ = item.rotation
                                alpha = animState.alpha
                            }
                            .clickable { onSelectSticker(item.id) }
                            .pointerInput(item.id, canvasWpx, canvasHpx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaX = if (canvasWpx > 0f) dragAmount.x / canvasWpx else 0f
                                    val deltaY = if (canvasHpx > 0f) dragAmount.y / canvasHpx else 0f
                                    val newX = (item.posX + deltaX).coerceIn(0.05f, 0.95f)
                                    val newY = (item.posY + deltaY).coerceIn(0.05f, 0.95f)
                                    onMoveSticker(item.id, newX, newY)
                                }
                            }
                            .pointerInput(item.id) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1.0f && onScaleSticker != null) {
                                        val newScale = (item.scale * zoom).coerceIn(0.3f, 3.5f)
                                        onScaleSticker(item.id, newScale)
                                    }
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

                        // Alças de controle quando selecionado
                        if (isSelected) {
                            // 1. Excluir (Topo-Direita)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryPurple,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .clickable { onDeleteSticker(item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover Sticker",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }

                            // 2. Girar (Topo-Esquerda)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurpleVariant),
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopStart)
                                    .offset(x = (-6).dp, y = (-6).dp)
                                    .clickable {
                                        val newRot = (item.rotation + 45f) % 360f
                                        onRotateSticker?.invoke(item.id, newRot)
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Girar Sticker",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }

                            // 3. Dimensionar (Base-Direita)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryPurpleVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .pointerInput(item.id, canvasWpx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = (dragAmount.x + dragAmount.y) / (canvasWpx * 0.4f).coerceAtLeast(100f)
                                            val newScale = (item.scale + delta).coerceIn(0.3f, 3.5f)
                                            onScaleSticker?.invoke(item.id, newScale)
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Redimensionar Sticker",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
