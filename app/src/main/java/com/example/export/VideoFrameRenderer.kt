package com.example.export

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import com.example.effect.ClipEffectState
import com.example.effect.ColorMatrixPipeline
import com.example.model.MediaClip
import com.example.model.MediaType
import com.example.model.VFXEffectItem
import com.example.transition.ClipTransitionTransform
import com.example.transition.TransitionType
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Renderizador de quadros de vídeo de alta fidelidade para exportação.
 * Replica exatamente a mesma lógica visual do preview (Canvas, ColorMatrix, VFX, Transições, Textos e Stickers).
 */
class VideoFrameRenderer(
    private val context: Context,
    private val config: VideoExportConfig
) : AutoCloseable {

    private val targetWidth = config.width
    private val targetHeight = config.height

    // Caches de mídia para evitar re-leitura de disco em cada quadro
    private val imageCache = mutableMapOf<String, Bitmap>()
    private val videoRetrievers = mutableMapOf<String, MediaMetadataRetriever>()

    // Paints reutilizáveis para máxima performance
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val vfxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Bitmap temporário para composições intermediárias durante transições
    private val layerBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    private val layerCanvas = Canvas(layerBitmap)

    /**
     * Renderiza o quadro completo no bitmap de destino.
     */
    fun render(snapshot: ExportFrameSnapshot, targetBitmap: Bitmap) {
        val canvas = Canvas(targetBitmap)
        // 1. Fundo preto padrão (letterbox/pillarbox)
        canvas.drawColor(Color.BLACK)

        // 2. Transições ativas ou Clipe individual
        val transition = snapshot.activeTransition
        if (transition != null) {
            renderTransition(canvas, snapshot, transition)
        } else if (snapshot.activeClipInfo != null) {
            val clipInfo = snapshot.activeClipInfo
            renderClip(
                canvas = canvas,
                clip = clipInfo.clip,
                sourceTimeMs = clipInfo.sourcePositionMs,
                transform = ClipTransitionTransform(alpha = 1.0f),
                activeVfx = snapshot.activeVfx
            )
        }

        // 3. Efeitos visuais globais (VFX)
        if (transition == null && snapshot.activeVfx.isNotEmpty()) {
            renderVfxOverlay(canvas, snapshot.activeVfx, snapshot.timestampMs)
        }

        // 4. Sublegendas (Subtitles)
        if (snapshot.activeSubtitles.isNotEmpty()) {
            renderSubtitles(canvas, snapshot)
        }

        // 5. Overlays de Texto com animações
        if (snapshot.activeTexts.isNotEmpty()) {
            renderTextOverlays(canvas, snapshot.activeTexts)
        }

        // 6. Stickers e GIFs
        if (snapshot.activeStickers.isNotEmpty()) {
            renderStickers(canvas, snapshot.activeStickers, snapshot.timestampMs)
        }

        // 7. Marca d'Água Boti se o usuário não for Pro ou não removeu
        if (!config.removeWatermark) {
            renderWatermark(canvas)
        }
    }

    // -------------------------------------------------------------
    // RENDERIZAÇÃO DE TRANSIÇÕES
    // -------------------------------------------------------------

    private fun renderTransition(
        canvas: Canvas,
        snapshot: ExportFrameSnapshot,
        transition: com.example.transition.ActiveTransitionInfo
    ) {
        val type = transition.type

        // Renderiza Clip A na camada intermediária
        layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val sourceTimeA = transition.clipA.durationMs.coerceAtLeast(1L) - 50L
        renderClip(
            canvas = layerCanvas,
            clip = transition.clipA,
            sourceTimeMs = sourceTimeA,
            transform = transition.transformA,
            activeVfx = snapshot.activeVfx
        )

        // Aplica Wipe se for o caso
        if (type == TransitionType.WIPE) {
            canvas.save()
            val wipeX = targetWidth * transition.transformA.wipeProgress
            canvas.clipRect(0f, 0f, wipeX, targetHeight.toFloat())
            bitmapPaint.alpha = (transition.transformA.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layerBitmap, 0f, 0f, bitmapPaint)
            canvas.restore()
        } else {
            bitmapPaint.alpha = (transition.transformA.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layerBitmap, 0f, 0f, bitmapPaint)
        }

        // Renderiza Clip B na camada intermediária
        layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        renderClip(
            canvas = layerCanvas,
            clip = transition.clipB,
            sourceTimeMs = 50L,
            transform = transition.transformB,
            activeVfx = snapshot.activeVfx
        )

        if (type == TransitionType.WIPE) {
            canvas.save()
            val startX = targetWidth * (1f - transition.transformB.wipeProgress)
            canvas.clipRect(startX, 0f, targetWidth.toFloat(), targetHeight.toFloat())
            bitmapPaint.alpha = (transition.transformB.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layerBitmap, 0f, 0f, bitmapPaint)
            canvas.restore()
        } else {
            bitmapPaint.alpha = (transition.transformB.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layerBitmap, 0f, 0f, bitmapPaint)
        }
    }

    // -------------------------------------------------------------
    // RENDERIZAÇÃO DE CLIPE (VÍDEO OU FOTO)
    // -------------------------------------------------------------

    private fun renderClip(
        canvas: Canvas,
        clip: MediaClip,
        sourceTimeMs: Long,
        transform: ClipTransitionTransform,
        activeVfx: List<VFXEffectItem>
    ) {
        val srcBitmap = getFrameForClip(clip, sourceTimeMs)

        canvas.save()

        // 1. Transformações de Transição
        if (transform.translationX != 0f || transform.translationY != 0f) {
            canvas.translate(transform.translationX, transform.translationY)
        }
        if (transform.scale != 1.0f) {
            canvas.scale(transform.scale, transform.scale, targetWidth / 2f, targetHeight / 2f)
        }

        // 2. Transformações do Clipe (Position, Scale, Rotation, Flip)
        val centerX = targetWidth / 2f + clip.positionX
        val centerY = targetHeight / 2f + clip.positionY
        val clipScaleX = clip.scale * (if (clip.flipHorizontal) -1f else 1f)
        val clipScaleY = clip.scale * (if (clip.flipVertical) -1f else 1f)

        canvas.translate(clip.positionX, clip.positionY)
        canvas.rotate(clip.rotation, targetWidth / 2f, targetHeight / 2f)
        canvas.scale(clipScaleX, clipScaleY, targetWidth / 2f, targetHeight / 2f)

        // 3. Matriz de Cor (ColorMatrixPipeline)
        val colorMatrix = ColorMatrixPipeline.composeColorMatrix(
            brightness = clip.brightness,
            contrast = clip.contrast,
            saturation = clip.saturation,
            filterName = clip.filter
        )
        bitmapPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        bitmapPaint.alpha = ((clip.opacity * transform.alpha).coerceIn(0f, 1f) * 255).toInt()

        if (srcBitmap != null && !srcBitmap.isRecycled) {
            // Desenha com Letterbox/Fit perfeito mantendo aspect ratio e recorte (Crop)
            val srcW = srcBitmap.width.toFloat()
            val srcH = srcBitmap.height.toFloat()

            val targetCropRatio = when (clip.cropRatio) {
                "1:1" -> 1f
                "16:9" -> 16f / 9f
                "9:16" -> 9f / 16f
                "4:5" -> 4f / 5f
                "4:3" -> 4f / 3f
                else -> srcW / srcH
            }

            val currentSrcRatio = srcW / srcH
            val cropW = if (currentSrcRatio > targetCropRatio) srcH * targetCropRatio else srcW
            val cropH = if (currentSrcRatio > targetCropRatio) srcH else srcW / targetCropRatio
            val cropX = (srcW - cropW) / 2f
            val cropY = (srcH - cropH) / 2f
            val srcRect = Rect(cropX.toInt(), cropY.toInt(), (cropX + cropW).toInt(), (cropY + cropH).toInt())

            val scale = min(targetWidth / cropW, targetHeight / cropH)
            val dstW = cropW * scale
            val dstH = cropH * scale
            val left = (targetWidth - dstW) / 2f
            val top = (targetHeight - dstH) / 2f

            val dstRect = RectF(left, top, left + dstW, top + dstH)
            canvas.drawBitmap(srcBitmap, srcRect, dstRect, bitmapPaint)
        } else {
            // Fallback elegante caso a mídia esteja ausente ou seja gerada dinamicamente
            renderSyntheticMediaPlaceholder(canvas, clip, sourceTimeMs)
        }

        canvas.restore()
        bitmapPaint.colorFilter = null
        bitmapPaint.alpha = 255
    }

    private fun renderSyntheticMediaPlaceholder(canvas: Canvas, clip: MediaClip, sourceTimeMs: Long) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())

        // Gradiente cinematográfico estilizado
        val grad = LinearGradient(
            0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(),
            intArrayOf(0xFF1E1035.toInt(), 0xFF31105C.toInt(), 0xFF120824.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = grad
        canvas.drawRect(rect, paint)
        paint.shader = null

        // Título e Timecode
        paint.color = Color.WHITE
        paint.textSize = targetHeight * 0.035f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(clip.title.ifBlank { "Clipe Boti" }, targetWidth / 2f, targetHeight / 2f - 20f, paint)

        paint.textSize = targetHeight * 0.02f
        paint.color = 0xFFBB86FC.toInt()
        val sec = sourceTimeMs / 1000
        val ms = (sourceTimeMs % 1000) / 10
        canvas.drawText(String.format("%02d:%02d.%02d", sec / 60, sec % 60, ms), targetWidth / 2f, targetHeight / 2f + 30f, paint)
    }

    // -------------------------------------------------------------
    // EFEITOS VFX REAIS (CANVAS)
    // -------------------------------------------------------------

    private fun renderVfxOverlay(canvas: Canvas, vfxList: List<VFXEffectItem>, playheadMs: Long) {
        vfxList.filter { vfx ->
            vfx.isEnabled && playheadMs >= vfx.startTimeMs && playheadMs < (vfx.startTimeMs + vfx.durationMs)
        }.forEach { vfx ->
            val intensity = (vfx.intensity / 100f).coerceIn(0f, 1f)
            when (vfx.name.trim()) {
                "Vinheta" -> {
                    val radius = max(targetWidth, targetHeight) / 1.4f
                    val alpha = (0.75f * intensity).coerceIn(0f, 0.95f)
                    val shader = RadialGradient(
                        targetWidth / 2f, targetHeight / 2f, radius,
                        intArrayOf(
                            Color.TRANSPARENT,
                            Color.argb((alpha * 0.4f * 255).toInt(), 0, 0, 0),
                            Color.argb((alpha * 255).toInt(), 0, 0, 0)
                        ),
                        floatArrayOf(0f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    vfxPaint.shader = shader
                    vfxPaint.xfermode = null
                    canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), vfxPaint)
                    vfxPaint.shader = null
                }
                "Luz Vazada" -> {
                    val alpha = (0.55f * intensity).coerceIn(0f, 0.85f)
                    val shader = RadialGradient(
                        targetWidth * 0.85f, targetHeight * 0.15f, targetWidth * 0.8f,
                        intArrayOf(
                            Color.argb((alpha * 255).toInt(), 255, 152, 0),
                            Color.argb((alpha * 0.5f * 255).toInt(), 255, 87, 34),
                            Color.TRANSPARENT
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    vfxPaint.shader = shader
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), vfxPaint)
                    vfxPaint.shader = null
                    vfxPaint.xfermode = null
                }
                "Glitch" -> {
                    val sliceCount = (6 * intensity).toInt().coerceAtLeast(2)
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    for (i in 0 until sliceCount) {
                        val y = (targetHeight / sliceCount) * i
                        val h = targetHeight / (sliceCount * 3f)
                        val color = if (i % 2 == 0) Color.CYAN else Color.MAGENTA
                        vfxPaint.color = Color.argb((intensity * 0.4f * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                        canvas.drawRect(0f, y.toFloat(), targetWidth.toFloat(), (y + h), vfxPaint)
                    }
                    vfxPaint.xfermode = null
                }
                "RGB Split" -> {
                    val offsetPx = 12f * intensity
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    vfxPaint.color = Color.argb((intensity * 0.35f * 255).toInt(), 255, 0, 0)
                    canvas.drawRect(-offsetPx, 0f, targetWidth - offsetPx, targetHeight.toFloat(), vfxPaint)
                    vfxPaint.color = Color.argb((intensity * 0.35f * 255).toInt(), 0, 255, 255)
                    canvas.drawRect(offsetPx, 0f, targetWidth + offsetPx, targetHeight.toFloat(), vfxPaint)
                    vfxPaint.xfermode = null
                }
                "VHS 90s" -> {
                    vfxPaint.color = Color.argb((intensity * 0.25f * 255).toInt(), 0, 0, 0)
                    vfxPaint.strokeWidth = 2f
                    var y = 0f
                    while (y < targetHeight) {
                        canvas.drawLine(0f, y, targetWidth.toFloat(), y, vfxPaint)
                        y += 6f
                    }
                    // Camcorder timestamp
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb((intensity * 0.8f * 255).toInt().coerceIn(40, 255), 0, 255, 102)
                        textSize = targetHeight * 0.024f
                        typeface = Typeface.MONOSPACE
                        style = Paint.Style.FILL
                    }
                    val sec = playheadMs / 1000
                    canvas.drawText(
                        String.format("PLAY ▶ SP\n00:%02d:%02d", sec / 60, sec % 60),
                        targetWidth * 0.05f,
                        targetHeight * 0.92f,
                        textP
                    )
                }
                "Bokeh Glow" -> {
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    val orbs = listOf(
                        Triple(0.25f, 0.35f, targetWidth * 0.12f),
                        Triple(0.70f, 0.60f, targetWidth * 0.16f),
                        Triple(0.40f, 0.75f, targetWidth * 0.10f),
                        Triple(0.80f, 0.25f, targetWidth * 0.14f)
                    )
                    orbs.forEach { (relX, relY, radius) ->
                        val shader = RadialGradient(
                            targetWidth * relX, targetHeight * relY, radius,
                            intArrayOf(
                                Color.argb((intensity * 0.4f * 255).toInt(), 224, 231, 255),
                                Color.argb((intensity * 0.15f * 255).toInt(), 199, 210, 254),
                                Color.TRANSPARENT
                            ),
                            null,
                            Shader.TileMode.CLAMP
                        )
                        vfxPaint.shader = shader
                        canvas.drawCircle(targetWidth * relX, targetHeight * relY, radius, vfxPaint)
                    }
                    vfxPaint.shader = null
                    vfxPaint.xfermode = null
                }
                "Pixel Art" -> {
                    vfxPaint.color = Color.argb((intensity * 0.25f * 255).toInt(), 0, 0, 0)
                    vfxPaint.strokeWidth = 1.5f
                    val step = (targetWidth / 80f).coerceAtLeast(8f)
                    var x = 0f
                    while (x < targetWidth) {
                        canvas.drawLine(x, 0f, x, targetHeight.toFloat(), vfxPaint)
                        x += step
                    }
                    var yy = 0f
                    while (yy < targetHeight) {
                        canvas.drawLine(0f, yy, targetWidth.toFloat(), yy, vfxPaint)
                        yy += step
                    }
                }
                "Desfoque" -> {
                    vfxPaint.color = Color.argb((intensity * 0.25f * 255).toInt(), 255, 255, 255)
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), vfxPaint)
                    vfxPaint.xfermode = null
                }
                "Shake" -> {
                    val offset = 10f * intensity
                    vfxPaint.color = Color.argb((intensity * 0.18f * 255).toInt(), 0, 0, 0)
                    vfxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    canvas.drawRect(offset, offset, targetWidth.toFloat(), targetHeight.toFloat(), vfxPaint)
                    vfxPaint.xfermode = null
                }
            }
        }
    }

    // -------------------------------------------------------------
    // OVERLAYS DE TEXTO
    // -------------------------------------------------------------

    private fun renderTextOverlays(canvas: Canvas, texts: List<EvaluatedTextOverlay>) {
        val baseScale = targetHeight / 1920f

        texts.forEach { evaluated ->
            val item = evaluated.item
            val state = evaluated.animState
            val displayText = state.visibleText.ifBlank { item.text }

            if (displayText.isNotBlank()) {
                canvas.save()

                // Posição no canvas normalizada: posX (0..1), posY (0..1)
                val posX = targetWidth * item.posX + state.translationX * baseScale
                val posY = targetHeight * item.posY + state.translationY * baseScale

                canvas.translate(posX, posY)
                canvas.rotate(item.rotation)
                val finalScale = item.scale * state.scale
                canvas.scale(finalScale, finalScale)

                val scaledFontSize = (item.fontSizeSp * 2.5f * baseScale).coerceAtLeast(16f)

                textPaint.textSize = scaledFontSize
                textPaint.color = parseColorSafely(item.colorHex, Color.WHITE)
                textPaint.alpha = ((item.opacity * state.alpha).coerceIn(0f, 1f) * 255).toInt()
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textAlign = when (item.alignment.lowercase()) {
                    "left" -> Paint.Align.LEFT
                    "right" -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }

                // Sombra se configurada
                if (item.shadowColorHex != null && item.shadowRadius > 0f) {
                    textPaint.setShadowLayer(
                        item.shadowRadius * baseScale * 2f,
                        2f * baseScale,
                        2f * baseScale,
                        parseColorSafely(item.shadowColorHex, Color.BLACK)
                    )
                } else {
                    textPaint.clearShadowLayer()
                }

                val textBounds = Rect()
                textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)

                // Background box se configurado
                if (item.bgHex != null) {
                    bgPaint.color = parseColorSafely(item.bgHex, Color.BLACK)
                    bgPaint.alpha = ((item.opacity * state.alpha * 0.75f).coerceIn(0f, 1f) * 255).toInt()
                    val padH = 20f * baseScale
                    val padV = 10f * baseScale
                    val bgRect = RectF(
                        -textBounds.width() / 2f - padH,
                        -textBounds.height() - padV,
                        textBounds.width() / 2f + padH,
                        padV
                    )
                    canvas.drawRoundRect(bgRect, 12f * baseScale, 12f * baseScale, bgPaint)
                }

                // Stroke se configurado
                if (item.strokeColorHex != null && item.strokeWidth > 0f) {
                    textStrokePaint.textSize = scaledFontSize
                    textStrokePaint.color = parseColorSafely(item.strokeColorHex, Color.BLACK)
                    textStrokePaint.strokeWidth = item.strokeWidth * baseScale * 2f
                    textStrokePaint.alpha = textPaint.alpha
                    textStrokePaint.textAlign = textPaint.textAlign
                    textStrokePaint.typeface = textPaint.typeface
                    canvas.drawText(displayText, 0f, 0f, textStrokePaint)
                }

                // Desenho do texto principal
                canvas.drawText(displayText, 0f, 0f, textPaint)

                canvas.restore()
            }
        }
    }

    // -------------------------------------------------------------
    // STICKERS E GIFS
    // -------------------------------------------------------------

    private fun renderStickers(canvas: Canvas, stickers: List<EvaluatedSticker>, playheadMs: Long) {
        val baseScale = targetHeight / 1920f

        stickers.forEach { evaluated ->
            val item = evaluated.item
            val state = evaluated.animState

            val stickerBitmap = getStickerBitmap(item, playheadMs)
            if (stickerBitmap != null && !stickerBitmap.isRecycled) {
                canvas.save()

                val posX = targetWidth * item.posX + state.translationX * baseScale
                val posY = targetHeight * item.posY + state.translationY * baseScale

                canvas.translate(posX, posY)
                canvas.rotate(item.rotation)
                val finalScale = item.scale * state.scale * baseScale
                canvas.scale(finalScale, finalScale)

                val w = stickerBitmap.width.toFloat()
                val h = stickerBitmap.height.toFloat()
                val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)

                bitmapPaint.alpha = ((item.opacity * state.alpha).coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(stickerBitmap, null, rect, bitmapPaint)

                canvas.restore()
                bitmapPaint.alpha = 255
            }
        }
    }

    // -------------------------------------------------------------
    // SUBTÍTULOS / LEGENDAS
    // -------------------------------------------------------------

    private fun renderSubtitles(canvas: Canvas, snapshot: ExportFrameSnapshot) {
        val style = snapshot.subtitleStyle
        val baseScale = targetHeight / 1920f
        val posY = targetHeight * style.positionYPercent

        snapshot.activeSubtitles.forEach { sub ->
            val text = sub.text
            val textSize = style.fontSizeSp * 2.2f * baseScale

            textPaint.textSize = textSize
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = parseColorSafely(style.textColorHex, Color.WHITE)

            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)

            // Caixa de fundo da legenda
            if (style.bgEnabled) {
                bgPaint.color = parseColorSafely(style.bgColorHex, Color.BLACK)
                bgPaint.alpha = (style.bgOpacity.coerceIn(0f, 1f) * 255).toInt()
                val padH = 24f * baseScale
                val padV = 12f * baseScale
                val rect = RectF(
                    targetWidth / 2f - textBounds.width() / 2f - padH,
                    posY - textBounds.height() - padV,
                    targetWidth / 2f + textBounds.width() / 2f + padH,
                    posY + padV
                )
                canvas.drawRoundRect(rect, 8f * baseScale, 8f * baseScale, bgPaint)
            }

            // Contorno preto para máxima legibilidade
            if (style.strokeWidth > 0f) {
                textStrokePaint.textSize = textSize
                textStrokePaint.typeface = textPaint.typeface
                textStrokePaint.textAlign = Paint.Align.CENTER
                textStrokePaint.color = parseColorSafely(style.strokeColorHex, Color.BLACK)
                textStrokePaint.strokeWidth = style.strokeWidth * baseScale * 2f
                canvas.drawText(text, targetWidth / 2f, posY, textStrokePaint)
            }

            canvas.drawText(text, targetWidth / 2f, posY, textPaint)
        }
    }

    // -------------------------------------------------------------
    // MARCA D'ÁGUA
    // -------------------------------------------------------------

    private fun renderWatermark(canvas: Canvas) {
        val baseScale = targetHeight / 1920f
        val pad = 36f * baseScale
        val badgeW = 200f * baseScale
        val badgeH = 50f * baseScale

        val right = targetWidth - pad
        val top = pad
        val left = right - badgeW
        val bottom = top + badgeH

        // Fundo escuro translúcido com cantos arredondados
        bgPaint.color = Color.BLACK
        bgPaint.alpha = 160
        canvas.drawRoundRect(RectF(left, top, right, bottom), 16f * baseScale, 16f * baseScale, bgPaint)

        // Texto do logotipo
        textPaint.textSize = 20f * baseScale
        textPaint.color = Color.WHITE
        textPaint.alpha = 230
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Boti Video", (left + right) / 2f, top + badgeH * 0.65f, textPaint)
    }

    // -------------------------------------------------------------
    // RECURSOS DE MÍDIA E CACHE
    // -------------------------------------------------------------

    private fun getFrameForClip(clip: MediaClip, sourceTimeMs: Long): Bitmap? {
        val key = "${clip.id}_${clip.localPath}"

        // Se for foto, carrega uma única vez no cache
        if (clip.type == MediaType.PHOTO) {
            imageCache[key]?.let { return it }
            val path = clip.localPath.ifBlank { clip.thumbnailPath }
            if (path.isNotBlank() && File(path).exists()) {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    imageCache[key] = bmp
                    return bmp
                }
            }
            return null
        }

        // Se for vídeo, obtém o frame via MediaMetadataRetriever
        val videoPath = clip.localPath
        if (videoPath.isNotBlank() && File(videoPath).exists()) {
            val retriever = videoRetrievers.getOrPut(videoPath) {
                MediaMetadataRetriever().apply {
                    setDataSource(videoPath)
                }
            }
            return try {
                retriever.getFrameAtTime(
                    sourceTimeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            } catch (_: Exception) {
                null
            }
        }

        // Fallback: se houver thumbnail gerado no cache
        if (clip.thumbnailPath.isNotBlank() && File(clip.thumbnailPath).exists()) {
            imageCache[clip.thumbnailPath]?.let { return it }
            val bmp = BitmapFactory.decodeFile(clip.thumbnailPath)
            if (bmp != null) {
                imageCache[clip.thumbnailPath] = bmp
                return bmp
            }
        }

        return null
    }

    private fun getStickerBitmap(item: com.example.model.StickerItem, playheadMs: Long): Bitmap? {
        val key = "${item.id}_${item.localPath}_${if (item.isVideo) (playheadMs / 100) else 0}"
        imageCache[key]?.let { return it }

        // Vídeo de sobreposição: decodifica quadro real sincronizado com playhead
        if (item.isVideo) {
            val relativeTimeMs = (playheadMs - item.startTimeMs).coerceAtLeast(0L)
            val videoBmp = com.example.util.VideoFrameExtractor.extractFrame(
                context = context,
                localPath = item.localPath,
                uri = item.uri,
                timeMs = relativeTimeMs
            )
            if (videoBmp != null && !videoBmp.isRecycled) {
                imageCache[key] = videoBmp
                return videoBmp
            }
        }

        if (item.localPath.isNotBlank() && File(item.localPath).exists()) {
            val bmp = BitmapFactory.decodeFile(item.localPath)
            if (bmp != null) {
                imageCache[key] = bmp
                return bmp
            }
        }

        if (item.uri.isNotBlank()) {
            try {
                val parsed = android.net.Uri.parse(item.uri)
                if (parsed.scheme == "content" || parsed.scheme == "file") {
                    context.contentResolver.openInputStream(parsed)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            imageCache[key] = bmp
                            return bmp
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback: Gera um sticker gráfico procedural colorido estilizado
        val size = 160
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF4081.toInt()
            style = Paint.Style.FILL
        }
        c.drawCircle(size / 2f, size / 2f, size / 2.2f, p)
        p.color = Color.WHITE
        p.textSize = size * 0.35f
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("★", size / 2f, size * 0.62f, p)
        imageCache[key] = bmp
        return bmp
    }

    private fun parseColorSafely(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }

    override fun close() {
        videoRetrievers.values.forEach { retriever ->
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        videoRetrievers.clear()

        imageCache.values.forEach { bmp ->
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        imageCache.clear()

        if (!layerBitmap.isRecycled) {
            layerBitmap.recycle()
        }
    }
}
