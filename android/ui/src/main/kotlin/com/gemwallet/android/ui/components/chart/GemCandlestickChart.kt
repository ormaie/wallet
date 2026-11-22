package com.gemwallet.android.ui.components.chart

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.gemwallet.android.ext.toPrimitives
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.components.list_item.ListItemTextStyle
import com.gemwallet.android.ui.components.list_item.color
import com.gemwallet.android.ui.components.perpetual.color
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.style.color
import com.gemwallet.android.ui.theme.alpha60
import com.gemwallet.android.ui.theme.captionFontSize
import com.gemwallet.android.ui.theme.space1
import com.gemwallet.android.ui.theme.space12
import com.gemwallet.android.ui.theme.space2
import com.gemwallet.android.ui.theme.space24
import com.gemwallet.android.ui.theme.space4
import com.gemwallet.android.ui.theme.space6
import com.gemwallet.android.ui.theme.space8
import uniffi.gemstone.ChartCandleStick
import uniffi.gemstone.GemCandleChart
import uniffi.gemstone.GemCandleTick
import uniffi.gemstone.GemCandleTickFormat
import uniffi.gemstone.GemFormattedNumber
import uniffi.gemstone.GemPerpetualChartLine
import uniffi.gemstone.GemPerpetualChartLineKind
import uniffi.gemstone.GemValueTone
import uniffi.gemstone.PerpetualDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.min

private object CandlestickMetrics {
    val topPadding = space4
    val bottomPadding = space24
    val leftPadding = space8
    val rightPadding = space8
    val labelPadding = space4
    val volumeBandGap = space4
    val timeLabelGap = space4
    val minWickWidth = space1
    val maxWickWidth = space2
    val currentPriceDash = space2
    val currentPriceGap = space2
    val referenceLineThickness = space1
    val referenceLineDash = space4
    val referenceLineGap = space4
    val selectionLineWidth = space1
    val selectionDashLength = space4
    val selectionDotOuterRadius = space6
    val selectionDotBorderWidth = space2
    val currentPriceBadgeHorizontalPadding = space2
    val currentPriceBadgeVerticalPadding = space1
    val referenceBadgeHorizontalPadding = space4
    val referenceBadgeVerticalPadding = space4
    val badgeCornerRadius = space4
    val referenceLabelGap = space8
    val markerSize = space12
    val markerGap = space4
    val markerRingWidth = space1

    const val SELECTION_LINE_ALPHA = 0.50f
    const val BODY_CORNER_RATIO = 0.15f
    const val VOLUME_ALPHA = 0.12f
    const val TEXT_CACHE_SIZE = 32
    const val MARKER_GLYPH_INSET_RATIO = 0.25f
}

private class CandleStaticLayerCache {
    var renderKey: Any? = null
    var recordedSize: Size = Size.Unspecified
}

@Composable
fun GemCandlestickChart(
    chart: GemCandleChart,
    onZoom: (Float, Float) -> Unit,
    onPan: (Float) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelectionChanged: (Int?) -> Unit = {},
) {
    val currentSelection by rememberUpdatedState(selectedIndex)
    val cache = remember(chart) { CandleChartSelectionCache(chart) }
    GemCandlestickChart(
        chart = chart,
        onZoom = onZoom,
        onPan = onPan,
        selectedIndexProvider = { currentSelection },
        indexAt = cache::indexAt,
        modifier = modifier,
        onSelectionChanged = onSelectionChanged,
    )
}

@Composable
internal fun GemCandlestickChart(
    chart: GemCandleChart,
    onZoom: (Float, Float) -> Unit,
    onPan: (Float) -> Unit,
    selectedIndexProvider: () -> Int?,
    indexAt: (Float) -> Int?,
    modifier: Modifier = Modifier,
    selection: ChartSelection = rememberChartSelection(selectedIndexProvider),
    onSelectionChanged: (Int?) -> Unit = {},
) {
    if (chart.candles.isEmpty()) return

    val layout = chart.layout
    val context = LocalContext.current
    val lineLabels = remember(chart) { layout.lines.map { it.label.string(context) } }
    val timeLabels = remember(chart) { chartTimeLabels(chart.xTicks, ZoneId.systemDefault(), Locale.getDefault()) }
    val levelLabels = remember(layout.levels) { layout.levels.map { it.text() } }
    val currentPriceLabel = remember(layout.currentPrice) { layout.currentPrice.text() }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = CandlestickMetrics.TEXT_CACHE_SIZE)

    val upColor = ListItemTextStyle.Positive.color()
    val downColor = ListItemTextStyle.Negative.color()
    val flatColor = ListItemTextStyle.Secondary.color()
    val axisLabelColor = MaterialTheme.colorScheme.secondary
    val gridGuidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    val volumeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CandlestickMetrics.VOLUME_ALPHA)
    val selectionAccentColor = MaterialTheme.colorScheme.primary
    val longColor = PerpetualDirection.LONG.toPrimitives().color()
    val shortColor = PerpetualDirection.SHORT.toPrimitives().color()
    val markerColors = remember(longColor, shortColor) {
        mapOf(PerpetualDirection.LONG to longColor, PerpetualDirection.SHORT to shortColor)
    }
    val markerRingColor = MaterialTheme.colorScheme.surface
    val currentPriceLineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
    val referenceColorByRole = referenceColors()

    val gridDashEffect = remember(density) {
        val dashPx = with(density) { space4.toPx() }
        PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
    }
    val referenceLineDashEffect = remember(density) {
        with(density) {
            PathEffect.dashPathEffect(floatArrayOf(CandlestickMetrics.referenceLineDash.toPx(), CandlestickMetrics.referenceLineGap.toPx()))
        }
    }
    val selectionDashEffect = remember(density) {
        val dashPx = with(density) { CandlestickMetrics.selectionDashLength.toPx() }
        PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
    }
    val axisLabelStyle = remember(axisLabelColor) {
        TextStyle(color = axisLabelColor, fontSize = captionFontSize, textAlign = TextAlign.Start)
    }
    val whiteLabelStyle = remember(axisLabelStyle) { axisLabelStyle.copy(color = Color.White) }
    val referenceLabelStyle = remember { TextStyle(color = Color.White, fontSize = captionFontSize, fontWeight = FontWeight.SemiBold) }
    val timeLabelStyle = remember(axisLabelStyle) { axisLabelStyle.copy(textAlign = TextAlign.Center) }
    val currentPriceDashEffect = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(CandlestickMetrics.currentPriceDash.toPx(), CandlestickMetrics.currentPriceGap.toPx())) }
    }

    val minWickWidthPx = with(density) { CandlestickMetrics.minWickWidth.toPx() }
    val maxWickWidthPx = with(density) { CandlestickMetrics.maxWickWidth.toPx() }
    val timeLabelGapPx = with(density) { CandlestickMetrics.timeLabelGap.toPx() }
    val referenceLineThicknessPx = with(density) { CandlestickMetrics.referenceLineThickness.toPx() }
    val labelPaddingPx = with(density) { CandlestickMetrics.labelPadding.toPx() }
    val selectionLineWidthPx = with(density) { CandlestickMetrics.selectionLineWidth.toPx() }
    val selectionDotOuterRadiusPx = with(density) { CandlestickMetrics.selectionDotOuterRadius.toPx() }
    val selectionDotBorderPx = with(density) { CandlestickMetrics.selectionDotBorderWidth.toPx() }
    val currentPriceBadgeHorizontalPaddingPx = with(density) { CandlestickMetrics.currentPriceBadgeHorizontalPadding.toPx() }
    val currentPriceBadgeVerticalPaddingPx = with(density) { CandlestickMetrics.currentPriceBadgeVerticalPadding.toPx() }
    val referenceBadgeHorizontalPaddingPx = with(density) { CandlestickMetrics.referenceBadgeHorizontalPadding.toPx() }
    val referenceBadgeVerticalPaddingPx = with(density) { CandlestickMetrics.referenceBadgeVerticalPadding.toPx() }
    val badgeCornerRadiusPx = with(density) { CandlestickMetrics.badgeCornerRadius.toPx() }
    val referenceLabelGapPx = with(density) { CandlestickMetrics.referenceLabelGap.toPx() }
    val markerSizePx = with(density) { CandlestickMetrics.markerSize.toPx() }
    val markerRingWidthPx = with(density) { CandlestickMetrics.markerRingWidth.toPx() }
    val markerGapPx = with(density) { CandlestickMetrics.markerGap.toPx() }
    val topPaddingPx = with(density) { CandlestickMetrics.topPadding.toPx() }
    val bottomPaddingPx = with(density) { CandlestickMetrics.bottomPadding.toPx() }
    val leftPaddingPx = with(density) { CandlestickMetrics.leftPadding.toPx() }
    val priceTextWidthPx = remember(levelLabels, currentPriceLabel, axisLabelStyle, density) {
        var maxWidth = textMeasurer.measure(currentPriceLabel, whiteLabelStyle).size.width
        for (i in levelLabels.indices) {
            val width = textMeasurer.measure(levelLabels[i], axisLabelStyle).size.width
            if (width > maxWidth) maxWidth = width
        }
        maxWidth.toFloat()
    }
    val rightAxisWidthPx = labelPaddingPx + priceTextWidthPx + with(density) { CandlestickMetrics.rightPadding.toPx() }
    val volumeBandGapPx = with(density) { CandlestickMetrics.volumeBandGap.toPx() }

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val priceRange = rememberChartRange(layout.priceLow.toFloat(), layout.priceHigh.toFloat())
    val volumeRange = rememberChartRange(0f, layout.volumeHigh.toFloat())
    val staticLayer = rememberGraphicsLayer()
    val layerCache = remember { CandleStaticLayerCache() }
    val markerPath = remember { Path() }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { chartSize = it }) {
        if (chartSize.width <= 0 || chartSize.height <= 0) return@Box

        val frame = Rect(leftPaddingPx, topPaddingPx, chartSize.width - rightAxisWidthPx, chartSize.height - bottomPaddingPx)
        if (frame.width <= 0 || frame.height <= 0) return@Box
        val priceViewport = priceRange.viewport ?: return@Box
        val volumeViewport = volumeRange.viewport
        val plot = remember(
            chart,
            frame,
            priceViewport,
            volumeViewport,
            volumeBandGapPx,
            minWickWidthPx,
            maxWickWidthPx,
            markerSizePx,
            markerGapPx,
        ) {
            CandlestickPlot(chart, frame, volumeBandGapPx, minWickWidthPx, maxWickWidthPx, markerSizePx, markerGapPx, priceViewport, volumeViewport)
        }

        val candleIndex by rememberUpdatedState(indexAt)
        val selectionChanged by rememberUpdatedState(onSelectionChanged)
        val zoom by rememberUpdatedState(onZoom)
        val pan by rememberUpdatedState(onPan)
        val zoomed by rememberUpdatedState(chart.isZoomed)

        val renderKey = remember(
            plot,
            upColor,
            downColor,
            flatColor,
            axisLabelColor,
            gridGuidelineColor,
            volumeColor,
            markerRingColor,
            currentPriceLineColor,
            lineLabels,
            timeLabels,
            levelLabels,
            currentPriceLabel,
        ) {
            listOf(
                plot,
                upColor,
                downColor,
                flatColor,
                axisLabelColor,
                gridGuidelineColor,
                volumeColor,
                markerRingColor,
                currentPriceLineColor,
                lineLabels,
                timeLabels,
                levelLabels,
                currentPriceLabel,
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .chartGestures(
                    plotLeft = frame.left,
                    plotWidth = frame.width,
                    isZoomed = { zoomed },
                    indexAt = { candleIndex(it) },
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
                            drawYAxis(layout.levels, levelLabels, plot, gridGuidelineColor, gridDashEffect, textMeasurer, axisLabelStyle, labelPaddingPx)
                            drawTimeAxis(chart.xTicks, timeLabels, plot, gridGuidelineColor, gridDashEffect, textMeasurer, timeLabelStyle, timeLabelGapPx)
                            drawCurrentPriceLine(layout.currentPrice.value, plot, currentPriceLineColor, currentPriceDashEffect)
                            clipRect(left = frame.left, top = 0f, right = frame.right, bottom = size.height) {
                                drawVolumes(chart.candles, plot, volumeColor)
                                drawCandles(chart.candles, layout.tones, plot, upColor, downColor, flatColor)
                            }
                            drawReferenceLines(
                                referenceLines = layout.lines,
                                labels = lineLabels,
                                referenceColorByRole = referenceColorByRole,
                                plot = plot,
                                lineThicknessPx = referenceLineThicknessPx,
                                lineDashEffect = referenceLineDashEffect,
                                labelStyle = referenceLabelStyle,
                                labelGapPx = referenceLabelGapPx,
                                badgeHorizontalPaddingPx = referenceBadgeHorizontalPaddingPx,
                                badgeVerticalPaddingPx = referenceBadgeVerticalPaddingPx,
                                badgeCornerRadiusPx = badgeCornerRadiusPx,
                                textMeasurer = textMeasurer,
                            )
                            for (i in chart.markers.indices) {
                                val marker = chart.markers[i]
                                drawMarker(
                                    bounds = plot.marker(marker),
                                    direction = marker.direction,
                                    color = markerColors.getValue(marker.direction),
                                    ringColor = markerRingColor,
                                    ringWidth = markerRingWidthPx,
                                    trianglePath = markerPath,
                                )
                            }
                            drawCurrentPriceBadge(
                                price = layout.currentPrice.value,
                                tone = layout.currentTone,
                                priceLabel = currentPriceLabel,
                                plot = plot,
                                labelPaddingPx = labelPaddingPx,
                                badgeHorizontalPaddingPx = currentPriceBadgeHorizontalPaddingPx,
                                badgeVerticalPaddingPx = currentPriceBadgeVerticalPaddingPx,
                                badgeCornerRadiusPx = badgeCornerRadiusPx,
                                labelStyle = whiteLabelStyle,
                                upColor = upColor,
                                downColor = downColor,
                                flatColor = flatColor,
                                textMeasurer = textMeasurer,
                            )
                        }
                    }
                    drawLayer(staticLayer)
                },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSelection(
                selectedIndex = selectedIndexProvider(),
                selectionAlpha = selection.alpha,
                candles = chart.candles,
                plot = plot,
                accentColor = selectionAccentColor,
                lineWidthPx = selectionLineWidthPx,
                dashEffect = selectionDashEffect,
                dotOuterRadiusPx = selectionDotOuterRadiusPx,
                dotBorderPx = selectionDotBorderPx,
            )
        }
    }
}

@Composable
private fun referenceColors(): (GemPerpetualChartLineKind) -> Color {
    val entry = GemPerpetualChartLineKind.ENTRY.color()
    val takeProfit = GemPerpetualChartLineKind.TAKE_PROFIT.color()
    val stopLoss = GemPerpetualChartLineKind.STOP_LOSS.color()
    val liquidation = GemPerpetualChartLineKind.LIQUIDATION.color()
    return remember(entry, takeProfit, stopLoss, liquidation) {
        { role ->
            when (role) {
                GemPerpetualChartLineKind.ENTRY -> entry
                GemPerpetualChartLineKind.TAKE_PROFIT -> takeProfit
                GemPerpetualChartLineKind.STOP_LOSS -> stopLoss
                GemPerpetualChartLineKind.LIQUIDATION -> liquidation
            }
        }
    }
}

private fun candleColor(tone: GemValueTone, up: Color, down: Color, flat: Color): Color = when (tone) {
    GemValueTone.POSITIVE -> up

    GemValueTone.NEGATIVE -> down

    GemValueTone.NEUTRAL,
    GemValueTone.PLAIN,
    GemValueTone.WARNING,
    -> flat
}

private fun DrawScope.drawYAxis(
    ticks: List<GemFormattedNumber>,
    labels: List<String>,
    plot: CandlestickPlot,
    guidelineColor: Color,
    guidelineDash: PathEffect,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    labelPaddingPx: Float,
) {
    val count = min(ticks.size, labels.size)
    for (i in 0 until count) {
        val y = plot.y(ticks[i].value)
        drawLine(
            color = guidelineColor,
            start = Offset(plot.frame.left, y),
            end = Offset(plot.frame.right, y),
            strokeWidth = 1f,
            pathEffect = guidelineDash,
        )
        val measured = textMeasurer.measure(labels[i], style)
        drawText(textLayoutResult = measured, topLeft = Offset(plot.frame.right + labelPaddingPx, y - measured.size.height / 2f))
    }
}

private fun DrawScope.drawTimeAxis(
    ticks: List<GemCandleTick>,
    labels: List<String>,
    plot: CandlestickPlot,
    color: Color,
    dash: PathEffect,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    labelGapPx: Float,
) {
    val count = min(ticks.size, labels.size)
    for (i in 0 until count) {
        val x = plot.x(ticks[i].date)
        drawLine(
            color = color,
            start = Offset(x, plot.frame.top),
            end = Offset(x, plot.frame.bottom),
            strokeWidth = 1f,
            pathEffect = dash,
        )
        val measured = textMeasurer.measure(labels[i], style)
        drawText(textLayoutResult = measured, topLeft = Offset(x - measured.size.width / 2f, plot.frame.bottom + labelGapPx))
    }
}

private fun DrawScope.drawCurrentPriceLine(price: Double, plot: CandlestickPlot, color: Color, dash: PathEffect) {
    val y = plot.y(price)
    if (y !in plot.frame.top..plot.priceBottom) return
    drawLine(
        color = color,
        start = Offset(plot.frame.left, y),
        end = Offset(plot.frame.right, y),
        strokeWidth = 1f,
        pathEffect = dash,
    )
}

private fun DrawScope.drawVolumes(candles: List<ChartCandleStick>, plot: CandlestickPlot, color: Color) {
    val bodyCorner = plot.bodyWidth * CandlestickMetrics.BODY_CORNER_RATIO
    for (i in candles.indices) {
        val candle = candles[i]
        if (candle.volume <= 0.0) continue
        val bar = plot.volume(candle) ?: continue
        val corner = min(bodyCorner, bar.height / 2f)
        drawRoundRect(color = color, topLeft = bar.topLeft, size = bar.size, cornerRadius = CornerRadius(corner, corner))
    }
}

private fun DrawScope.drawCandles(candles: List<ChartCandleStick>, tones: List<GemValueTone>, plot: CandlestickPlot, upColor: Color, downColor: Color, flatColor: Color) {
    val count = min(candles.size, tones.size)
    val bodyCorner = plot.bodyWidth * CandlestickMetrics.BODY_CORNER_RATIO
    for (i in 0 until count) {
        val candle = candles[i]
        val color = candleColor(tones[i], upColor, downColor, flatColor)
        val wick = plot.wick(candle)
        drawLine(color = color, start = wick.topCenter, end = wick.bottomCenter, strokeWidth = wick.width, cap = StrokeCap.Round)
        val body = plot.body(candle)
        val corner = min(bodyCorner, body.height / 2f)
        drawRoundRect(color = color, topLeft = body.topLeft, size = body.size, cornerRadius = CornerRadius(corner, corner))
    }
}

private fun DrawScope.drawReferenceLines(
    referenceLines: List<GemPerpetualChartLine>,
    labels: List<String>,
    referenceColorByRole: (GemPerpetualChartLineKind) -> Color,
    plot: CandlestickPlot,
    lineThicknessPx: Float,
    lineDashEffect: PathEffect,
    labelStyle: TextStyle,
    labelGapPx: Float,
    badgeHorizontalPaddingPx: Float,
    badgeVerticalPaddingPx: Float,
    badgeCornerRadiusPx: Float,
    textMeasurer: TextMeasurer,
) {
    val count = min(referenceLines.size, labels.size)
    if (count == 0) return
    val visibleYs = FloatArray(count)
    val visibleIndices = IntArray(count)
    var visibleCount = 0
    for (i in 0 until count) {
        val y = plot.y(referenceLines[i].price.value)
        if (y in plot.frame.top..plot.priceBottom) {
            visibleIndices[visibleCount] = i
            visibleYs[visibleCount] = y
            visibleCount += 1
        }
    }
    for (v in 0 until visibleCount) {
        val line = referenceLines[visibleIndices[v]]
        val y = visibleYs[v]
        drawLine(
            color = referenceColorByRole(line.kind).copy(alpha = alpha60),
            start = Offset(plot.frame.left, y),
            end = Offset(plot.frame.right, y),
            strokeWidth = lineThicknessPx,
            pathEffect = lineDashEffect,
        )
    }
    var previousBadgeEndX = plot.frame.left
    for (v in 0 until visibleCount) {
        val idx = visibleIndices[v]
        val line = referenceLines[idx]
        val label = labels[idx]
        val y = visibleYs[v]
        val anchorX = if (line.overlapLevel == 0) plot.frame.left else previousBadgeEndX + labelGapPx
        val badgeWidth = drawBadgeLabel(
            textMeasurer = textMeasurer,
            text = label,
            textStyle = labelStyle,
            backgroundColor = referenceColorByRole(line.kind),
            anchorX = anchorX,
            anchorY = y,
            horizontalPaddingPx = badgeHorizontalPaddingPx,
            verticalPaddingPx = badgeVerticalPaddingPx,
            cornerRadiusPx = badgeCornerRadiusPx,
        )
        previousBadgeEndX = anchorX + badgeWidth
    }
}

private fun DrawScope.drawCurrentPriceBadge(
    price: Double,
    tone: GemValueTone,
    priceLabel: String,
    plot: CandlestickPlot,
    labelPaddingPx: Float,
    badgeHorizontalPaddingPx: Float,
    badgeVerticalPaddingPx: Float,
    badgeCornerRadiusPx: Float,
    labelStyle: TextStyle,
    upColor: Color,
    downColor: Color,
    flatColor: Color,
    textMeasurer: TextMeasurer,
) {
    val y = plot.y(price)
    if (y !in plot.frame.top..plot.priceBottom) return
    drawBadgeLabel(
        textMeasurer = textMeasurer,
        text = priceLabel,
        textStyle = labelStyle,
        backgroundColor = candleColor(tone, upColor, downColor, flatColor),
        anchorX = plot.frame.right + labelPaddingPx - badgeHorizontalPaddingPx,
        anchorY = y,
        horizontalPaddingPx = badgeHorizontalPaddingPx,
        verticalPaddingPx = badgeVerticalPaddingPx,
        cornerRadiusPx = badgeCornerRadiusPx,
    )
}

private fun DrawScope.drawMarker(
    bounds: Rect,
    direction: PerpetualDirection,
    color: Color,
    ringColor: Color,
    ringWidth: Float,
    trianglePath: Path,
) {
    val glyph = bounds.deflate(bounds.width * CandlestickMetrics.MARKER_GLYPH_INSET_RATIO)
    val (tipY, baseY) = when (direction) {
        PerpetualDirection.LONG -> glyph.top to glyph.bottom
        PerpetualDirection.SHORT -> glyph.bottom to glyph.top
    }
    drawCircle(color = ringColor, radius = bounds.width / 2f + ringWidth, center = bounds.center)
    drawCircle(color = color, radius = bounds.width / 2f, center = bounds.center)
    trianglePath.reset()
    trianglePath.moveTo(glyph.center.x, tipY)
    trianglePath.lineTo(glyph.left, baseY)
    trianglePath.lineTo(glyph.right, baseY)
    trianglePath.close()
    drawPath(trianglePath, ringColor)
}

private fun DrawScope.drawSelection(
    selectedIndex: Int?,
    selectionAlpha: Float,
    candles: List<ChartCandleStick>,
    plot: CandlestickPlot,
    accentColor: Color,
    lineWidthPx: Float,
    dashEffect: PathEffect,
    dotOuterRadiusPx: Float,
    dotBorderPx: Float,
) {
    if (selectedIndex == null || selectedIndex !in candles.indices || selectionAlpha <= 0f) return
    val candle = candles[selectedIndex]
    val centerX = plot.x(candle.date)
    val closeY = plot.y(candle.close)
    drawLine(
        color = accentColor.copy(alpha = CandlestickMetrics.SELECTION_LINE_ALPHA * selectionAlpha),
        start = Offset(centerX, plot.frame.top),
        end = Offset(centerX, plot.frame.bottom),
        strokeWidth = lineWidthPx,
        pathEffect = dashEffect,
    )
    val ringRadius = dotOuterRadiusPx - dotBorderPx / 2f
    drawCircle(
        color = Color.White.copy(alpha = selectionAlpha),
        radius = ringRadius,
        center = Offset(centerX, closeY),
    )
    drawCircle(
        color = accentColor.copy(alpha = selectionAlpha),
        radius = ringRadius,
        center = Offset(centerX, closeY),
        style = Stroke(width = dotBorderPx),
    )
}

private fun DrawScope.drawBadgeLabel(
    textMeasurer: TextMeasurer,
    text: String,
    textStyle: TextStyle,
    backgroundColor: Color,
    anchorX: Float,
    anchorY: Float,
    horizontalPaddingPx: Float,
    verticalPaddingPx: Float,
    cornerRadiusPx: Float,
): Float {
    val measured = textMeasurer.measure(text, textStyle)
    val badgeWidth = measured.size.width + horizontalPaddingPx * 2f
    val badgeHeight = measured.size.height + verticalPaddingPx * 2f
    val maxY = size.height - badgeHeight
    val topY = (anchorY - badgeHeight / 2f).coerceIn(0f, maxY)
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(anchorX, topY),
        size = Size(badgeWidth, badgeHeight),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(anchorX + horizontalPaddingPx, topY + verticalPaddingPx),
    )
    return badgeWidth
}

internal fun chartTimeLabels(ticks: List<GemCandleTick>, zone: ZoneId, locale: Locale): List<String> {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val day by lazy { bestPattern("dMMM", locale) }
    val monthYear by lazy { bestPattern("MMMy", locale) }
    return ticks.map { tick ->
        when (tick.format) {
            GemCandleTickFormat.TIME -> time
            GemCandleTickFormat.DAY -> day
            GemCandleTickFormat.MONTH_YEAR -> monthYear
        }.format(Instant.ofEpochMilli(tick.date).atZone(zone))
    }
}

private fun bestPattern(skeleton: String, locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
