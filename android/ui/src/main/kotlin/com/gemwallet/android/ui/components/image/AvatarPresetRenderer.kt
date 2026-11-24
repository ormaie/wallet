package com.gemwallet.android.ui.components.image

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
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

    AvatarPreset.SceneFujiDawn -> prepareGeometric(size) { s ->
        val sky = verticalGradientPaint(size, 0xFFFFE4E6.toInt(), 0xFFFDE68A.toInt())
        val sun = fillPaint(0xFFF43F5E.toInt())
        val mountain = fillPaint(0xFF1E3A8A.toInt())
        val snow = fillPaint(0xFFF8FAFC.toInt())
        val water = fillPaint(0xFF172554.toInt())
        val peakPath = Path().apply {
            moveToScaled(-4f, 64f, s)
            lineToScaled(26f, 24f, s)
            lineToScaled(38f, 24f, s)
            lineToScaled(68f, 64f, s)
            close()
        }
        val snowPath = Path().apply {
            moveToScaled(26f, 24f, s)
            lineToScaled(38f, 24f, s)
            lineToScaled(43f, 32f, s)
            lineToScaled(36f, 30f, s)
            lineToScaled(32f, 34f, s)
            lineToScaled(28f, 30f, s)
            lineToScaled(21f, 32f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(46f * s, 18f * s, 8f * s, sun)
            canvas.drawPath(peakPath, mountain)
            canvas.drawPath(snowPath, snow)
            canvas.drawRect(0f, 52f * s, size, size, water)
        }
    }

    AvatarPreset.SceneAuroraPines -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFF0B132B.toInt())
        val auroraTeal = fillPaint(0x8C2DD4BF.toInt())
        val auroraViolet = fillPaint(0x73A78BFA.toInt())
        val star = fillPaint(0xFFFFFFFF.toInt())
        val ridge = fillPaint(0xFF0F172A.toInt())
        val pine = fillPaint(0xFF064E3B.toInt())
        val ribbonOne = Path().apply {
            moveToScaled(0f, 24f, s)
            quadToScaled(20f, 8f, 40f, 22f, s)
            quadToScaled(52f, 28f, 64f, 14f, s)
            lineToScaled(64f, 36f, s)
            quadToScaled(42f, 42f, 22f, 30f, s)
            quadToScaled(10f, 24f, 0f, 40f, s)
            close()
        }
        val ribbonTwo = Path().apply {
            moveToScaled(0f, 18f, s)
            quadToScaled(24f, 28f, 46f, 12f, s)
            quadToScaled(56f, 8f, 64f, 20f, s)
            lineToScaled(64f, 30f, s)
            quadToScaled(40f, 20f, 20f, 34f, s)
            quadToScaled(8f, 38f, 0f, 28f, s)
            close()
        }
        val mountainPath = Path().apply {
            moveToScaled(0f, 64f, s)
            lineToScaled(0f, 44f, s)
            lineToScaled(14f, 34f, s)
            lineToScaled(26f, 46f, s)
            lineToScaled(42f, 36f, s)
            lineToScaled(64f, 48f, s)
            lineToScaled(64f, 64f, s)
            close()
        }
        val pineLeft = Path().apply {
            moveToScaled(20f, 56f, s)
            lineToScaled(26f, 38f, s)
            lineToScaled(32f, 56f, s)
            close()
        }
        val pineRight = Path().apply {
            moveToScaled(44f, 58f, s)
            lineToScaled(49f, 42f, s)
            lineToScaled(54f, 58f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawPath(ribbonOne, auroraTeal)
            canvas.drawPath(ribbonTwo, auroraViolet)
            canvas.drawCircle(16f * s, 13f * s, 1.5f * s, star)
            canvas.drawCircle(50f * s, 10f * s, 1.2f * s, star)
            canvas.drawPath(mountainPath, ridge)
            canvas.drawPath(pineLeft, pine)
            canvas.drawPath(pineRight, pine)
        }
    }

    AvatarPreset.SceneSaharaDunes -> prepareGeometric(size) { s ->
        val sky = verticalGradientPaint(size, 0xFFFDE68A.toInt(), 0xFFFB923C.toInt())
        val sun = fillPaint(0xFFFFFBEB.toInt())
        val midDune = fillPaint(0xFFEA580C.toInt())
        val frontDune = fillPaint(0xFF9A3412.toInt())
        val backPath = Path().apply {
            moveToScaled(0f, 44f, s)
            quadToScaled(18f, 34f, 38f, 44f, s)
            quadToScaled(52f, 50f, 64f, 40f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        val frontPath = Path().apply {
            moveToScaled(0f, 52f, s)
            quadToScaled(26f, 42f, 52f, 54f, s)
            quadToScaled(58f, 56f, 64f, 52f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(32f * s, 30f * s, 13f * s, sun)
            canvas.drawPath(backPath, midDune)
            canvas.drawPath(frontPath, frontDune)
        }
    }

    AvatarPreset.SceneCrescentTide -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFF0F172A.toInt())
        val moon = fillPaint(0xFFFEF08A.toInt())
        val star = fillPaint(0xFF93C5FD.toInt())
        val deepSea = fillPaint(0xFF1D4ED8.toInt())
        val frontSea = fillPaint(0xB338BDF8.toInt())
        val backWave = Path().apply {
            moveToScaled(0f, 44f, s)
            quadToScaled(16f, 38f, 32f, 44f, s)
            quadToScaled(48f, 50f, 64f, 44f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        val frontWave = Path().apply {
            moveToScaled(0f, 52f, s)
            quadToScaled(16f, 47f, 32f, 52f, s)
            quadToScaled(48f, 57f, 64f, 52f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(34f * s, 22f * s, 11f * s, moon)
            canvas.drawCircle(39f * s, 19f * s, 10f * s, sky)
            canvas.drawCircle(16f * s, 16f * s, 1.5f * s, star)
            canvas.drawCircle(50f * s, 28f * s, 1.2f * s, star)
            canvas.drawPath(backWave, deepSea)
            canvas.drawPath(frontWave, frontSea)
        }
    }

    AvatarPreset.SceneSaturnOrbit -> prepareGeometric(size) { s ->
        val space = fillPaint(0xFF1E1B4B.toInt())
        val starGold = fillPaint(0xFFFDE047.toInt())
        val starWhite = fillPaint(0xFFFFFFFF.toInt())
        val starLilac = fillPaint(0xFFC4B5FD.toInt())
        val backRing = strokePaint(0xFFF472B6.toInt(), 4.5f * s)
        val frontRing = strokePaint(0xFFFBCFE8.toInt(), 3.6f * s, Paint.Cap.ROUND)
        val planet = fillPaint(0xFFF59E0B.toInt())
        val planetShadow = fillPaint(0x597C3AED.toInt())
        val ringRect = RectF(4f * s, 26f * s, 60f * s, 42f * s)
        val planetClip = Path().apply { addCircle(32f * s, 34f * s, 13f * s, Path.Direction.CW) }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, space)
            canvas.drawCircle(14f * s, 16f * s, 1.5f * s, starGold)
            canvas.drawCircle(52f * s, 14f * s, 1.2f * s, starWhite)
            canvas.drawCircle(50f * s, 50f * s, 1.5f * s, starLilac)
            canvas.withSave {
                rotate(-22f, 32f * s, 34f * s)
                drawOval(ringRect, backRing)
            }
            canvas.drawCircle(32f * s, 34f * s, 13f * s, planet)
            canvas.withSave {
                clipPath(planetClip)
                drawCircle(26f * s, 41f * s, 14f * s, planetShadow)
            }
            canvas.withSave {
                rotate(-22f, 32f * s, 34f * s)
                drawArc(ringRect, 0f, 180f, false, frontRing)
            }
        }
    }

    AvatarPreset.SceneCrimsonMoon -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFF18181B.toInt())
        val moon = fillPaint(0xFFEF4444.toInt())
        val mountain = fillPaint(0xFF27272A.toInt())
        val shadow = fillPaint(0xFF09090B.toInt())
        val ridgePath = Path().apply {
            moveToScaled(-2f, 64f, s)
            lineToScaled(22f, 32f, s)
            lineToScaled(40f, 50f, s)
            lineToScaled(50f, 38f, s)
            lineToScaled(66f, 64f, s)
            close()
        }
        val shadowPath = Path().apply {
            moveToScaled(-2f, 64f, s)
            lineToScaled(22f, 32f, s)
            lineToScaled(34f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(32f * s, 26f * s, 16f * s, moon)
            canvas.drawPath(ridgePath, mountain)
            canvas.drawPath(shadowPath, shadow)
        }
    }

    AvatarPreset.SceneCloudSunrise -> prepareGeometric(size) { s ->
        val sky = verticalGradientPaint(size, 0xFF38BDF8.toInt(), 0xFFFDE68A.toInt())
        val sun = fillPaint(0xFFF59E0B.toInt())
        val cloud = fillPaint(0xFFFFFFFF.toInt())
        val mist = fillPaint(0xFFE0F2FE.toInt())
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(32f * s, 36f * s, 14f * s, sun)
            canvas.drawCircle(14f * s, 48f * s, 14f * s, cloud)
            canvas.drawCircle(34f * s, 52f * s, 16f * s, cloud)
            canvas.drawCircle(54f * s, 46f * s, 14f * s, cloud)
            canvas.drawCircle(24f * s, 56f * s, 12f * s, mist)
        }
    }

    AvatarPreset.SceneCanyonArch -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFFFFEDD5.toInt())
        val sun = fillPaint(0xFFFACC15.toInt())
        val leftRock = fillPaint(0xFFC2410C.toInt())
        val rightRock = fillPaint(0xFF9A3412.toInt())
        val river = fillPaint(0xFF38BDF8.toInt())
        val leftPath = Path().apply {
            moveToScaled(0f, 24f, s)
            lineToScaled(18f, 28f, s)
            lineToScaled(26f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        val rightPath = Path().apply {
            moveToScaled(64f, 20f, s)
            lineToScaled(44f, 26f, s)
            lineToScaled(38f, 64f, s)
            lineToScaled(64f, 64f, s)
            close()
        }
        val riverPath = Path().apply {
            moveToScaled(20f, 64f, s)
            quadToScaled(32f, 48f, 44f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(44f * s, 18f * s, 7f * s, sun)
            canvas.drawPath(leftPath, leftRock)
            canvas.drawPath(rightPath, rightRock)
            canvas.drawPath(riverPath, river)
        }
    }

    AvatarPreset.SceneGlacierPeak -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFFE0F2FE.toInt())
        val peakDark = fillPaint(0xFF0284C7.toInt())
        val peakLight = fillPaint(0xFF7DD3FC.toInt())
        val peakSide = fillPaint(0xFF0369A1.toInt())
        val water = fillPaint(0xFF0C4A6E.toInt())
        val reflection = fillPaint(0x7338BDF8.toInt())
        val shimmer = strokePaint(0x66E0F2FE.toInt(), 1.5f * s, Paint.Cap.ROUND)
        val mainPeak = Path().apply {
            moveToScaled(10f, 40f, s)
            lineToScaled(28f, 14f, s)
            lineToScaled(44f, 40f, s)
            close()
        }
        val litFace = Path().apply {
            moveToScaled(28f, 14f, s)
            lineToScaled(34f, 40f, s)
            lineToScaled(44f, 40f, s)
            close()
        }
        val sidePeak = Path().apply {
            moveToScaled(34f, 40f, s)
            lineToScaled(48f, 22f, s)
            lineToScaled(60f, 40f, s)
            close()
        }
        val reflectPath = Path().apply {
            moveToScaled(10f, 40f, s)
            lineToScaled(28f, 60f, s)
            lineToScaled(44f, 40f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawPath(mainPeak, peakDark)
            canvas.drawPath(litFace, peakLight)
            canvas.drawPath(sidePeak, peakSide)
            canvas.drawRect(0f, 40f * s, size, size, water)
            canvas.drawPath(reflectPath, reflection)
            canvas.drawLine(12f * s, 48f * s, 52f * s, 48f * s, shimmer)
        }
    }

    AvatarPreset.SceneLagoonIsle -> prepareGeometric(size) { s ->
        val sky = fillPaint(0xFFCCFBF1.toInt())
        val sun = fillPaint(0xFFFDE047.toInt())
        val lagoon = fillPaint(0xFF0D9488.toInt())
        val sand = fillPaint(0xFFF59E0B.toInt())
        val trunk = strokePaint(0xFF78350F.toInt(), 3.2f * s, Paint.Cap.ROUND)
        val leafDark = strokePaint(0xFF059669.toInt(), 3f * s, Paint.Cap.ROUND)
        val leafBright = strokePaint(0xFF10B981.toInt(), 2.8f * s, Paint.Cap.ROUND)
        val sandPath = Path().apply {
            moveToScaled(14f, 46f, s)
            quadToScaled(32f, 34f, 50f, 46f, s)
            close()
        }
        val trunkPath = Path().apply {
            moveToScaled(32f, 42f, s)
            quadToScaled(30f, 28f, 24f, 22f, s)
        }
        val frondLeft = Path().apply {
            moveToScaled(24f, 22f, s)
            quadToScaled(16f, 18f, 12f, 24f, s)
        }
        val frondTop = Path().apply {
            moveToScaled(24f, 22f, s)
            quadToScaled(26f, 14f, 34f, 16f, s)
        }
        val frondRight = Path().apply {
            moveToScaled(24f, 22f, s)
            quadToScaled(32f, 22f, 36f, 28f, s)
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(48f * s, 16f * s, 7f * s, sun)
            canvas.drawRect(0f, 44f * s, size, size, lagoon)
            canvas.drawPath(sandPath, sand)
            canvas.drawPath(trunkPath, trunk)
            canvas.drawPath(frondLeft, leafDark)
            canvas.drawPath(frondTop, leafDark)
            canvas.drawPath(frondRight, leafBright)
        }
    }

    AvatarPreset.SceneStarlightLighthouse -> prepareGeometric(size) { s ->
        val night = fillPaint(0xFF1E1B4B.toInt())
        val beam = fillPaint(0x52FEF08A.toInt())
        val tower = fillPaint(0xFFF8FAFC.toInt())
        val stripe = fillPaint(0xFFEF4444.toInt())
        val beacon = fillPaint(0xFFFDE047.toInt())
        val sea = fillPaint(0xFF312E81.toInt())
        val beamPath = Path().apply {
            moveToScaled(32f, 20f, s)
            lineToScaled(64f, 8f, s)
            lineToScaled(64f, 30f, s)
            close()
        }
        val towerPath = Path().apply {
            moveToScaled(27f, 54f, s)
            lineToScaled(30f, 22f, s)
            lineToScaled(34f, 22f, s)
            lineToScaled(37f, 54f, s)
            close()
        }
        val seaPath = Path().apply {
            moveToScaled(0f, 54f, s)
            quadToScaled(32f, 48f, 64f, 54f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, night)
            canvas.drawPath(beamPath, beam)
            canvas.drawPath(towerPath, tower)
            canvas.drawRect(28f * s, 30f * s, 36f * s, 36f * s, stripe)
            canvas.drawRect(27.5f * s, 42f * s, 36.5f * s, 48f * s, stripe)
            canvas.drawCircle(32f * s, 20f * s, 4f * s, beacon)
            canvas.drawPath(seaPath, sea)
        }
    }

    AvatarPreset.SceneSakuraMist -> prepareGeometric(size) { s ->
        val sky = verticalGradientPaint(size, 0xFFFCE7F3.toInt(), 0xFFF9A8D4.toInt())
        val moon = fillPaint(0xFFFFF1F2.toInt())
        val midHill = fillPaint(0xA6DB2777.toInt())
        val frontHill = fillPaint(0xFF831843.toInt())
        val petalPink = fillPaint(0xFFEC4899.toInt())
        val petalWhite = fillPaint(0xFFFFFFFF.toInt())
        val midPath = Path().apply {
            moveToScaled(0f, 46f, s)
            quadToScaled(20f, 30f, 40f, 44f, s)
            quadToScaled(52f, 50f, 64f, 38f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        val frontPath = Path().apply {
            moveToScaled(0f, 54f, s)
            quadToScaled(28f, 42f, 64f, 52f, s)
            lineToScaled(64f, 64f, s)
            lineToScaled(0f, 64f, s)
            close()
        }
        PreparedAvatarPreset { canvas ->
            canvas.drawRect(0f, 0f, size, size, sky)
            canvas.drawCircle(20f * s, 20f * s, 8f * s, moon)
            canvas.drawPath(midPath, midHill)
            canvas.drawPath(frontPath, frontHill)
            canvas.drawCircle(48f * s, 18f * s, 2.2f * s, petalPink)
            canvas.drawCircle(42f * s, 26f * s, 1.8f * s, petalWhite)
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

private fun verticalGradientPaint(size: Float, topColor: Int, bottomColor: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    shader = LinearGradient(0f, 0f, 0f, size, topColor, bottomColor, Shader.TileMode.CLAMP)
}

private fun strokePaint(color: Int, width: Float, cap: Paint.Cap = Paint.Cap.BUTT): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    this.color = color
    strokeWidth = width
    strokeCap = cap
}

private fun Path.moveToScaled(x: Float, y: Float, scale: Float) = moveTo(x * scale, y * scale)

private fun Path.lineToScaled(x: Float, y: Float, scale: Float) = lineTo(x * scale, y * scale)

private fun Path.quadToScaled(x1: Float, y1: Float, x2: Float, y2: Float, scale: Float) = quadTo(x1 * scale, y1 * scale, x2 * scale, y2 * scale)
