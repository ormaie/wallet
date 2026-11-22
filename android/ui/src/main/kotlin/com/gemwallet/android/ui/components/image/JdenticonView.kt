package com.gemwallet.android.ui.components.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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

private class CachedJdenticonLayer(val path: Path, val color: Color)

@Composable
fun JdenticonView(jdenticon: GemJdenticon, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val press = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Spacer(
        modifier = modifier
            .clip(CircleShape)
            .then(press)
            .drawWithCache {
                if (jdenticon.size <= 0.0) {
                    return@drawWithCache onDrawBehind {}
                }
                val dimension = min(size.width, size.height)
                val originX = (size.width - dimension) / 2f
                val originY = (size.height - dimension) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = dimension / 2f
                val scale = (dimension / jdenticon.size).toFloat()
                val borderStrokeWidth = (jdenticon.borderWidth.toFloat() * scale).coerceAtLeast(0f)
                val backgroundColor = jdenticon.background.toComposeColor()
                val borderColor = jdenticon.border.toComposeColor()
                val borderStroke = if (borderStrokeWidth > 0f && outerRadius > borderStrokeWidth / 2f) {
                    Stroke(width = borderStrokeWidth)
                } else {
                    null
                }
                val borderRadius = outerRadius - borderStrokeWidth / 2f
                val cachedLayers = ArrayList<CachedJdenticonLayer>(jdenticon.layers.size)
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
                    cachedLayers.add(CachedJdenticonLayer(path = path, color = layer.color.toComposeColor()))
                }
                onDrawBehind {
                    drawCircle(color = backgroundColor, radius = outerRadius, center = center)
                    for (i in cachedLayers.indices) {
                        val layer = cachedLayers[i]
                        drawPath(path = layer.path, color = layer.color)
                    }
                    if (borderStroke != null) {
                        drawCircle(
                            color = borderColor,
                            radius = borderRadius,
                            center = center,
                            style = borderStroke,
                        )
                    }
                }
            },
    )
}

private fun GemJdenticonColor.toComposeColor(): Color = Color(
    red = red.toInt(),
    green = green.toInt(),
    blue = blue.toInt(),
)
