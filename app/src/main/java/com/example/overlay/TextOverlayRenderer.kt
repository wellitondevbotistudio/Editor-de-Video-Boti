package com.example.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    modifier: Modifier = Modifier,
    isTextVisible: Boolean = true,
    isTextLocked: Boolean = false
) {
    if (!isTextVisible) return

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
                    val isLocked = isTextLocked || item.isLocked
                    val textColor = parseColorSafely(item.colorHex, Color.White)
                    val bgColor = item.bgHex?.let { parseColorSafely(it, Color.Transparent) }
                    val textAlign = when (item.alignment.lowercase()) {
                        "left" -> TextAlign.Left
                        "right" -> TextAlign.Right
                        else -> TextAlign.Center
                    }

                    val offsetX = ((item.posX - 0.5f) * canvasWidth.value).dp + animState.translationX.dp
                    val offsetY = ((item.posY - 0.5f) * canvasHeight.value).dp + animState.translationY.dp

                    val currentItem by rememberUpdatedState(item)
                    val gestureModifier = if (isLocked) {
                        Modifier.clickable { onSelectText(item.id) }
                    } else {
                        Modifier
                            .pointerInput(item.id) {
                                detectTapGestures(
                                    onTap = { onSelectText(item.id) }
                                )
                            }
                            .pointerInput(item.id, canvasWpx, canvasHpx) {
                                detectDragGestures(
                                    onDragStart = {
                                        onSelectText(item.id)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (canvasWpx > 0f && canvasHpx > 0f) {
                                            val deltaX = dragAmount.x / canvasWpx
                                            val deltaY = dragAmount.y / canvasHpx
                                            val newX = (currentItem.posX + deltaX).coerceIn(0.02f, 0.98f)
                                            val newY = (currentItem.posY + deltaY).coerceIn(0.02f, 0.98f)
                                            onMoveText(item.id, newX, newY)
                                        }
                                    }
                                )
                            }
                    }

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
                            .then(gestureModifier)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isLocked) Color(0xFFEAB308) else PrimaryPurpleVariant,
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
                        if (isSelected && isLocked) {
                            // Indicador de Camada Bloqueada
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
                                    contentDescription = "Texto Bloqueado",
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
                                                onScaleText?.invoke(item.id, newScale)
                                            }
                                        )
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
