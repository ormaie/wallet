package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.gemwallet.android.ext.errorText
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.format.rowDateFormatter
import com.gemwallet.android.ui.localization.text
import com.gemwallet.android.ui.models.ChartUIState
import com.gemwallet.android.ui.models.StateViewType
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space4
import com.wallet.core.primitives.ChartPeriod
import uniffi.gemstone.GemChartData
import uniffi.gemstone.GemChartPhase
import java.time.ZoneId

@Composable
fun ChartSection(state: ChartUIState, onPeriodSelect: (ChartPeriod) -> Unit, onZoom: (Float, Float) -> Unit, onPan: (Float) -> Unit) {
    val context = LocalContext.current
    val dateFormatter = remember(context) { context.rowDateFormatter() }
    val viewState: StateViewType<GemChartPhase> = when (val phase = state.phase) {
        GemChartPhase.Loading -> StateViewType.Loading
        is GemChartPhase.Data -> StateViewType.Data(phase)
        is GemChartPhase.Candles -> StateViewType.Data(phase)
        GemChartPhase.NoData -> StateViewType.NoData
        is GemChartPhase.Failed -> StateViewType.Error(phase.error.errorText().text(context))
    }

    val isCandles = state.phase is GemChartPhase.Candles
    val selectedIndexState = remember(state.period, isCandles) { mutableStateOf<Int?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val lineCache = remember(state.phase, dateFormatter, zoneId, locale) {
        (state.phase as? GemChartPhase.Data)?.let { LineChartSelectionCache(it.data, dateFormatter, zoneId, locale) }
    }
    val candleCache = remember(state.phase, dateFormatter, zoneId, locale) {
        (state.phase as? GemChartPhase.Candles)?.let { CandleChartSelectionCache(it.chart, dateFormatter, zoneId, locale) }
    }

    ChartStateView(
        state = viewState,
        headerContent = {
            ChartSectionHeader(
                phase = state.phase,
                selectedIndexProvider = { selectedIndexState.value },
                lineCache = lineCache,
                candleCache = candleCache,
            )
        },
        period = state.period,
        onPeriodSelect = onPeriodSelect,
        periods = state.periods,
    ) { phase ->
        when (phase) {
            is GemChartPhase.Data -> key(state.period) {
                val cache = lineCache ?: remember(phase.data, dateFormatter, zoneId, locale) {
                    LineChartSelectionCache(phase.data, dateFormatter, zoneId, locale)
                }
                val points = remember(phase.data) { phase.data.linePoints() }
                val minLabel = remember(phase.data) { phase.data.bounds.low.text() }
                val maxLabel = remember(phase.data) { phase.data.bounds.high.text() }
                GemLineChart(
                    points = points,
                    bounds = phase.data.bounds,
                    isZoomed = phase.data.isZoomed,
                    lineColor = MaterialTheme.colorScheme.primary,
                    indexAt = cache::indexAt,
                    selectedIndexProvider = { selectedIndexState.value?.takeIf { it in phase.data.values.indices } },
                    onSelectionChanged = { selectedIndexState.value = it },
                    onZoom = onZoom,
                    onPan = onPan,
                    minLabel = minLabel,
                    maxLabel = maxLabel,
                )
            }

            is GemChartPhase.Candles -> CandleChartBody(
                chart = phase.chart,
                cache = candleCache ?: remember(phase.chart, dateFormatter, zoneId, locale) {
                    CandleChartSelectionCache(phase.chart, dateFormatter, zoneId, locale)
                },
                selectedIndexProvider = { selectedIndexState.value },
                onSelectionChanged = { selectedIndexState.value = it },
                onZoom = onZoom,
                onPan = onPan,
            )

            else -> Unit
        }
    }
}

@Composable
private fun ChartSectionHeader(
    phase: GemChartPhase,
    selectedIndexProvider: () -> Int?,
    lineCache: LineChartSelectionCache?,
    candleCache: CandleChartSelectionCache?,
) {
    val selectedIndex = selectedIndexProvider()
    val header: FormattedChartHeader?
    val headerDate: String?
    when (phase) {
        is GemChartPhase.Data -> {
            val selection = selectedIndex?.let { lineCache?.selection(it) }
            header = selection?.header ?: lineCache?.defaultHeader
            headerDate = selection?.date
        }

        is GemChartPhase.Candles -> {
            val selection = selectedIndex?.let { candleCache?.selection(it) }
            header = selection?.header ?: candleCache?.defaultHeader
            headerDate = selection?.date
        }

        else -> {
            header = null
            headerDate = null
        }
    }
    header?.let {
        ChartHeader(
            formatted = it,
            date = headerDate,
            modifier = Modifier.padding(top = paddingSmall, bottom = space4),
        )
    }
}

internal fun GemChartData.linePoints(): List<ChartPoint> = values.map { value -> ChartPoint(x = (value.date - start) / (end - start).coerceAtLeast(1L).toFloat(), y = value.value.toFloat()) }
