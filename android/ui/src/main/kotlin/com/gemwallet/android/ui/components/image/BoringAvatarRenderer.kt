package com.gemwallet.android.ui.components.image

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.gemwallet.android.ui.theme.extraLargeIconSize
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MIN_BORING_RENDER_SIZE_PX = 256
private const val BAUHAUS_VIEWPORT = 80f
private const val RING_VIEWPORT = 90f
private const val BEAM_VIEWPORT = 36f
private const val SUNSET_VIEWPORT = 80f
private const val MARBLE_VIEWPORT = 80f
private const val CONTRAST_DARK = 0xFF0F172A.toInt()
private const val CONTRAST_LIGHT = 0xFFFFFFFF.toInt()
private const val MARBLE_OVERLAY_ALPHA = 216

object BoringAvatarRenderer {

    fun render(context: Context, avatar: BoringAvatar): ByteArray {
        val size = (extraLargeIconSize.value * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(MIN_BORING_RENDER_SIZE_PX)
        val contentBitmap = createBitmap(size, size)
        val outputBitmap = createBitmap(size, size)
        return try {
            val drawer = prepareBoringAvatar(avatar, size.toFloat())
            drawer.draw(Canvas(contentBitmap))

            val outputCanvas = Canvas(outputBitmap)
            val center = size / 2f
            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = BitmapShader(contentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            outputCanvas.drawCircle(center, center, center, shaderPaint)
            outputBitmap.toPng().withAvatarSelectionMetadata(
                StoredAvatarSelection.Boring(
                    variant = avatar.variant,
                    palette = avatar.palette,
                    paletteMode = avatar.paletteMode,
                ),
            )
        } finally {
            contentBitmap.recycle()
            outputBitmap.recycle()
        }
    }
}

internal fun interface PreparedBoringAvatar {
    fun draw(canvas: Canvas)
}

internal fun prepareBoringAvatar(avatar: BoringAvatar, size: Float): PreparedBoringAvatar = when (avatar.variant) {
    BoringAvatarVariant.Bauhaus -> prepareBauhaus(avatar.seed, avatar.palette, size)
    BoringAvatarVariant.Ring -> prepareRing(avatar.seed, avatar.palette, size)
    BoringAvatarVariant.Beam -> prepareBeam(avatar.seed, avatar.palette, size)
    BoringAvatarVariant.Sunset -> prepareSunset(avatar.seed, avatar.palette, size)
    BoringAvatarVariant.Marble -> prepareMarble(avatar.seed, avatar.palette, size)
}

private fun prepareBauhaus(seed: String, palette: BoringAvatarPalette, size: Float): PreparedBoringAvatar {
    val num = boringHashCode(seed).toLong()
    val s = size / BAUHAUS_VIEWPORT
    val bgPaint = boringFill(paletteColor(num, palette))
    val rectPaint = boringFill(paletteColor(num + 1L, palette))
    val circlePaint = boringFill(paletteColor(num + 2L, palette))
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = paletteColor(num + 3L, palette)
        strokeWidth = 2.5f * s
    }

    val rectTx = unit(num * 2L, 40 - 18, 1) * s
    val rectTy = unit(num * 2L, 40 - 18, 2) * s
    val rectRot = unit(num * 2L, 360).toFloat()
    val rectHeight = (if (digitBoolean(num, 2)) 80f else 10f) * s

    val circleTx = unit(num * 3L, 40 - 19, 1) * s
    val circleTy = unit(num * 3L, 40 - 19, 2) * s

    val lineTx = unit(num * 4L, 40 - 20, 1) * s
    val lineTy = unit(num * 4L, 40 - 20, 2) * s
    val lineRot = unit(num * 4L, 360).toFloat()
    val pivot = 40f * s

    return PreparedBoringAvatar { canvas ->
        canvas.drawRect(0f, 0f, size, size, bgPaint)
        canvas.withSave {
            translate(rectTx, rectTy)
            rotate(rectRot, pivot, pivot)
            drawRect(10f * s, 30f * s, 90f * s, 30f * s + rectHeight, rectPaint)
        }
        canvas.withSave {
            translate(circleTx, circleTy)
            drawCircle(pivot, pivot, 16f * s, circlePaint)
        }
        canvas.withSave {
            translate(lineTx, lineTy)
            rotate(lineRot, pivot, pivot)
            drawLine(0f, pivot, size, pivot, linePaint)
        }
    }
}

private fun prepareRing(seed: String, palette: BoringAvatarPalette, size: Float): PreparedBoringAvatar {
    val num = boringHashCode(seed).toLong()
    val s = size / RING_VIEWPORT
    val sh0 = paletteColor(num, palette)
    val sh1 = paletteColor(num + 1L, palette)
    val sh2 = paletteColor(num + 2L, palette)
    val sh3 = paletteColor(num + 3L, palette)
    val sh4 = paletteColor(num + 4L, palette)

    val p0 = boringFill(sh0)
    val p1 = boringFill(sh1)
    val p2 = boringFill(sh2)
    val p3 = boringFill(sh3)
    val p4 = boringFill(sh4)

    val half = 45f * s
    val outer = RectF(7f * s, 7f * s, 83f * s, 83f * s)
    val mid = RectF(13f * s, 13f * s, 77f * s, 77f * s)
    val inner = RectF(19f * s, 19f * s, 71f * s, 71f * s)

    return PreparedBoringAvatar { canvas ->
        canvas.drawRect(0f, 0f, size, half, p0)
        canvas.drawRect(0f, half, size, size, p1)
        canvas.drawArc(outer, 180f, 180f, true, p1)
        canvas.drawArc(outer, 0f, 180f, true, p2)
        canvas.drawArc(mid, 180f, 180f, true, p2)
        canvas.drawArc(mid, 0f, 180f, true, p3)
        canvas.drawArc(inner, 180f, 180f, true, p3)
        canvas.drawArc(inner, 0f, 180f, true, p0)
        canvas.drawCircle(half, half, 23f * s, p4)
    }
}

private fun prepareBeam(seed: String, palette: BoringAvatarPalette, size: Float): PreparedBoringAvatar {
    val num = boringHashCode(seed).toLong()
    val s = size / BEAM_VIEWPORT
    val wrapperColor = paletteColor(num, palette)
    val preTx = unit(num, 10, 1)
    val tx = if (preTx < 5) preTx + 4 else preTx
    val preTy = unit(num, 10, 2)
    val ty = if (preTy < 5) preTy + 4 else preTy
    val bgPaint = boringFill(paletteColor(num + 13L, palette))
    val wrapperPaint = boringFill(wrapperColor)
    val faceColor = contrastColor(wrapperColor)
    val faceFill = boringFill(faceColor)
    val faceStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = faceColor
        strokeWidth = 1.2f * s
        strokeCap = Paint.Cap.ROUND
    }

    val wrapRot = unit(num, 360).toFloat()
    val wrapScale = 1f + unit(num, 3) / 10f
    val wrapRadius = (if (digitBoolean(num, 1)) 36f else 6f) * s
    val eyeSpread = unit(num, 5).toFloat()
    val mouthSpread = unit(num, 3).toFloat()
    val isMouthOpen = digitBoolean(num, 2)
    val faceTx = (if (tx > 6) tx / 2f else unit(num, 8, 1).toFloat()) * s
    val faceTy = (if (ty > 6) ty / 2f else unit(num, 7, 2).toFloat()) * s
    val faceRot = unit(num, 10, 3).toFloat()
    val pivot = 18f * s

    val wrapperRect = RectF(0f, 0f, size, size)
    val leftEye = RectF((14f - eyeSpread) * s, 14f * s, (15.5f - eyeSpread) * s, 16f * s)
    val rightEye = RectF((20f + eyeSpread) * s, 14f * s, (21.5f + eyeSpread) * s, 16f * s)
    val openMouthPath = Path().apply {
        val y = (19f + mouthSpread) * s
        moveTo(15f * s, y)
        cubicTo(17f * s, y + 1f * s, 19f * s, y + 1f * s, 21f * s, y)
    }
    val closedMouthRect = RectF(13f * s, (19f + mouthSpread - 3.75f) * s, 23f * s, (19f + mouthSpread + 3.75f) * s)

    return PreparedBoringAvatar { canvas ->
        canvas.drawRect(0f, 0f, size, size, bgPaint)
        canvas.withSave {
            translate(tx * s, ty * s)
            rotate(wrapRot, pivot, pivot)
            scale(wrapScale, wrapScale, pivot, pivot)
            drawRoundRect(wrapperRect, wrapRadius, wrapRadius, wrapperPaint)
        }
        canvas.withSave {
            translate(faceTx, faceTy)
            rotate(faceRot, pivot, pivot)
            if (isMouthOpen) {
                drawPath(openMouthPath, faceStroke)
            } else {
                drawArc(closedMouthRect, 0f, 180f, true, faceFill)
            }
            drawRoundRect(leftEye, 1f * s, 1f * s, faceFill)
            drawRoundRect(rightEye, 1f * s, 1f * s, faceFill)
        }
    }
}

private fun prepareSunset(seed: String, palette: BoringAvatarPalette, size: Float): PreparedBoringAvatar {
    val num = boringHashCode(seed).toLong()
    val half = size / 2f
    val c0 = paletteColor(num, palette)
    val c1 = paletteColor(num + 1L, palette)
    val c2 = paletteColor(num + 2L, palette)
    val c3 = paletteColor(num + 3L, palette)

    val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(half, 0f, half, half, c0, c1, Shader.TileMode.CLAMP)
    }
    val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(half, half, half, size, c2, c3, Shader.TileMode.CLAMP)
    }

    return PreparedBoringAvatar { canvas ->
        canvas.drawRect(0f, 0f, size, half, topPaint)
        canvas.drawRect(0f, half, size, size, bottomPaint)
    }
}

private fun prepareMarble(seed: String, palette: BoringAvatarPalette, size: Float): PreparedBoringAvatar {
    val num = boringHashCode(seed).toLong()
    val s = size / MARBLE_VIEWPORT
    val pivot = 40f * s
    val blurRadius = (7f * s).coerceAtLeast(1f)

    val bgPaint = boringFill(paletteColor(num, palette))
    val layerOnePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = paletteColor(num + 1L, palette)
        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }
    val rawTwo = paletteColor(num + 2L, palette)
    val layerTwoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(MARBLE_OVERLAY_ALPHA, Color.red(rawTwo), Color.green(rawTwo), Color.blue(rawTwo))
        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }

    val pathOne = Path().apply {
        moveTo(32.414f * s, 59.35f * s)
        lineTo(50.376f * s, 70.5f * s)
        lineTo(72.5f * s, 70.5f * s)
        lineTo(72.5f * s, -0.5f * s)
        lineTo(33.728f * s, -0.5f * s)
        lineTo(26.5f * s, 13.381f * s)
        lineTo(45.557f * s, 40.461f * s)
        close()
    }
    val pathTwo = Path().apply {
        moveTo(22.216f * s, 24f * s)
        lineTo(0f, 46.75f * s)
        lineTo(14.108f * s, 84.879f * s)
        lineTo(78.44f * s, 84.88f * s)
        lineTo(78.44f * s, 0f)
        lineTo(22.216f * s, 0f)
        close()
    }

    val tx1 = unit(num * 2L, 8, 1) * s
    val ty1 = unit(num * 2L, 8, 2) * s
    val scale1 = 1.2f + unit(num * 2L, 4) / 10f
    val rot1 = unit(num * 2L, 360, 1).toFloat()

    val tx2 = unit(num * 3L, 8, 1) * s
    val ty2 = unit(num * 3L, 8, 2) * s
    val scale2 = 1.2f + unit(num * 3L, 4) / 10f
    val rot2 = unit(num * 3L, 360, 1).toFloat()

    return PreparedBoringAvatar { canvas ->
        canvas.drawRect(0f, 0f, size, size, bgPaint)
        canvas.withSave {
            translate(tx1, ty1)
            rotate(rot1, pivot, pivot)
            scale(scale1, scale1, pivot, pivot)
            drawPath(pathOne, layerOnePaint)
        }
        canvas.withSave {
            translate(tx2, ty2)
            rotate(rot2, pivot, pivot)
            scale(scale2, scale2, pivot, pivot)
            drawPath(pathTwo, layerTwoPaint)
        }
    }
}

private fun boringFill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
}

private fun digit(number: Long, position: Int): Int = ((number / 10.0.pow(position).toLong()) % 10L).toInt()

private fun digitBoolean(number: Long, position: Int): Boolean = digit(number, position) % 2 == 0

private fun unit(number: Long, range: Int, index: Int? = null): Int {
    val value = Math.floorMod(number, range.toLong()).toInt()
    return if (index != null && digit(number, index) % 2 == 0) -value else value
}

private fun paletteColor(number: Long, palette: BoringAvatarPalette): Int = palette.colorAt(Math.floorMod(number, palette.colorCount.toLong()).toInt())

private fun contrastColor(color: Int): Int {
    val yiq = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
    return if (yiq >= 128) CONTRAST_DARK else CONTRAST_LIGHT
}
