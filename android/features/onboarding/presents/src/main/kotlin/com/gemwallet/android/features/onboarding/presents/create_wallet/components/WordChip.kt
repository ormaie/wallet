package com.gemwallet.android.features.onboarding.presents.create_wallet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemwallet.android.features.onboarding.presents.create_wallet.onboardingNeutralFillColor
import com.gemwallet.android.ui.components.sensitiveData
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.space10
import kotlinx.coroutines.delay

private const val ChipFadeDurationMs = 150
private const val RejectColorEnterMs = 100
private const val RejectColorExitMs = 160
private const val RejectNudgeOutMs = 80
private const val RejectHoldMs = 140L
private val rejectNudgeDistance = 4.5.dp
private val wordChipHeightDefault = 40.dp
private val wordChipHeightCompact = 36.dp
private val wordChipMinFontSize = 11.sp
private val wordChipMaxFontSize = 14.sp

@Composable
internal fun WordChip(word: String, isEnable: Boolean, modifier: Modifier = Modifier, onClick: () -> Boolean) {
    val haptic = LocalHapticFeedback.current
    val nudgeDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val nudgeProgress = remember { Animatable(0f) }
    var rejectTrigger by remember { mutableIntStateOf(0) }
    var isRejected by remember { mutableStateOf(false) }
    val chipHeight = if (isCompactDimension(WindowDimension.Height)) wordChipHeightCompact else wordChipHeightDefault
    val shape = RoundedCornerShape(space10)
    val textColor: Color by animateColorAsState(
        targetValue = if (isRejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(if (isRejected) RejectColorEnterMs else RejectColorExitMs),
        label = "word_chip_text",
    )

    LaunchedEffect(rejectTrigger) {
        if (rejectTrigger == 0) {
            return@LaunchedEffect
        }
        isRejected = true
        nudgeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = RejectNudgeOutMs, easing = FastOutSlowInEasing),
        )
        nudgeProgress.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        )
        delay(RejectHoldMs)
        isRejected = false
    }

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
                    .graphicsLayer {
                        translationX = nudgeProgress.value * rejectNudgeDistance.toPx() * nudgeDirection
                    }
                    .clip(shape)
                    .testTag("word_option")
                    .clickable(enabled = isEnable) {
                        if (!isEnable) {
                            return@clickable
                        }
                        if (onClick()) {
                            isRejected = false
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            rejectTrigger++
                        }
                    }
                    .semantics { if (!isEnable) disabled() },
                shape = shape,
                color = onboardingNeutralFillColor(),
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
