package com.gemwallet.android.ui.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.compactIconSize
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.sceneContentPadding
import com.gemwallet.android.ui.theme.secondaryFaded
import com.gemwallet.android.ui.theme.space6
import com.gemwallet.android.ui.theme.space8

private val minAmountFontSize = 18.sp

fun decimalKeyboardOptions(keyboardType: KeyboardType = KeyboardType.Decimal, imeAction: ImeAction = ImeAction.Default) = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = keyboardType,
    imeAction = imeAction,
    platformImeOptions = PlatformImeOptions(privateImeOptions = "disableToolbar=true"),
)

@Composable
fun ColumnScope.AmountField(
    state: TextFieldState,
    symbol: AmountSymbolUIModel,
    equivalent: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onInputTypeClick: (() -> Unit)? = null,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    maximumFractionDigits: Int? = null,
    error: String,
    errorInfo: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val internalFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val baselineMinHeight = with(density) {
        if (textStyle.lineHeight.isSp) {
            textStyle.lineHeight.toDp()
        } else {
            textStyle.fontSize.toDp()
        }
    }
    val displayText = state.text.ifEmpty { "0" }.toString()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sceneContentPadding()),
        ) {
            val fittedStyle = if (constraints.hasBoundedWidth) {
                rememberFittedAmountTextStyle(
                    text = displayText,
                    symbol = symbol.symbol,
                    maxWidthPx = constraints.maxWidth.toFloat(),
                    textStyle = textStyle,
                )
            } else {
                textStyle
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = baselineMinHeight)
                    .then(
                        if (readOnly) {
                            Modifier
                        } else {
                            Modifier.pointerInput(internalFocusRequester, keyboardController) {
                                detectTapGestures {
                                    internalFocusRequester.requestFocusIfAttached()
                                    keyboardController?.show()
                                }
                            }
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(paddingSmall, Alignment.CenterHorizontally),
            ) {
                if (symbol.placement == AmountSymbolPlacement.Leading) {
                    AmountSymbol(symbol.symbol, fittedStyle)
                }
                BasicTextField(
                    modifier = modifier
                        .focusRequester(internalFocusRequester)
                        .weight(1f, fill = false)
                        .width(IntrinsicSize.Min),
                    state = state,
                    readOnly = readOnly,
                    inputTransformation = AmountInputTransformation(maximumFractionDigits),
                    outputTransformation = ZeroPlaceholder,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = fittedStyle.copy(
                        textAlign = TextAlign.Center,
                        color = if (state.text.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = decimalKeyboardOptions(keyboardType, ImeAction.Next),
                    onKeyboardAction = { onNext() },
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                if (symbol.placement == AmountSymbolPlacement.Trailing) {
                    AmountSymbol(symbol.symbol, fittedStyle)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(paddingSmall))
    if (equivalent.isNotEmpty()) {
        Row(
            modifier = if (onInputTypeClick == null) {
                Modifier
            } else {
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(role = Role.Button, onClick = onInputTypeClick)
                    .padding(horizontal = paddingMiddle, vertical = space6)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(paddingSmall),
        ) {
            Text(
                modifier = Modifier.testTag("amount_equivalent"),
                text = equivalent,
                color = MaterialTheme.colorScheme.secondary,
            )
            onInputTypeClick?.let {
                Icon(
                    modifier = Modifier.size(compactIconSize),
                    painter = painterResource(R.drawable.amount_switch),
                    tint = MaterialTheme.colorScheme.secondary,
                    contentDescription = null,
                )
            }
        }
    }
    error.takeIf { it.isNotEmpty() }?.run {
        Spacer(modifier = Modifier.height(paddingSmall))
        Row(
            modifier = Modifier
                .padding(horizontal = paddingDefault)
                .then(
                    if (errorInfo != null) {
                        Modifier
                            .clip(RoundedCornerShape(paddingSmall))
                            .clickable(role = Role.Button, onClick = errorInfo)
                            .padding(horizontal = paddingSmall, vertical = paddingHalfSmall)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(paddingSmall),
        ) {
            if (errorInfo != null) {
                Icon(
                    modifier = Modifier.size(compactIconSize),
                    imageVector = AppIcons.InfoOutlined,
                    contentDescription = stringResource(R.string.common_learn_more),
                    tint = MaterialTheme.colorScheme.secondaryFaded,
                )
            }
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun rememberFittedAmountTextStyle(text: String, symbol: String, maxWidthPx: Float, textStyle: TextStyle): TextStyle {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacingPx = with(density) {
        val symbolGap = if (symbol.isNotEmpty()) paddingSmall.toPx() else 0f
        symbolGap + space8.toPx()
    }
    val availableWidthPx = (maxWidthPx - spacingPx).coerceAtLeast(0f)
    return remember(text, symbol, availableWidthPx, textStyle, density.density, density.fontScale) {
        if (!textStyle.fontSize.isSp) {
            return@remember textStyle
        }
        val maxSp = textStyle.fontSize.value
        val minSp = minOf(minAmountFontSize.value, maxSp)
        val fittedSp = fitAmountFontSize(
            maxFontSizeSp = maxSp,
            minFontSizeSp = minSp,
            availableWidthPx = availableWidthPx,
        ) { candidateSp ->
            val scaledStyle = textStyle.scaleTo(candidateSp.sp)
            val amountWidth = textMeasurer.measure(
                text = text,
                style = scaledStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width
            val symbolWidth = if (symbol.isNotEmpty()) {
                textMeasurer.measure(
                    text = symbol,
                    style = scaledStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width
            } else {
                0
            }
            (amountWidth + symbolWidth).toFloat()
        }
        textStyle.scaleTo(fittedSp.sp)
    }
}

internal fun fitAmountFontSize(maxFontSizeSp: Float, minFontSizeSp: Float, availableWidthPx: Float, measureWidthPx: (Float) -> Float): Float {
    if (availableWidthPx <= 0f || maxFontSizeSp <= minFontSizeSp) {
        return maxFontSizeSp
    }
    if (measureWidthPx(maxFontSizeSp) <= availableWidthPx) {
        return maxFontSizeSp
    }
    var low = minFontSizeSp.toInt()
    var high = maxFontSizeSp.toInt()
    var best = minFontSizeSp
    while (low <= high) {
        val mid = (low + high) ushr 1
        val candidate = mid.toFloat().coerceIn(minFontSizeSp, maxFontSizeSp)
        if (measureWidthPx(candidate) <= availableWidthPx) {
            best = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

private fun TextStyle.scaleTo(targetFontSize: TextUnit): TextStyle {
    if (!fontSize.isSp || fontSize == targetFontSize) {
        return this
    }
    val scaledLineHeight = if (lineHeight.isSp) {
        (lineHeight.value * (targetFontSize.value / fontSize.value)).sp
    } else {
        lineHeight
    }
    return copy(fontSize = targetFontSize, lineHeight = scaledLineHeight)
}

@Composable
private fun AmountSymbol(symbol: String, textStyle: TextStyle) {
    Text(
        text = symbol,
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

private val ZeroPlaceholder = OutputTransformation {
    if (length == 0) {
        append("0")
    }
}
