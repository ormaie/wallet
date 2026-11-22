package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.format.rowDateFormatter
import com.gemwallet.android.ui.models.StateViewType
import com.gemwallet.android.ui.models.dataOrNull
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space4
import com.wallet.core.primitives.ChartPeriod
import uniffi.gemstone.GemCandleChart
import java.time.ZoneId

private val TooltipRightSafeArea = 96.dp

private class TooltipDisplayHolder {
    var tooltip: FormattedCandleTooltip? = null
    var isRightHalf: Boolean = false
}

@Composable
fun CandleChartSection(state: StateViewType<GemCandleChart>, period: ChartPeriod, onPeriodSelect: (ChartPeriod) -> Unit, onZoom: (Float, Float) -> Unit, onPan: (Float) -> Unit, periods: List<ChartPeriod>, modifier: Modifier = Modifier) {
    val chart = state.dataOrNull
    val selectedIndexState = remember(period) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val dateFormatter = remember(context) { context.rowDateFormatter() }
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val cache = remember(chart, dateFormatter, zoneId, locale) {
        chart?.let { CandleChartSelectionCache(it, dateFormatter, zoneId, locale) }
    }

    ChartStateView(
        state = state,
        headerContent = {
            if (cache != null) {
                CandleChartSectionHeader(
                    cache = cache,
                    selectedIndexProvider = { selectedIndexState.value },
                )
            }
        },
        period = period,
        onPeriodSelect = onPeriodSelect,
        periods = periods,
        modifier = modifier,
    ) { model ->
        CandleChartBody(
            chart = model,
            cache = cache ?: remember(model, dateFormatter, zoneId, locale) {
                CandleChartSelectionCache(model, dateFormatter, zoneId, locale)
            },
            selectedIndexProvider = { selectedIndexState.value },
            onSelectionChanged = { selectedIndexState.value = it },
            onZoom = onZoom,
            onPan = onPan,
        )
    }
}

@Composable
private fun CandleChartSectionHeader(
    cache: CandleChartSelectionCache,
    selectedIndexProvider: () -> Int?,
) {
    val selection = selectedIndexProvider()?.let(cache::selection)
    ChartHeader(
        formatted = selection?.header ?: cache.defaultHeader,
        date = selection?.date,
        modifier = Modifier.padding(top = paddingSmall, bottom = space4),
    )
}

@Composable
internal fun BoxScope.CandleChartBody(
    chart: GemCandleChart,
    cache: CandleChartSelectionCache,
    selectedIndexProvider: () -> Int?,
    onSelectionChanged: (Int?) -> Unit,
    onZoom: (Float, Float) -> Unit,
    onPan: (Float) -> Unit,
) {
    val safeSelectedProvider = remember(chart, selectedIndexProvider) {
        { selectedIndexProvider()?.takeIf { it in chart.candles.indices } }
    }
    val selection = rememberChartSelection(safeSelectedProvider)
    GemCandlestickChart(
        chart = chart,
        selectedIndexProvider = safeSelectedProvider,
        indexAt = cache::indexAt,
        selection = selection,
        onSelectionChanged = onSelectionChanged,
        onZoom = onZoom,
        onPan = onPan,
    )
    CandleTooltipOverlay(
        chart = chart,
        cache = cache,
        selection = selection,
        selectedIndexProvider = safeSelectedProvider,
    )
}

@Composable
private fun BoxScope.CandleTooltipOverlay(
    chart: GemCandleChart,
    cache: CandleChartSelectionCache,
    selection: ChartSelection,
    selectedIndexProvider: () -> Int?,
) {
    val holder = remember(chart) { TooltipDisplayHolder() }
    val isVisible by remember(selection, selectedIndexProvider) {
        derivedStateOf { selectedIndexProvider() != null || selection.alpha > 0f }
    }
    if (!isVisible) return

    val safeSelectedIndex = selectedIndexProvider()
    if (safeSelectedIndex != null) {
        holder.tooltip = cache.tooltip(safeSelectedIndex)
        holder.isRightHalf = (chart.candles[safeSelectedIndex].date - chart.start) * 2 > chart.end - chart.start
    }
    val tooltip = holder.tooltip ?: return
    val isRightHalf = holder.isRightHalf
    CandlestickTooltip(
        tooltip = tooltip,
        modifier = Modifier
            .align(if (isRightHalf) Alignment.TopStart else Alignment.TopEnd)
            .padding(
                start = paddingSmall,
                end = if (isRightHalf) paddingSmall else TooltipRightSafeArea,
                top = paddingSmall,
            )
            .graphicsLayer { alpha = selection.alpha },
    )
}
