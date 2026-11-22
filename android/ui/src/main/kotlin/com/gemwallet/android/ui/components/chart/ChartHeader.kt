package com.gemwallet.android.ui.components.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.gemwallet.android.ui.components.NumericText
import com.gemwallet.android.ui.style.color
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.space8
import uniffi.gemstone.GemChartHeader

@Composable
fun ChartHeader(header: GemChartHeader, date: String?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(header, locale) { header.format(locale) }
    ChartHeader(formatted = formatted, date = date, modifier = modifier)
}

@Composable
internal fun ChartHeader(formatted: FormattedChartHeader, date: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val secondaryValue = formatted.secondaryValueText
        secondaryValue?.let { headerValue ->
            NumericText(
                text = headerValue,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val changeStyle = if (secondaryValue != null) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.headlineSmall
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumericText(
                text = formatted.valueText,
                style = changeStyle,
                color = formatted.valueTone.color(),
            )
            val changeText = formatted.changeText
            val changeTone = formatted.changeTone
            if (changeText != null && changeTone != null) {
                Spacer(modifier = Modifier.width(space8))
                NumericText(
                    text = changeText,
                    style = if (secondaryValue != null) changeStyle else MaterialTheme.typography.bodyLarge,
                    color = changeTone.color(),
                )
            }
        }
        Box(
            modifier = Modifier.height(paddingDefault),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
