package com.gemwallet.android.ui.components.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import uniffi.gemstone.GemJdenticon
import uniffi.gemstone.GemJdenticonColor
import uniffi.gemstone.GemJdenticonSubpath
import kotlin.math.min

@Composable
fun JdenticonView(jdenticon: GemJdenticon, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val press = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .then(press),
    ) {
        if (jdenticon.size <= 0.0) return@Canvas
        val dimension = min(size.width, size.height)
        val originX = (size.width - dimension) / 2f
        val originY = (size.height - dimension) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = dimension / 2f
        val scale = (dimension / jdenticon.size).toFloat()
        val borderStroke = (jdenticon.borderWidth.toFloat() * scale).coerceAtLeast(0f)

        drawCircle(color = jdenticon.background.toComposeColor(), radius = outerRadius, center = center)

        for (layer in jdenticon.layers) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                for (subpath in layer.subpaths) {
                    when (subpath) {
                        is GemJdenticonSubpath.Polygon -> {
                            val first = subpath.points.firstOrNull() ?: continue
                            moveTo(originX + first.x.toFloat() * scale, originY + first.y.toFloat() * scale)
                            for (index in 1 until subpath.points.size) {
                                val point = subpath.points[index]
                                lineTo(originX + point.x.toFloat() * scale, originY + point.y.toFloat() * scale)
                            }
                            close()
                        }

                        is GemJdenticonSubpath.Oval -> {
                            val left = originX + subpath.x.toFloat() * scale
                            val top = originY + subpath.y.toFloat() * scale
                            val span = subpath.diameter.toFloat() * scale
                            addOval(Rect(left = left, top = top, right = left + span, bottom = top + span))
                        }
                    }
                }
            }
            drawPath(path = path, color = layer.color.toComposeColor())
        }

        if (borderStroke > 0f && outerRadius > borderStroke / 2f) {
            drawCircle(
                color = jdenticon.border.toComposeColor(),
                radius = outerRadius - borderStroke / 2f,
                center = center,
                style = Stroke(width = borderStroke),
            )
        }
    }
}

private fun GemJdenticonColor.toComposeColor(): Color = Color(
    red = red.toInt(),
    green = green.toInt(),
    blue = blue.toInt(),
)
