package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.gemwallet.android.ui.components.list_item.color
import com.gemwallet.android.ui.localization.stringRes
import com.gemwallet.android.ui.style.textStyle
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space1
import com.gemwallet.android.ui.theme.space10
import com.gemwallet.android.ui.theme.space4
import com.gemwallet.android.ui.theme.space6
import uniffi.gemstone.GemCandleTooltip
import uniffi.gemstone.GemCandleTooltipRow
import uniffi.gemstone.GemValueTone

private object CandlestickTooltipMetrics {
    val ChipCornerRadius = space10
    val BorderWidth = space1
    val DividerThickness = space1
    const val BackgroundAlpha = 0.92f
    const val BorderAlpha = 0.08f
    const val TabularNumbers = "tnum"
    const val TextCacheSize = 32
}

private class MeasuredTooltipLabel(val text: String, val layout: TextLayoutResult)

@Composable
fun CandlestickTooltip(tooltip: GemCandleTooltip, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(tooltip, locale) { tooltip.format(locale) }
    CandlestickTooltip(tooltip = formatted, modifier = modifier)
}

@Composable
internal fun CandlestickTooltip(tooltip: FormattedCandleTooltip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = CandlestickTooltipMetrics.TextCacheSize)
    val shape = remember { RoundedCornerShape(CandlestickTooltipMetrics.ChipCornerRadius) }

    val labelBaseStyle = MaterialTheme.typography.labelSmall
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = remember(labelBaseStyle, onSurfaceVariant) {
        labelBaseStyle.copy(color = onSurfaceVariant)
    }
    val valueBaseStyle = MaterialTheme.typography.labelMedium
    val valueStyle = remember(valueBaseStyle) {
        valueBaseStyle.copy(
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = CandlestickTooltipMetrics.TabularNumbers,
            textAlign = TextAlign.End,
        )
    }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CandlestickTooltipMetrics.BackgroundAlpha)
    val borderColor = remember { Color.Black.copy(alpha = CandlestickTooltipMetrics.BorderAlpha) }

    val plainColor = GemValueTone.PLAIN.textStyle().color()
    val positiveColor = GemValueTone.POSITIVE.textStyle().color()
    val negativeColor = GemValueTone.NEGATIVE.textStyle().color()
    val neutralColor = GemValueTone.NEUTRAL.textStyle().color()
    val warningColor = GemValueTone.WARNING.textStyle().color()
    val toneColor = remember(plainColor, positiveColor, negativeColor, neutralColor, warningColor) {
        { tone: GemValueTone ->
            when (tone) {
                GemValueTone.PLAIN -> plainColor
                GemValueTone.POSITIVE -> positiveColor
                GemValueTone.NEGATIVE -> negativeColor
                GemValueTone.NEUTRAL -> neutralColor
                GemValueTone.WARNING -> warningColor
            }
        }
    }

    val measuredLabels = remember(context, locale, labelStyle, textMeasurer) {
        GemCandleTooltipRow.entries.associateWith { row ->
            val label = context.getString(row.stringRes())
            MeasuredTooltipLabel(text = label, layout = textMeasurer.measure(label, labelStyle))
        }
    }

    val columnGapPx = with(density) { paddingDefault.roundToPx() }
    val rowSpacingPx = with(density) { space4.roundToPx() }
    val dividerSpacingPx = with(density) { space4.roundToPx() }
    val dividerThicknessPx = with(density) { CandlestickTooltipMetrics.DividerThickness.toPx() }
    val dividerHeightPx = with(density) { CandlestickTooltipMetrics.DividerThickness.roundToPx() }

    val cells = tooltip.cells
    if (cells.isEmpty()) return

    val valueLayouts = remember(tooltip, valueStyle, textMeasurer) {
        Array(cells.size) { index -> textMeasurer.measure(cells[index].valueText, valueStyle) }
    }

    val layoutInfo = remember(tooltip, measuredLabels, valueLayouts, columnGapPx, rowSpacingPx, dividerSpacingPx, dividerHeightPx) {
        var labelColumnWidth = 0
        var valueColumnWidth = 0
        val rowHeights = IntArray(cells.size)
        var baseHeight = 0
        for (i in cells.indices) {
            val labelLayout = measuredLabels.getValue(cells[i].row).layout
            val valueLayout = valueLayouts[i]
            if (labelLayout.size.width > labelColumnWidth) labelColumnWidth = labelLayout.size.width
            if (valueLayout.size.width > valueColumnWidth) valueColumnWidth = valueLayout.size.width
            val rowHeight = maxOf(labelLayout.size.height, valueLayout.size.height)
            rowHeights[i] = rowHeight
            baseHeight += rowHeight
        }
        val totalWidth = labelColumnWidth + columnGapPx + valueColumnWidth
        val showDivider = tooltip.dividerAfter in 0 until cells.lastIndex
        val totalHeight = baseHeight + rowSpacingPx * (cells.size - 1) + if (showDivider) {
            -rowSpacingPx + dividerSpacingPx + dividerHeightPx + dividerSpacingPx
        } else {
            0
        }
        TooltipLayoutInfo(totalWidth, totalHeight, rowHeights, showDivider)
    }

    val description = remember(tooltip, measuredLabels) {
        buildString {
            for (i in cells.indices) {
                if (i > 0) append(", ")
                append(measuredLabels.getValue(cells[i].row).text)
                append(' ')
                append(cells[i].valueText)
            }
        }
    }

    Spacer(
        modifier = modifier
            .shadow(elevation = space6, shape = shape)
            .background(color = backgroundColor, shape = shape)
            .border(width = CandlestickTooltipMetrics.BorderWidth, color = borderColor, shape = shape)
            .padding(horizontal = paddingSmall, vertical = paddingSmall)
            .semantics { contentDescription = description }
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(layoutInfo.totalWidth, layoutInfo.totalHeight))
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .drawBehind {
                var y = 0
                val totalWidth = layoutInfo.totalWidth
                for (i in cells.indices) {
                    val cell = cells[i]
                    val labelLayout = measuredLabels.getValue(cell.row).layout
                    val valueLayout = valueLayouts[i]
                    val rowHeight = layoutInfo.rowHeights[i]
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(0f, (y + (rowHeight - labelLayout.size.height) / 2).toFloat()),
                    )
                    drawText(
                        textLayoutResult = valueLayout,
                        color = toneColor(cell.tone),
                        topLeft = Offset((totalWidth - valueLayout.size.width).toFloat(), (y + (rowHeight - valueLayout.size.height) / 2).toFloat()),
                    )
                    y += rowHeight
                    when {
                        layoutInfo.showDivider && i == tooltip.dividerAfter -> {
                            y += dividerSpacingPx
                            val lineY = y + dividerThicknessPx / 2f
                            drawLine(
                                color = dividerColor,
                                start = Offset(0f, lineY),
                                end = Offset(totalWidth.toFloat(), lineY),
                                strokeWidth = dividerThicknessPx,
                            )
                            y += dividerHeightPx + dividerSpacingPx
                        }

                        i < cells.lastIndex -> y += rowSpacingPx
                    }
                }
            },
    )
}

private class TooltipLayoutInfo(
    val totalWidth: Int,
    val totalHeight: Int,
    val rowHeights: IntArray,
    val showDivider: Boolean,
)
