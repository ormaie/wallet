package com.gemwallet.android.ui.components.screen

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.components.sensitiveData
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.adaptivePadding
import com.gemwallet.android.ui.theme.hairlineThickness
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.isLightSurface
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.plainContainer
import com.gemwallet.android.ui.theme.space10
import com.gemwallet.android.ui.theme.space8
import uniffi.gemstone.GemSecretPhraseRow
import uniffi.gemstone.secretPhraseRows

private const val TabularNumbersFeature = "tnum"
private const val OrdinalDigits = 2
private const val HighlightAlphaLight = 0.07f
private const val HighlightAlphaDark = 0.15f
private const val OrdinalMutedAlpha = 0.72f
private const val CellTransitionMs = 180
private val ordinalMinWidth = 22.dp
private val cellVerticalPadding = 12.dp
private val cellMinHeightDefault = 48.dp
private val cellMinHeightCompact = 40.dp
private val wordLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Composable
fun PhraseLayout(words: List<String>, modifier: Modifier = Modifier, highlightIndex: Int? = null) {
    val rows = remember(words.size) { secretPhraseRows(words.size) }
    if (rows.isEmpty()) {
        return
    }
    val isLight = MaterialTheme.colorScheme.isLightSurface
    val containerColor = MaterialTheme.colorScheme.plainContainer
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("phrase_layout"),
        shape = RoundedCornerShape(paddingDefault),
        color = containerColor,
        border = BorderStroke(hairlineThickness, dividerColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(thickness = hairlineThickness, color = dividerColor)
                }
                when (row) {
                    is GemSecretPhraseRow.Pair -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        PhraseWordCell(
                            index = row.left,
                            word = words[row.left],
                            isHighlighted = row.left == highlightIndex,
                            isLight = isLight,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = hairlineThickness,
                            color = dividerColor,
                        )
                        PhraseWordCell(
                            index = row.right,
                            word = words[row.right],
                            isHighlighted = row.right == highlightIndex,
                            isLight = isLight,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    is GemSecretPhraseRow.Single -> PhraseWordCell(
                        index = row.index,
                        word = words[row.index],
                        isHighlighted = row.index == highlightIndex,
                        isLight = isLight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseWordCell(index: Int, word: String, isHighlighted: Boolean, isLight: Boolean, modifier: Modifier = Modifier) {
    val horizontalPadding = adaptivePadding(default = paddingDefault, compact = paddingMiddle)
    val verticalPadding = adaptivePadding(default = cellVerticalPadding, compact = space8)
    val minHeight = if (isCompactDimension(WindowDimension.Width)) cellMinHeightCompact else cellMinHeightDefault
    val highlightAlpha = if (isLight) HighlightAlphaLight else HighlightAlphaDark
    val cellBackground by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = tween(CellTransitionMs),
        label = "phrase_cell_background",
    )
    val ordinalColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = OrdinalMutedAlpha)
        },
        animationSpec = tween(CellTransitionMs),
        label = "phrase_cell_ordinal",
    )
    val wordAlpha by animateFloatAsState(
        targetValue = if (word.isEmpty()) 0f else 1f,
        animationSpec = tween(CellTransitionMs),
        label = "phrase_cell_word_alpha",
    )

    Row(
        modifier = modifier
            .background(cellBackground)
            .heightIn(min = minHeight)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(OrdinalDigits, '0'),
            modifier = Modifier
                .widthIn(min = ordinalMinWidth)
                .sensitiveData(),
            color = ordinalColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TabularNumbersFeature,
            ),
        )
        Spacer(modifier = Modifier.width(space10))
        Text(
            text = word,
            modifier = Modifier
                .weight(1f)
                .testTag("word_$index")
                .sensitiveData()
                .graphicsLayer { alpha = wordAlpha },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeightStyle = wordLineHeightStyle),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPhraseLayout() {
    WalletTheme {
        PhraseLayout(
            words = listOf(
                "alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
                "golf", "hotel", "india", "juliet", "kilo", "lima",
            ),
            highlightIndex = 2,
        )
    }
}
