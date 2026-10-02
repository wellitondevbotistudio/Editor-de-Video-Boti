package com.example.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TextOverlayItem
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleVariant

/**
 * Renderizador de overlays de texto reais sobre o vídeo com animações e manipulação direta.
 */
@Composable
fun TextOverlayLayer(
    texts: List<TextOverlayItem>,
    currentPlayheadMs: Long,
    selectedTextId: String?,
    onSelectText: (String?) -> Unit,
    onMoveText: (id: String, newPosX: Float, newPosY: Float) -> Unit,
    onScaleText: ((id: String, newScale: Float) -> Unit)? = null,
    onRotateText: ((id: String, newRotation: Float) -> Unit)? = null,
    onDeleteText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        val canvasWpx = with(density) { canvasWidth.toPx() }
        val canvasHpx = with(density) { canvasHeight.toPx() }

        texts.forEach { item ->
            if (item.isVisible) {
                val animState = OverlayAnimationEngine.calculateTextState(
                    playheadMs = currentPlayheadMs,
                    startTimeMs = item.startTimeMs,
                    durationMs = item.durationMs,
                    baseScale = item.scale,
                    baseOpacity = item.opacity,
                    fullText = item.text,
                    animationIn = item.animationIn,
                    animationOut = item.animationOut,
                    animationDurationMs = item.animationDurationMs,
                    textAnimationMode = item.textAnimationMode
                )

                if (animState.isVisible) {
                    val isSelected = item.id == selectedTextId
                    val textColor = parseColorSafely(item.colorHex, Color.White)
                    val bgColor = item.bgHex?.let { parseColorSafely(it, Color.Transparent) }
                    val textAlign = when (item.alignment.lowercase()) {
                        "left" -> TextAlign.Left
                        "right" -> TextAlign.Right
                        else -> TextAlign.Center
                    }

                    val offsetX = ((item.posX - 0.5f) * canvasWidth.value).dp + animState.translationX.dp
                    val offsetY = ((item.posY - 0.5f) * canvasHeight.value).dp + animState.translationY.dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = offsetX, y = offsetY)
                            .graphicsLayer {
                                scaleX = animState.scale
                                scaleY = animState.scale
                                rotationZ = item.rotation
                                alpha = animState.alpha
                            }
                            .clickable { onSelectText(item.id) }
                            .pointerInput(item.id, canvasWpx, canvasHpx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaX = if (canvasWpx > 0f) dragAmount.x / canvasWpx else 0f
                                    val deltaY = if (canvasHpx > 0f) dragAmount.y / canvasHpx else 0f
                                    val newX = (item.posX + deltaX).coerceIn(0.05f, 0.95f)
                                    val newY = (item.posY + deltaY).coerceIn(0.05f, 0.95f)
                                    onMoveText(item.id, newX, newY)
                                }
                            }
                            .pointerInput(item.id) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1.0f && onScaleText != null) {
                                        val newScale = (item.scale * zoom).coerceIn(0.3f, 3.5f)
                                        onScaleText(item.id, newScale)
                                    }
                                }
                            }
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = PrimaryPurpleVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(4.dp)
                                } else Modifier
                            )
                            .testTag("text_overlay_${item.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (bgColor != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(bgColor)
                                    } else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = animState.visibleText,
                                color = textColor,
                                fontSize = item.fontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = textAlign,
                                fontFamily = if (item.fontFamily == "Monospace") FontFamily.Monospace else FontFamily.Default,
                                style = TextStyle(
                                    shadow = if (item.shadowColorHex != null && item.shadowRadius > 0f) {
                                        Shadow(
                                            color = parseColorSafely(item.shadowColorHex, Color.Black),
                                            blurRadius = item.shadowRadius
                                        )
                                    } else null
                                )
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
                                    .clickable { onDeleteText(item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover Texto",
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
                                        onRotateText?.invoke(item.id, newRot)
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Girar Texto",
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
                                            onScaleText?.invoke(item.id, newScale)
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Redimensionar Texto",
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

fun parseColorSafely(hex: String?, defaultColor: Color): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        defaultColor
    }
}
