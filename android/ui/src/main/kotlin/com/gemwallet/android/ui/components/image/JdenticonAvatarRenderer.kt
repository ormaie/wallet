package com.gemwallet.android.ui.components.image

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.gemwallet.android.ui.theme.extraLargeIconSize
import uniffi.gemstone.GemJdenticon
import uniffi.gemstone.GemJdenticonColor
import uniffi.gemstone.GemJdenticonSubpath
import kotlin.math.roundToInt

object JdenticonAvatarRenderer {

    fun render(context: Context, jdenticon: GemJdenticon): ByteArray {
        val size = (extraLargeIconSize.value * context.resources.displayMetrics.density).roundToInt()
        val outputBitmap = createBitmap(size, size)
        if (jdenticon.size <= 0.0) {
            return outputBitmap.toPng()
        }

        val contentBitmap = createBitmap(size, size)
        val contentCanvas = Canvas(contentBitmap)
        val bounds = size.toFloat()
        val scale = (bounds / jdenticon.size).toFloat()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = jdenticon.background.toArgb()
        }
        contentCanvas.drawRect(0f, 0f, bounds, bounds, fillPaint)

        for (layer in jdenticon.layers) {
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                for (subpath in layer.subpaths) {
                    when (subpath) {
                        is GemJdenticonSubpath.Polygon -> {
                            val first = subpath.points.firstOrNull() ?: continue
                            moveTo(first.x.toFloat() * scale, first.y.toFloat() * scale)
                            for (index in 1 until subpath.points.size) {
                                val point = subpath.points[index]
                                lineTo(point.x.toFloat() * scale, point.y.toFloat() * scale)
                            }
                            close()
                        }

                        is GemJdenticonSubpath.Oval -> {
                            val left = subpath.x.toFloat() * scale
                            val top = subpath.y.toFloat() * scale
                            val span = subpath.diameter.toFloat() * scale
                            addOval(left, top, left + span, top + span, Path.Direction.CW)
                        }
                    }
                }
            }
            fillPaint.color = layer.color.toArgb()
            contentCanvas.drawPath(path, fillPaint)
        }

        val outputCanvas = Canvas(outputBitmap)
        val center = bounds / 2f
        val borderStroke = (jdenticon.borderWidth.toFloat() * scale).coerceAtLeast(0f)
        val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(contentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        if (borderStroke > 0f && center > borderStroke) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = jdenticon.border.toArgb()
            }
            outputCanvas.drawCircle(center, center, center, borderPaint)
            outputCanvas.drawCircle(center, center, center - borderStroke, shaderPaint)
        } else {
            outputCanvas.drawCircle(center, center, center, shaderPaint)
        }

        return outputBitmap.toPng()
    }

    private fun GemJdenticonColor.toArgb(): Int = Color.rgb(red.toInt(), green.toInt(), blue.toInt())
}
