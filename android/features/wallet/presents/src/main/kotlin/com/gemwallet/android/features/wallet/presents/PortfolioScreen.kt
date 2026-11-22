package com.gemwallet.android.features.wallet.presents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.wallet.viewmodels.PortfolioViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.GemDropdownMenu
import com.gemwallet.android.ui.components.GemDropdownMenuDivider
import com.gemwallet.android.ui.components.GemDropdownMenuItem
import com.gemwallet.android.ui.components.RefreshOnTimer
import com.gemwallet.android.ui.components.TabsBar
import com.gemwallet.android.ui.components.chart.ChartSection
import com.gemwallet.android.ui.components.list_item.GemListRowView
import com.gemwallet.android.ui.components.list_item.SubheaderItem
import com.gemwallet.android.ui.components.list_item.property.itemsPositioned
import com.gemwallet.android.ui.components.screen.PullToRefreshBox
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.stringRes
import com.wallet.core.primitives.PortfolioType
import uniffi.gemstone.GemChartPhase
import uniffi.gemstone.PortfolioChartType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(onCancel: () -> Unit, viewModel: PortfolioViewModel = hiltViewModel()) {
    val refreshIntervalMillis by viewModel.refreshIntervalMillis.collectAsStateWithLifecycle()
    RefreshOnTimer(refreshIntervalMillis, viewModel::refresh)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scene(
        titleContent = {
            if (uiState.showSegmentedControl) {
                PortfolioTypeSelector(selected = uiState.selectedType, onSelect = viewModel::setType)
            } else {
                Text(stringResource(uiState.selectedType.stringRes()))
            }
        },
        onClose = onCancel,
        actions = {
            if (uiState.showChartTypePicker) {
                ChartTypeSelector(selected = uiState.selectedChartType, onSelect = viewModel::setChartType)
            }
        },
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            containerColor = PullToRefreshDefaults.indicatorContainerColor,
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "portfolio_chart", contentType = "portfolio_chart") {
                    ChartSection(
                        state = uiState.chartUIState,
                        onPeriodSelect = viewModel::setPeriod,
                        onZoom = viewModel::onZoom,
                        onPan = viewModel::onPan,
                    )
                }
                if (uiState.chartUIState.phase is GemChartPhase.Data || uiState.chartUIState.phase is GemChartPhase.NoData) {
                    if (uiState.statistics.isNotEmpty()) {
                        item(key = "portfolio_info_header", contentType = "subheader") { SubheaderItem(R.string.common_info) }
                        itemsPositioned(
                            items = uiState.statistics,
                            key = { index, _ -> "portfolio_stat_$index" },
                            contentType = { _, _ -> "portfolio_stat" },
                        ) { position, row -> GemListRowView(row = row, listPosition = position) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioTypeSelector(selected: PortfolioType, onSelect: (PortfolioType) -> Unit) {
    TabsBar(PortfolioType.entries, selected, onSelect) { type ->
        Text(stringResource(type.stringRes()))
    }
}

@Composable
private fun ChartTypeSelector(selected: PortfolioChartType, onSelect: (PortfolioChartType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(selected.stringRes()),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = null,
                )
            }
        }
        GemDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PortfolioChartType.entries.forEachIndexed { index, type ->
                if (index > 0) GemDropdownMenuDivider()
                GemDropdownMenuItem(
                    text = stringResource(type.stringRes()),
                    isSelected = type == selected,
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
