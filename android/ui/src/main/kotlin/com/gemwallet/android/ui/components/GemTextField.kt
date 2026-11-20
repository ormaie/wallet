package com.gemwallet.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.components.list_item.listItem
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.theme.Spacer4
import com.gemwallet.android.ui.theme.paddingDefault

private const val FLOATING_LABEL_FADE_IN_MS = 180
private const val FLOATING_LABEL_FADE_OUT_MS = 140
private val minInputHeight = 40.dp
private val floatingLabelExpandSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val floatingLabelShrinkSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Composable
fun GemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    floatingLabel: (@Composable () -> Unit)? = null,
    error: String = "",
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    listPosition: ListPosition = ListPosition.Single,
    errorDivider: Boolean = false,
) {
    val hasFloatingLabel = value.isNotEmpty() && label.isNotEmpty()
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (textFieldValue.text != value) {
        textFieldValue = TextFieldValue(value, TextRange(value.length))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .listItem(listPosition)
            .padding(paddingDefault),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.heightIn(min = minInputHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                modifier = Modifier.weight(1f),
                value = textFieldValue,
                onValueChange = { next ->
                    textFieldValue = next
                    if (next.text != value) onValueChange(next.text)
                },
                readOnly = readOnly,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardActions = keyboardActions,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedVisibility(
                            modifier = Modifier.fillMaxWidth(),
                            visible = hasFloatingLabel,
                            enter = expandIn(
                                expandFrom = Alignment.TopStart,
                                animationSpec = floatingLabelExpandSpring,
                                initialSize = { IntSize(it.width, 0) },
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = FLOATING_LABEL_FADE_IN_MS),
                            ),
                            exit = shrinkOut(
                                shrinkTowards = Alignment.TopStart,
                                animationSpec = floatingLabelShrinkSpring,
                                targetSize = { IntSize(it.width, 0) },
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = FLOATING_LABEL_FADE_OUT_MS),
                            ),
                        ) {
                            if (floatingLabel != null) {
                                floatingLabel()
                            } else {
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Box(contentAlignment = Alignment.CenterStart) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = label.isNotEmpty() && !hasFloatingLabel,
                                enter = fadeIn(animationSpec = tween(durationMillis = FLOATING_LABEL_FADE_IN_MS)),
                                exit = ExitTransition.None,
                            ) {
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            innerTextField()
                        }
                    }
                },
            )
            trailing?.invoke()
        }
        if (error.isNotEmpty()) {
            if (errorDivider) {
                Spacer4()
                HorizontalDivider()
            }
            Spacer4()
            Text(
                modifier = Modifier,
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
