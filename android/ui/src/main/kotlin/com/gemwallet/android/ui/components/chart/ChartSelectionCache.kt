package com.gemwallet.android.ui.components.chart

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.format.SectionDateFormatter
import uniffi.gemstone.GemCandleChart
import uniffi.gemstone.GemCandleTooltip
import uniffi.gemstone.GemCandleTooltipRow
import uniffi.gemstone.GemChartData
import uniffi.gemstone.GemChartHeader
import uniffi.gemstone.GemValueTone
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@Immutable
internal data class FormattedChartHeader(
    val valueText: String,
    val valueTone: GemValueTone,
    val secondaryValueText: String?,
    val changeText: String?,
    val changeTone: GemValueTone?,
)

@Immutable
internal data class FormattedChartSelection(
    val header: FormattedChartHeader,
    val date: String,
)

@Immutable
internal data class FormattedTooltipCell(
    val row: GemCandleTooltipRow,
    val valueText: String,
    val tone: GemValueTone,
)

@Immutable
internal data class FormattedCandleTooltip(
    val cells: List<FormattedTooltipCell>,
    val dividerAfter: Int,
)

internal fun GemChartHeader.format(locale: Locale = Locale.getDefault()): FormattedChartHeader = FormattedChartHeader(
    valueText = value.text(locale),
    valueTone = value.tone,
    secondaryValueText = secondaryValue?.text(locale),
    changeText = change?.text(locale),
    changeTone = change?.tone,
)

internal fun GemCandleTooltip.format(locale: Locale = Locale.getDefault()): FormattedCandleTooltip {
    val cells = ArrayList<FormattedTooltipCell>(prices.size + summary.size)
    for (i in prices.indices) {
        val cell = prices[i]
        cells.add(FormattedTooltipCell(row = cell.row, valueText = cell.value.text(locale), tone = cell.value.tone))
    }
    for (i in summary.indices) {
        val cell = summary[i]
        cells.add(FormattedTooltipCell(row = cell.row, valueText = cell.value.text(locale), tone = cell.value.tone))
    }
    return FormattedCandleTooltip(cells = cells, dividerAfter = prices.lastIndex)
}

internal fun nearestDateIndex(dates: LongArray, start: Long, end: Long, fraction: Float): Int? {
    if (dates.isEmpty() || start > end) return null
    val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
    val span = (end - start).coerceAtLeast(0L)
    val target = start + (span.toDouble() * clamped).toLong()

    val firstVisible = lowerBound(dates, start)
    if (firstVisible >= dates.size || dates[firstVisible] > end) return null
    val lastVisible = upperBound(dates, end) - 1
    if (lastVisible < firstVisible) return null

    val insertion = lowerBound(dates, target, firstVisible, lastVisible + 1)
    if (insertion <= firstVisible) return firstVisible
    if (insertion > lastVisible) return lastVisible

    val left = insertion - 1
    val right = insertion
    val leftDiff = abs(dates[left] - target)
    val rightDiff = abs(dates[right] - target)
    return if (leftDiff <= rightDiff) left else right
}

private fun lowerBound(dates: LongArray, value: Long, fromIndex: Int = 0, toIndex: Int = dates.size): Int {
    var low = fromIndex
    var high = toIndex
    while (low < high) {
        val mid = (low + high) ushr 1
        if (dates[mid] < value) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}

private fun upperBound(dates: LongArray, value: Long, fromIndex: Int = 0, toIndex: Int = dates.size): Int {
    var low = fromIndex
    var high = toIndex
    while (low < high) {
        val mid = (low + high) ushr 1
        if (dates[mid] <= value) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}

@Stable
internal class LineChartSelectionCache(
    private val data: GemChartData,
    private val dateFormatter: SectionDateFormatter,
    private val zoneId: ZoneId,
    private val locale: Locale,
) {
    private val dates = LongArray(data.values.size) { index -> data.values[index].date }
    private val selections = HashMap<Int, FormattedChartSelection?>()
    val defaultHeader: FormattedChartHeader? = data.header?.format(locale)

    fun indexAt(fraction: Float): Int? = nearestDateIndex(dates, data.start, data.end, fraction)

    fun selection(index: Int): FormattedChartSelection? {
        if (index !in data.values.indices) return null
        if (selections.containsKey(index)) {
            return selections[index]
        }
        val point = data.values[index]
        val resolved = if (point.date !in data.start..data.end) {
            null
        } else {
            data.copy(values = listOf(point), header = null).selection(0)?.let { selection ->
                FormattedChartSelection(
                    header = selection.header.format(locale),
                    date = dateFormatter.chartDate(selection.date, data.dateStyle, zoneId, locale),
                )
            }
        }
        selections[index] = resolved
        return resolved
    }
}

@Stable
internal class CandleChartSelectionCache(
    private val chart: GemCandleChart,
    private val dateFormatter: SectionDateFormatter? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val dates = LongArray(chart.candles.size) { index -> chart.candles[index].date }
    private val minimalLayout = chart.layout.copy(
        levels = emptyList(),
        lines = emptyList(),
        tones = emptyList(),
    )
    private val selections = HashMap<Int, FormattedChartSelection?>()
    private val tooltips = HashMap<Int, FormattedCandleTooltip?>()
    val defaultHeader: FormattedChartHeader = chart.header.format(locale)

    fun indexAt(fraction: Float): Int? = nearestDateIndex(dates, chart.start, chart.end, fraction)

    fun selection(index: Int): FormattedChartSelection? {
        if (index !in chart.candles.indices) return null
        if (selections.containsKey(index)) {
            return selections[index]
        }
        val formatter = dateFormatter ?: return null
        val resolved = singleCandleShell(index)?.selection(0)?.let { selection ->
            FormattedChartSelection(
                header = selection.header.format(locale),
                date = formatter.chartDate(selection.date, chart.dateStyle, zoneId, locale),
            )
        }
        selections[index] = resolved
        return resolved
    }

    fun tooltip(index: Int): FormattedCandleTooltip? {
        if (index !in chart.candles.indices) return null
        if (tooltips.containsKey(index)) {
            return tooltips[index]
        }
        val resolved = singleCandleShell(index)?.tooltip(0)?.format(locale)
        tooltips[index] = resolved
        return resolved
    }

    private fun singleCandleShell(index: Int): GemCandleChart? {
        val candle = chart.candles.getOrNull(index) ?: return null
        return chart.copy(
            candles = listOf(candle),
            layout = minimalLayout,
            xTicks = emptyList(),
            markers = emptyList(),
        )
    }
}
