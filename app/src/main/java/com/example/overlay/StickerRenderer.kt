package com.example.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.util.VideoFrameExtractor
import java.io.File

/**
 * Renderizador de Stickers, Imagens e Vídeos de Sobreposição reais sobre o vídeo com manipulação direta estável.
 */
@Composable
fun StickerLayer(
    stickers: List<StickerItem>,
    currentPlayheadMs: Long,
    selectedStickerId: String?,
    onSelectSticker: (String?) -> Unit,
    onMoveSticker: (id: String, newPosX: Float, newPosY: Float, isFinished: Boolean) -> Unit,
    onScaleSticker: ((id: String, newScale: Float) -> Unit)? = null,
    onRotateSticker: ((id: String, newRotation: Float) -> Unit)? = null,
    onDeleteSticker: (String) -> Unit,
    modifier: Modifier = Modifier,
    isOverlayVisible: Boolean = true,
    isOverlayLocked: Boolean = false
) {
    if (!isOverlayVisible) return

    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        val canvasWpx = with(density) { canvasWidth.toPx() }
        val canvasHpx = with(density) { canvasHeight.toPx() }

        stickers.forEach { item ->
            // Se o item estiver fora do intervalo temporal visível, pula qualquer computação imediatamente
            val isTimeActive = currentPlayheadMs in item.startTimeMs until (item.startTimeMs + item.durationMs)
            if (!isTimeActive && !item.isVisible) return@forEach

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
                    val isLocked = isOverlayLocked || item.isLocked

                    val model = when {
                        item.localPath.isNotBlank() -> File(item.localPath)
                        item.uri.isNotBlank() -> item.uri
                        else -> null
                    }
                    val isFileMissing = model is File && !model.exists()

                    val offsetX = ((item.posX - 0.5f) * canvasWidth.value).dp + animState.translationX.dp
                    val offsetY = ((item.posY - 0.5f) * canvasHeight.value).dp + animState.translationY.dp

                    val currentItem by rememberUpdatedState(item)
                    val stickerInteractionSource = remember(item.id) { MutableInteractionSource() }
                    val gestureModifier = if (isLocked) {
                        Modifier.clickable(
                            interactionSource = stickerInteractionSource,
                            indication = null
                        ) { onSelectSticker(item.id) }
                    } else {
                        Modifier
                            .pointerInput(item.id, canvasWpx, canvasHpx) {
                                var curX = item.posX
                                var curY = item.posY
                                detectDragGestures(
                                    onDragStart = {
                                        curX = currentItem.posX
                                        curY = currentItem.posY
                                        onSelectSticker(item.id)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (canvasWpx > 0f && canvasHpx > 0f) {
                                            val deltaX = dragAmount.x / canvasWpx
                                            val deltaY = dragAmount.y / canvasHpx
                                            curX = (curX + deltaX).coerceIn(0.05f, 0.95f)
                                            curY = (curY + deltaY).coerceIn(0.05f, 0.95f)
                                            onMoveSticker(item.id, curX, curY, false)
                                        }
                                    },
                                    onDragEnd = {
                                        onMoveSticker(item.id, curX, curY, true)
                                    },
                                    onDragCancel = {
                                        onMoveSticker(item.id, curX, curY, true)
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = stickerInteractionSource,
                                indication = null
                            ) {
                                onSelectSticker(item.id)
                            }
                    }

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
                            .then(gestureModifier)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isLocked) Color(0xFFEAB308) else PrimaryPurpleVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(4.dp)
                                } else Modifier
                            )
                            .testTag("sticker_overlay_${item.id}")
                    ) {
                        if (item.isVideo) {
                            // Extração de frame real do vídeo de sobreposição sincronizado com a timeline
                            val relativeTimeMs = (currentPlayheadMs - item.startTimeMs).coerceIn(0L, item.durationMs)
                            val videoFrameBitmap = remember(item.localPath, item.uri, relativeTimeMs / 100) {
                                VideoFrameExtractor.extractFrame(
                                    context = context,
                                    localPath = item.localPath.ifBlank { null },
                                    uri = item.uri.ifBlank { null },
                                    timeMs = relativeTimeMs
                                )
                            }

                            if (videoFrameBitmap != null && !videoFrameBitmap.isRecycled) {
                                Image(
                                    bitmap = videoFrameBitmap.asImageBitmap(),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (model != null && !isFileMissing) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(model)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
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
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = "Vídeo sobreposto",
                                            tint = PrimaryPurpleVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = item.name.ifBlank { "Vídeo" },
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        } else if (isFileMissing || model == null) {
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

                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("VÍDEO", color = Color.White, fontSize = 8.sp)
                                }
                            }
                        }

                        // Alças de controle quando selecionado
                        if (isSelected && isLocked) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAB308)),
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-11).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Sobreposição Bloqueada",
                                    tint = Color(0xFFEAB308),
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        } else if (isSelected) {
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
                                        var initialScale = item.scale
                                        var accumulatedDelta = 0f
                                        val referencePx = (canvasWpx * 0.45f).coerceAtLeast(150f)
                                        detectDragGestures(
                                            onDragStart = {
                                                initialScale = item.scale
                                                accumulatedDelta = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                accumulatedDelta += (dragAmount.x + dragAmount.y)
                                                val factor = 1f + (accumulatedDelta / referencePx)
                                                val newScale = (initialScale * factor).coerceIn(0.3f, 3.5f)
                                                onScaleSticker?.invoke(item.id, newScale)
                                            }
                                        )
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
