package com.gemwallet.android.features.onboarding.presents.create_wallet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemwallet.android.features.onboarding.presents.create_wallet.onboardingNeutralFillColor
import com.gemwallet.android.ui.components.sensitiveData
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.space10

private const val ChipFadeDurationMs = 150
private const val ChipColorAnimationMs = 140
private val wordChipHeightDefault = 40.dp
private val wordChipHeightCompact = 36.dp
private val wordChipMinFontSize = 11.sp
private val wordChipMaxFontSize = 14.sp

private enum class WordState {
    Error,
    Idle,
}

@Composable
internal fun WordChip(word: String, isEnable: Boolean, modifier: Modifier = Modifier, onClick: () -> Boolean) {
    val shakeController = rememberShakeController()
    val haptic = LocalHapticFeedback.current
    var wordState by remember { mutableStateOf(WordState.Idle) }
    val chipHeight = if (isCompactDimension(WindowDimension.Height)) wordChipHeightCompact else wordChipHeightDefault
    val shape = RoundedCornerShape(space10)
    val idleBackground = onboardingNeutralFillColor()
    val bgColor: Color by animateColorAsState(
        targetValue = if (wordState == WordState.Error) MaterialTheme.colorScheme.error else idleBackground,
        animationSpec = tween(ChipColorAnimationMs),
        label = "word_chip_background",
    )
    val textColor: Color by animateColorAsState(
        targetValue = if (wordState == WordState.Error) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(ChipColorAnimationMs),
        label = "word_chip_text",
    )

    Box(
        modifier = modifier.height(chipHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = isEnable,
            enter = fadeIn(tween(ChipFadeDurationMs)),
            exit = fadeOut(tween(ChipFadeDurationMs)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shake(shakeController)
                    .clip(shape)
                    .testTag("word_option")
                    .clickable(enabled = wordState != WordState.Idle || isEnable) {
                        if (!isEnable) {
                            return@clickable
                        }
                        if (!onClick()) {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            shakeController.shake(
                                ShakeConfig(
                                    iterations = 2,
                                    intensity = 1_000f,
                                    rotateY = 10f,
                                    translateX = 10f,
                                ),
                                onComplete = { wordState = WordState.Idle },
                            )
                            wordState = WordState.Error
                        }
                    }
                    .semantics { if (!isEnable) disabled() },
                shape = shape,
                color = bgColor,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = paddingHalfSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = word,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = wordChipMinFontSize,
                            maxFontSize = wordChipMaxFontSize,
                        ),
                        modifier = Modifier.sensitiveData(),
                    )
                }
            }
        }
    }
}
