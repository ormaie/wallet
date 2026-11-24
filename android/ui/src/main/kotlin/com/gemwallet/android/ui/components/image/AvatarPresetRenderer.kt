package com.gemwallet.android.ui.components.image

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.gemwallet.android.ui.theme.extraLargeIconSize
import kotlin.math.roundToInt

private const val VIEWPORT_SIZE = 64f
private const val MIN_RENDER_SIZE_PX = 256

object AvatarPresetRenderer {

    fun render(context: Context, preset: AvatarPreset): ByteArray {
        val size = (extraLargeIconSize.value * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(MIN_RENDER_SIZE_PX)
        val contentBitmap = createBitmap(size, size)
        val outputBitmap = createBitmap(size, size)
        return try {
            val drawer = prepareAvatarPreset(preset, size.toFloat())
            drawer.draw(Canvas(contentBitmap))

            val outputCanvas = Canvas(outputBitmap)
            val center = size / 2f
            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = BitmapShader(contentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            outputCanvas.drawCircle(center, center, center, shaderPaint)
            outputBitmap.toPng().withAvatarSelectionMetadata(StoredAvatarSelection.Preset(preset))
        } finally {
            contentBitmap.recycle()
            outputBitmap.recycle()
        }
    }
}

internal fun interface PreparedAvatarPreset {
    fun draw(canvas: Canvas)
}

internal fun prepareAvatarPreset(preset: AvatarPreset, size: Float): PreparedAvatarPreset = when (preset) {
    AvatarPreset.GeoCobaltSplit -> prepareGeometric(size) { s ->
        val navy = fillPaint(0xFF1E3A8A.toInt())
        val cobalt = fillPaint(0xFF2D5BE6.toInt())
        val sky = fillPaint(0xFF93C5FD.toInt())
        val ice = fillPaint(0xFFF8FAFC.toInt())
        val rightHalf = RectF(10f * s, 10f * s, 54f * s, 54f * s)
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, navy)
            canvas.drawRect(0f, 0f, 32f * s, size, cobalt)
            canvas.drawCircle(32f * s, 32f * s, 22f * s, sky)
            canvas.drawArc(rightHalf, -90f, 180f, true, ice)
            canvas.drawCircle(32f * s, 32f * s, 9f * s, navy)
        }
    }

    AvatarPreset.GeoSunsetSlice -> prepareGeometric(size) { s ->
        val cream = fillPaint(0xFFFFF1E6.toInt())
        val orange = fillPaint(0xFFF97316.toInt())
        val rust = fillPaint(0xFFEA580C.toInt())
        val sun = fillPaint(0xFFFACC15.toInt())
        val slate = fillPaint(0xFF1E293B.toInt())
        val topHalf = RectF(12f * s, 12f * s, 52f * s, 52f * s)
        val bar = RectF(28f * s, 28f * s, 52f * s, 36f * s)
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, cream)
            canvas.drawRect(0f, 32f * s, size, size, orange)
            canvas.drawCircle(32f * s, 32f * s, 20f * s, rust)
            canvas.drawArc(topHalf, 180f, 180f, true, sun)
            canvas.drawRoundRect(bar, 4f * s, 4f * s, slate)
        }
    }

    AvatarPreset.GeoEmeraldDiamond -> prepareGeometric(size) { s ->
        val forest = fillPaint(0xFF064E3B.toInt())
        val emerald = fillPaint(0xFF10B981.toInt())
        val mint = fillPaint(0xFFA7F3D0.toInt())
        val lemon = fillPaint(0xEBFEF08A.toInt())
        val diamond = Path().apply {
            moveToScaled(32f, 14f, s)
            lineToScaled(48f, 32f, s)
            lineToScaled(32f, 50f, s)
            lineToScaled(16f, 32f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, forest)
            canvas.drawCircle(44f * s, 22f * s, 26f * s, emerald)
            canvas.drawCircle(20f * s, 44f * s, 22f * s, mint)
            canvas.drawPath(diamond, lemon)
        }
    }

    AvatarPreset.GeoVioletPrism -> prepareGeometric(size) { s ->
        val plum = fillPaint(0xFF3B0764.toInt())
        val violet = fillPaint(0xFF7C3AED.toInt())
        val pink = fillPaint(0xFFF472B6.toInt())
        val gold = fillPaint(0xFFFDE047.toInt())
        val wedge = Path().apply {
            moveToScaled(0f, 0f, s)
            lineToScaled(64f, 0f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, plum)
            canvas.drawPath(wedge, violet)
            canvas.drawCircle(42f * s, 42f * s, 18f * s, pink)
            canvas.drawCircle(22f * s, 22f * s, 10f * s, gold)
        }
    }

    AvatarPreset.GeoNordicArcs -> prepareGeometric(size) { s ->
        val ice = fillPaint(0xFFE0F2FE.toInt())
        val sky = fillPaint(0xFF0284C7.toInt())
        val deep = fillPaint(0xFF0369A1.toInt())
        val coral = fillPaint(0xFFFB7185.toInt())
        val amber = fillPaint(0xFFFBBF24.toInt())
        val topLeftArc = RectF(0f, -32f * s, 64f * s, 32f * s)
        val bottomRightArc = RectF(0f, 32f * s, 64f * s, 96f * s)
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, ice)
            canvas.drawArc(topLeftArc, 90f, 90f, true, sky)
            canvas.drawArc(bottomRightArc, 270f, 90f, true, deep)
            canvas.drawCircle(46f * s, 18f * s, 10f * s, coral)
            canvas.drawCircle(18f * s, 46f * s, 10f * s, amber)
        }
    }

    AvatarPreset.GeoCoralOverlap -> prepareGeometric(size) { s ->
        val blush = fillPaint(0xFFFFE4E6.toInt())
        val rose = fillPaint(0xFFF43F5E.toInt())
        val slate = fillPaint(0xE01E293B.toInt())
        val gold = fillPaint(0xFFFDE047.toInt())
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, blush)
            canvas.drawCircle(22f * s, 32f * s, 20f * s, rose)
            canvas.drawCircle(42f * s, 32f * s, 20f * s, slate)
            canvas.drawCircle(32f * s, 32f * s, 9f * s, gold)
        }
    }

    AvatarPreset.GeoTealChecker -> prepareGeometric(size) { s ->
        val teal = fillPaint(0xFF0F766E.toInt())
        val aqua = fillPaint(0xFF2DD4BF.toInt())
        val pale = fillPaint(0xFFCCFBF1.toInt())
        val dark = fillPaint(0xFF134E4A.toInt())
        val amber = fillPaint(0xFFF59E0B.toInt())
        val half = 32f * s
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, half, half, teal)
            canvas.drawRect(half, 0f, size, half, aqua)
            canvas.drawRect(0f, half, half, size, pale)
            canvas.drawRect(half, half, size, size, dark)
            canvas.drawCircle(half, half, 14f * s, amber)
        }
    }

    AvatarPreset.GeoOrbitRings -> prepareGeometric(size) { s ->
        val indigoBg = fillPaint(0xFF1E1B4B.toInt())
        val outerRing = strokePaint(0xFF6366F1.toInt(), 8f * s)
        val innerRing = strokePaint(0xFF38BDF8.toInt(), 6f * s)
        val star = fillPaint(0xFFF8FAFC.toInt())
        val satellite = fillPaint(0xFFF472B6.toInt())
        val center = 32f * s
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, indigoBg)
            canvas.drawCircle(center, center, 24f * s, outerRing)
            canvas.drawCircle(center, center, 13f * s, innerRing)
            canvas.drawCircle(center, center, 5f * s, star)
            canvas.drawCircle(49f * s, 18f * s, 5f * s, satellite)
        }
    }

    AvatarPreset.GeoSandArch -> prepareGeometric(size) { s ->
        val sand = fillPaint(0xFFFEF3C7.toInt())
        val amber = fillPaint(0xFFD97706.toInt())
        val slate = fillPaint(0xFF1E293B.toInt())
        val ivory = fillPaint(0xFFFFFBEB.toInt())
        val outerArch = Path().apply {
            moveToScaled(14f, 64f, s)
            lineToScaled(14f, 30f, s)
            arcTo(RectF(14f * s, 12f * s, 50f * s, 48f * s), 180f, 180f, false)
            lineToScaled(50f, 64f, s)
            close()
        }
        val innerArch = Path().apply {
            moveToScaled(23f, 64f, s)
            lineToScaled(23f, 34f, s)
            arcTo(RectF(23f * s, 25f * s, 41f * s, 43f * s), 180f, 180f, false)
            lineToScaled(41f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sand)
            canvas.drawPath(outerArch, amber)
            canvas.drawPath(innerArch, slate)
            canvas.drawCircle(32f * s, 18.5f * s, 4.2f * s, ivory)
        }
    }

    AvatarPreset.GeoMintClover -> prepareGeometric(size) { s ->
        val mint = fillPaint(0xFFECFDF5.toInt())
        val light = fillPaint(0xFF34D399.toInt())
        val green = fillPaint(0xFF059669.toInt())
        val blue = fillPaint(0xD92563EB.toInt())
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, mint)
            canvas.drawCircle(32f * s, 14f * s, 18f * s, light)
            canvas.drawCircle(16f * s, 44f * s, 18f * s, green)
            canvas.drawCircle(48f * s, 44f * s, 18f * s, blue)
        }
    }

    AvatarPreset.GeoBerryCut -> prepareGeometric(size) { s ->
        val berry = fillPaint(0xFF831843.toInt())
        val magenta = fillPaint(0xFFEC4899.toInt())
        val blush = fillPaint(0xFFFBCFE8.toInt())
        val lemon = fillPaint(0xFFFEF08A.toInt())
        val bottomWedge = Path().apply {
            moveToScaled(0f, 64f, s)
            lineToScaled(64f, 12f, s)
            lineToScaled(64f, 64f, s)
            close()
        }
        val topWedge = Path().apply {
            moveToScaled(0f, 0f, s)
            lineToScaled(44f, 0f, s)
            lineToScaled(0f, 44f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, berry)
            canvas.drawPath(bottomWedge, magenta)
            canvas.drawPath(topWedge, blush)
            canvas.drawCircle(40f * s, 42f * s, 9f * s, lemon)
        }
    }

    AvatarPreset.GeoMonochrome -> prepareGeometric(size) { s ->
        val light = fillPaint(0xFFF1F5F9.toInt())
        val ink = fillPaint(0xFF0F172A.toInt())
        val cobalt = fillPaint(0xFF2D5BE6.toInt())
        val white = fillPaint(0xFFFFFFFF.toInt())
        val half = 32f * s
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, light)
            canvas.drawRect(half, 0f, size, size, ink)
            canvas.drawCircle(half, 20f * s, 12f * s, cobalt)
            canvas.drawCircle(half, 44f * s, 12f * s, white)
            canvas.drawCircle(half, 44f * s, 5f * s, ink)
        }
    }
}

private inline fun prepareGeometric(
    size: Float,
    build: (scale: Float) -> PreparedAvatarPreset,
): PreparedAvatarPreset = build(size / VIEWPORT_SIZE)

private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
}

private fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    this.color = color
    strokeWidth = width
}

private fun Path.moveToScaled(x: Float, y: Float, scale: Float) = moveTo(x * scale, y * scale)

private fun Path.lineToScaled(x: Float, y: Float, scale: Float) = lineTo(x * scale, y * scale)
