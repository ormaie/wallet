package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.theme.captionFontSize
import com.gemwallet.android.ui.theme.space0
import com.gemwallet.android.ui.theme.space1
import com.gemwallet.android.ui.theme.space24
import com.gemwallet.android.ui.theme.space4
import com.gemwallet.android.ui.theme.space6
import com.gemwallet.android.ui.theme.space8
import uniffi.gemstone.GemChartBounds
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ChartPoint(val x: Float, val y: Float)

private object Metrics {
    val lineWidth = 2.5.dp
    val selectionDotRadius = space6
    val dashLength = space4
    val labelWidth = 88.dp
    val verticalPadding = space24
    val horizontalPadding = space0
    val labelEdgePadding = space8
    val boundLabelOffsetBelow = space6
    val boundLabelOffsetAbove = 18.dp
    val selectionGlowExtra = space6
    val pulsingDotSize = space8
}

private object Alpha {
    const val SELECTION_LINE = 0.50f
    const val GLOW_CENTER = 0.40f
    const val GLOW_INNER = 0.15f
    const val GLOW_OUTER = 0.04f
    const val DOT_BORDER = 0.80f
    val GRADIENT_LIGHT = floatArrayOf(0.45f, 0.38f, 0.28f, 0.15f, 0.05f)
    val GRADIENT_DARK = floatArrayOf(0.30f, 0.22f, 0.14f, 0.07f, 0.02f)
}

private class LineStaticLayerCache {
    var renderKey: Any? = null
    var recordedSize: Size = Size.Unspecified
}

@Composable
fun GemLineChart(
    points: List<ChartPoint>,
    bounds: GemChartBounds,
    isZoomed: Boolean,
    lineColor: Color,
    indexAt: (Float) -> Int?,
    onZoom: (Float, Float) -> Unit,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelectionChanged: (Int?) -> Unit = {},
    minLabel: String? = null,
    maxLabel: String? = null,
) {
    val currentSelection by rememberUpdatedState(selectedIndex)
    GemLineChart(
        points = points,
        bounds = bounds,
        isZoomed = isZoomed,
        lineColor = lineColor,
        indexAt = indexAt,
        onZoom = onZoom,
        onPan = onPan,
        selectedIndexProvider = { currentSelection },
        modifier = modifier,
        onSelectionChanged = onSelectionChanged,
        minLabel = minLabel,
        maxLabel = maxLabel,
    )
}

@Composable
internal fun GemLineChart(
    points: List<ChartPoint>,
    bounds: GemChartBounds,
    isZoomed: Boolean,
    lineColor: Color,
    indexAt: (Float) -> Int?,
    onZoom: (Float, Float) -> Unit,
    onPan: (Float) -> Unit,
    selectedIndexProvider: () -> Int?,
    modifier: Modifier = Modifier,
    onSelectionChanged: (Int?) -> Unit = {},
    minLabel: String? = null,
    maxLabel: String? = null,
) {
    if (points.size < 2) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val isDark = isSystemInDarkTheme()

    val lineWidthPx = with(density) { Metrics.lineWidth.toPx() }
    val horizontalPaddingPx = with(density) { Metrics.horizontalPadding.toPx() }
    val selectionDotRadiusPx = with(density) { Metrics.selectionDotRadius.toPx() }
    val dashLengthPx = with(density) { Metrics.dashLength.toPx() }
    val labelWidthPx = with(density) { Metrics.labelWidth.toPx() }
    val labelEdgePaddingPx = with(density) { Metrics.labelEdgePadding.toPx() }
    val verticalPaddingPx = with(density) { Metrics.verticalPadding.toPx() }
    val labelOffsetBelowPx = with(density) { Metrics.boundLabelOffsetBelow.toPx() }
    val labelOffsetAbovePx = with(density) { Metrics.boundLabelOffsetAbove.toPx() }
    val glowExtraPx = with(density) { Metrics.selectionGlowExtra.toPx() }
    val dotContainerPx = with(density) { (Metrics.pulsingDotSize * 5).toPx() }

    val lineStroke = remember(lineWidthPx) {
        Stroke(lineWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val selectionDashEffect = remember(dashLengthPx) {
        PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, dashLengthPx))
    }

    val minIndex = bounds.lowerIndex
    val maxIndex = bounds.upperIndex

    val valueRange = rememberChartRange(bounds.yMin.toFloat(), bounds.yMax.toFloat())
    val viewport = valueRange.viewport

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val selection = rememberChartSelection(selectedIndexProvider)

    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelStyle = remember(secondaryColor) {
        TextStyle(
            color = secondaryColor,
            fontSize = captionFontSize,
            textAlign = TextAlign.Center,
        )
    }
    val minMeasured = remember(minLabel, labelStyle, textMeasurer) {
        minLabel?.let { textMeasurer.measure(it, labelStyle) }
    }
    val maxMeasured = remember(maxLabel, labelStyle, textMeasurer) {
        maxLabel?.let { textMeasurer.measure(it, labelStyle) }
    }

    val staticLayer = rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    val layerCache = remember { LineStaticLayerCache() }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { chartSize = it }) {
        if (chartSize.width > 0 && chartSize.height > 0) {
            val canvasWidth = chartSize.width.toFloat()
            val canvasHeight = chartSize.height.toFloat()
            val plotTop = verticalPaddingPx
            val plotBottom = canvasHeight - verticalPaddingPx
            val plotHeight = plotBottom - plotTop
            if (plotHeight <= 0) return@Box
            val chartViewport = viewport ?: return@Box

            val curveLeft = horizontalPaddingPx
            val curveWidth = canvasWidth - 2 * horizontalPaddingPx

            fun screenX(fraction: Float) = curveLeft + fraction * curveWidth
            fun valueToScreenY(value: Float) = plotTop + chartViewport.y(value, plotHeight)

            val pointIndex by rememberUpdatedState(indexAt)
            val selectionChanged by rememberUpdatedState(onSelectionChanged)
            val zoom by rememberUpdatedState(onZoom)
            val pan by rememberUpdatedState(onPan)
            val zoomed by rememberUpdatedState(isZoomed)

            val screenPoints = remember(points, chartSize, chartViewport) {
                points.map { point -> Offset(screenX(point.x), valueToScreenY(point.y)) }
            }
            val curvePath = remember(screenPoints) { buildCurvePath(screenPoints) }
            val screenYRange = remember(screenPoints) {
                var minY = Float.POSITIVE_INFINITY
                var maxY = Float.NEGATIVE_INFINITY
                for (i in screenPoints.indices) {
                    val y = screenPoints[i].y
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                maxY - minY
            }
            val areaPath = remember(curvePath, screenPoints, plotBottom, screenYRange) {
                if (screenYRange > 2f && screenPoints.size >= 2) {
                    buildAreaPath(curvePath, screenPoints, plotBottom)
                } else {
                    null
                }
            }
            val areaBrush = remember(lineColor, isDark, plotTop, plotBottom) {
                val gradientAlphas = if (isDark) Alpha.GRADIENT_DARK else Alpha.GRADIENT_LIGHT
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to lineColor.copy(gradientAlphas[0]),
                        0.25f to lineColor.copy(gradientAlphas[1]),
                        0.50f to lineColor.copy(gradientAlphas[2]),
                        0.75f to lineColor.copy(gradientAlphas[3]),
                        0.92f to lineColor.copy(gradientAlphas[4]),
                        1.00f to lineColor.copy(0f),
                    ),
                    startY = plotTop,
                    endY = plotBottom,
                )
            }

            val renderKey = remember(
                curvePath,
                areaPath,
                areaBrush,
                lineColor,
                minMeasured,
                maxMeasured,
                minIndex,
                maxIndex,
            ) {
                listOf(curvePath, areaPath, areaBrush, lineColor, minMeasured, maxMeasured, minIndex, maxIndex)
            }

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .chartGestures(
                        plotLeft = curveLeft,
                        plotWidth = curveWidth,
                        isZoomed = { zoomed },
                        indexAt = { pointIndex(it) },
                        onSelectionChanged = { selectionChanged(it) },
                        onZoom = { magnification, anchor -> zoom(magnification, anchor) },
                        onPan = { pan(it) },
                    )
                    .drawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@drawBehind
                        if (layerCache.renderKey != renderKey || layerCache.recordedSize != size) {
                            layerCache.renderKey = renderKey
                            layerCache.recordedSize = size
                            staticLayer.record {
                                if (areaPath != null) {
                                    drawPath(areaPath, areaBrush, style = Fill)
                                }
                                drawPath(curvePath, lineColor, style = lineStroke)
                                if (minMeasured != null && minIndex in points.indices) {
                                    drawMeasuredBoundLabel(minMeasured, screenX(points[minIndex].x), plotBottom + labelOffsetBelowPx, canvasWidth, labelWidthPx, labelEdgePaddingPx)
                                }
                                if (maxMeasured != null && maxIndex in points.indices) {
                                    drawMeasuredBoundLabel(maxMeasured, screenX(points[maxIndex].x), plotTop - labelOffsetAbovePx, canvasWidth, labelWidthPx, labelEdgePaddingPx)
                                }
                            }
                        }
                        drawLayer(staticLayer)
                    },
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                val activeIndex = selectedIndexProvider()
                val alpha = selection.alpha
                if (activeIndex != null && activeIndex in points.indices && alpha > 0f) {
                    drawSelectionIndicator(
                        point = Offset(screenX(points[activeIndex].x), valueToScreenY(points[activeIndex].y)),
                        canvasHeight = canvasHeight,
                        alpha = alpha,
                        color = lineColor,
                        lineWidth = lineWidthPx,
                        dotRadius = selectionDotRadiusPx,
                        dashEffect = selectionDashEffect,
                        glowExtra = glowExtraPx,
                    )
                }
            }

            if (points.last().x <= 1f) {
                LineChartPulsingDotOverlay(
                    selectedIndexProvider = selectedIndexProvider,
                    color = lineColor,
                    offsetX = (screenX(points.last().x) - dotContainerPx / 2f).roundToInt(),
                    offsetY = (valueToScreenY(points.last().y) - dotContainerPx / 2f).roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun LineChartPulsingDotOverlay(
    selectedIndexProvider: () -> Int?,
    color: Color,
    offsetX: Int,
    offsetY: Int,
) {
    if (selectedIndexProvider() == null) {
        PulsingDot(
            color = color,
            dotSize = Metrics.pulsingDotSize,
            modifier = Modifier.offset { IntOffset(offsetX, offsetY) },
        )
    }
}

private fun DrawScope.drawSelectionIndicator(
    point: Offset,
    canvasHeight: Float,
    alpha: Float,
    color: Color,
    lineWidth: Float,
    dotRadius: Float,
    dashEffect: PathEffect,
    glowExtra: Float,
) {
    drawLine(
        color.copy(Alpha.SELECTION_LINE * alpha),
        Offset(point.x, 0f),
        Offset(point.x, canvasHeight),
        space1.toPx(),
        pathEffect = dashEffect,
    )
    val glowRadius = dotRadius + glowExtra
    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(Alpha.GLOW_CENTER * alpha),
                0.4f to color.copy(Alpha.GLOW_INNER * alpha),
                0.7f to color.copy(Alpha.GLOW_OUTER * alpha),
                1f to color.copy(0f),
            ),
            center = point,
            radius = glowRadius,
        ),
        glowRadius,
        point,
    )
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha), color.copy(Alpha.DOT_BORDER * alpha)), point, dotRadius),
        dotRadius,
        point,
    )
    drawCircle(color.copy(alpha), dotRadius, point, style = Stroke(lineWidth))
}

private fun buildAreaPath(curvePath: Path, screenPoints: List<Offset>, bottomY: Float): Path = Path().apply {
    addPath(curvePath)
    lineTo(screenPoints.last().x, bottomY)
    lineTo(screenPoints.first().x, bottomY)
    close()
}

private fun DrawScope.drawMeasuredBoundLabel(
    measured: TextLayoutResult,
    anchorX: Float,
    anchorY: Float,
    canvasWidth: Float,
    labelWidth: Float,
    edgePadding: Float,
) {
    val halfLabel = labelWidth / 2f
    val maxLeading = (canvasWidth - labelWidth - edgePadding).coerceAtLeast(edgePadding)
    val labelX = (anchorX - halfLabel).coerceIn(edgePadding, maxLeading)
    val textX = labelX + (labelWidth - measured.size.width) / 2f
    val clampedY = anchorY.coerceIn(0f, size.height - measured.size.height.toFloat())
    drawText(measured, topLeft = Offset(textX, clampedY))
}

private fun buildCurvePath(screenPoints: List<Offset>): Path {
    val path = Path()
    if (screenPoints.size < 2) return path
    path.moveTo(screenPoints[0].x, screenPoints[0].y)
    if (screenPoints.size == 2) {
        path.lineTo(screenPoints[1].x, screenPoints[1].y)
        return path
    }
    for (segment in 0 until screenPoints.size - 1) {
        val prev = screenPoints[(segment - 1).coerceAtLeast(0)]
        val current = screenPoints[segment]
        val next = screenPoints[min(segment + 1, screenPoints.lastIndex)]
        val afterNext = screenPoints[min(segment + 2, screenPoints.lastIndex)]

        val distPrevCurrent = distanceBetween(prev, current).coerceAtLeast(1e-4f)
        val distCurrentNext = distanceBetween(current, next).coerceAtLeast(1e-4f)
        val distNextAfter = distanceBetween(next, afterNext).coerceAtLeast(1e-4f)

        val knot1 = sqrt(distPrevCurrent)
        val knot2 = knot1 + sqrt(distCurrentNext)
        val knot3 = knot2 + sqrt(distNextAfter)

        val tangent1X = (knot2 - knot1) * ((current.x - prev.x) / knot1 - (next.x - prev.x) / knot2 + (next.x - current.x) / (knot2 - knot1))
        val tangent1Y = (knot2 - knot1) * ((current.y - prev.y) / knot1 - (next.y - prev.y) / knot2 + (next.y - current.y) / (knot2 - knot1))
        val tangent2X = (knot2 - knot1) * ((next.x - current.x) / (knot2 - knot1) - (afterNext.x - current.x) / (knot3 - knot1) + (afterNext.x - next.x) / (knot3 - knot2))
        val tangent2Y = (knot2 - knot1) * ((next.y - current.y) / (knot2 - knot1) - (afterNext.y - current.y) / (knot3 - knot1) + (afterNext.y - next.y) / (knot3 - knot2))

        path.cubicTo(
            current.x + tangent1X / 3f,
            current.y + tangent1Y / 3f,
            next.x - tangent2X / 3f,
            next.y - tangent2Y / 3f,
            next.x,
            next.y,
        )
    }
    return path
}

private fun distanceBetween(from: Offset, to: Offset): Float = sqrt((from.x - to.x).let { it * it } + (from.y - to.y).let { it * it })
