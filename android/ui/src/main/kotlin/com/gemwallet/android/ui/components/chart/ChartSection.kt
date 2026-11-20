package com.gemwallet.android.ui.components.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.gemwallet.android.ext.errorText
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.format.rowDateFormatter
import com.gemwallet.android.ui.localization.text
import com.gemwallet.android.ui.models.ChartUIState
import com.gemwallet.android.ui.models.StateViewType
import com.wallet.core.primitives.ChartPeriod
import uniffi.gemstone.GemChartData
import uniffi.gemstone.GemChartHeader
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
    var selectedIndex by remember(state.period, isCandles) { mutableStateOf<Int?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = ZoneId.systemDefault()

    val header: GemChartHeader?
    val headerDate: String?
    when (val phase = state.phase) {
        is GemChartPhase.Data -> {
            val safeIndex = selectedIndex?.takeIf { it in phase.data.values.indices }
            val selection = remember(phase.data, safeIndex) { safeIndex?.let { phase.data.selection(it) } }
            header = selection?.header ?: phase.data.header
            headerDate = selection?.let { dateFormatter.chartDate(it.date, phase.data.dateStyle, zoneId, locale) }
        }

        is GemChartPhase.Candles -> {
            val safeIndex = selectedIndex?.takeIf { it in phase.chart.candles.indices }
            val selection = remember(phase.chart, safeIndex) { safeIndex?.let { phase.chart.selection(it) } }
            header = selection?.header ?: phase.chart.header
            headerDate = selection?.let { dateFormatter.chartDate(it.date, phase.chart.dateStyle, zoneId, locale) }
        }

        else -> {
            header = null
            headerDate = null
        }
    }

    ChartStateView(
        state = viewState,
        header = header,
        date = headerDate,
        period = state.period,
        onPeriodSelect = onPeriodSelect,
        periods = state.periods,
    ) { phase ->
        when (phase) {
            is GemChartPhase.Data -> key(state.period) {
                val points = remember(phase.data) { phase.data.linePoints() }
                val minLabel = remember(phase.data) { phase.data.bounds.low.text() }
                val maxLabel = remember(phase.data) { phase.data.bounds.high.text() }
                GemLineChart(
                    points = points,
                    bounds = phase.data.bounds,
                    isZoomed = phase.data.isZoomed,
                    lineColor = MaterialTheme.colorScheme.primary,
                    indexAt = { fraction -> phase.data.indexAt(fraction.toDouble()) },
                    selectedIndex = selectedIndex?.takeIf { it in phase.data.values.indices },
                    onSelectionChanged = { selectedIndex = it },
                    onZoom = onZoom,
                    onPan = onPan,
                    minLabel = minLabel,
                    maxLabel = maxLabel,
                )
            }

            is GemChartPhase.Candles -> CandleChartBody(
                chart = phase.chart,
                selectedIndex = selectedIndex,
                onSelectionChanged = { selectedIndex = it },
                onZoom = onZoom,
                onPan = onPan,
            )

            else -> Unit
        }
    }
}

internal fun GemChartData.linePoints(): List<ChartPoint> = values.map { value -> ChartPoint(x = (value.date - start) / (end - start).coerceAtLeast(1L).toFloat(), y = value.value.toFloat()) }
