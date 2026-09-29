package com.example.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
 * Renderizador de overlays de texto reais sobre o vídeo com animações e seleção interativa.
 */
@Composable
fun TextOverlayLayer(
    texts: List<TextOverlayItem>,
    currentPlayheadMs: Long,
    selectedTextId: String?,
    onSelectText: (String?) -> Unit,
    onMoveText: (id: String, newPosX: Float, newPosY: Float) -> Unit,
    onDeleteText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
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

                    // Posicionamento relativo normalizado: (item.posX - 0.5f) * largura, (item.posY - 0.5f) * altura
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = ((item.posX - 0.5f) * 280f + animState.translationX).dp,
                                y = ((item.posY - 0.5f) * 400f + animState.translationY).dp
                            )
                            .graphicsLayer {
                                scaleX = animState.scale
                                scaleY = animState.scale
                                rotationZ = item.rotation
                                alpha = animState.alpha
                            }
                            .clickable { onSelectText(item.id) }
                            .pointerInput(item.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaX = dragAmount.x / 280f
                                    val deltaY = dragAmount.y / 400f
                                    val newX = (item.posX + deltaX).coerceIn(0.05f, 0.95f)
                                    val newY = (item.posY + deltaY).coerceIn(0.05f, 0.95f)
                                    onMoveText(item.id, newX, newY)
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

                        // Badge de fechamento/remoção quando selecionado
                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryPurple,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .clickable { onDeleteText(item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover",
                                    tint = Color.White,
                                    modifier = Modifier.padding(2.dp)
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
