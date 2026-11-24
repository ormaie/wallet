package com.gemwallet.android.ui.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.alpha50
import com.gemwallet.android.ui.theme.mainActionHeight
import com.gemwallet.android.ui.theme.neutralFill
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.space6
import kotlinx.coroutines.delay

private const val CopyFeedbackDurationMs = 1_500L
private const val CopyFeedbackAnimationMs = 180
private const val CopyIconInitialScale = 0.6f
private const val CopySuccessBackgroundAlpha = 0.12f
private val copyButtonMinWidth = 112.dp
private val copyButtonIconSize = 18.dp

@Composable
fun CopyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var copyTrigger by remember { mutableIntStateOf(0) }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) {
            delay(CopyFeedbackDurationMs)
            isCopied = false
        }
    }

    val neutralFill = MaterialTheme.colorScheme.neutralFill
    val containerColor by animateColorAsState(
        targetValue = if (isCopied) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = CopySuccessBackgroundAlpha)
        } else {
            neutralFill
        },
        animationSpec = tween(CopyFeedbackAnimationMs),
        label = "copy_button_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isCopied) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(CopyFeedbackAnimationMs),
        label = "copy_button_content",
    )

    Button(
        onClick = {
            onClick()
            isCopied = true
            copyTrigger++
        },
        enabled = enabled,
        shape = CircleShape,
        elevation = null,
        contentPadding = PaddingValues(horizontal = paddingDefault),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = neutralFill.copy(alpha = alpha50),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha50),
        ),
        modifier = modifier
            .heightIn(min = mainActionHeight)
            .widthIn(min = copyButtonMinWidth),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space6),
        ) {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = {
                    (fadeIn(tween(CopyFeedbackAnimationMs)) + scaleIn(tween(CopyFeedbackAnimationMs), initialScale = CopyIconInitialScale))
                        .togetherWith(fadeOut(tween(CopyFeedbackAnimationMs)) + scaleOut(tween(CopyFeedbackAnimationMs), targetScale = CopyIconInitialScale))
                },
                label = "copy_button_icon",
            ) { copied ->
                Icon(
                    imageVector = if (copied) AppIcons.Check else AppIcons.ContentCopyOutlined,
                    contentDescription = null,
                    modifier = Modifier.size(copyButtonIconSize),
                )
            }
            Text(
                text = stringResource(if (isCopied) R.string.wallet_copied else R.string.common_copy),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
