package com.gemwallet.android.features.swap.presents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemwallet.android.domains.swap.SwapItemType
import com.gemwallet.android.features.swap.presents.components.SwapButton
import com.gemwallet.android.features.swap.presents.components.SwapQuoteError
import com.gemwallet.android.features.swap.presents.components.SwapToken
import com.gemwallet.android.features.swap.viewmodels.SwapViewModel
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.PercentSuggestionsBar
import com.gemwallet.android.ui.components.buttons.IndicatorButton
import com.gemwallet.android.ui.components.isKeyboardVisible
import com.gemwallet.android.ui.components.list_item.sectionHeaderItem
import com.gemwallet.android.ui.components.screen.MainActionWidth
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.components.swap.SwapDetailsSummaryItem
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.alpha10
import com.gemwallet.android.ui.theme.alpha40
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.sceneContentPadding
import com.gemwallet.android.ui.theme.space0
import com.wallet.core.primitives.AssetData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uniffi.gemstone.GemSwapViewState

private val switchButtonSize = 44.dp
private val switchHaloSize = 36.dp
private const val SWITCH_PRESSED_SCALE = 0.82f
private const val SWITCH_HALO_REST_SCALE = 0.76f
private const val SWITCH_HALO_EXIT_SCALE = 1.14f
private const val SWITCH_HALO_PULSE_THRESHOLD = 0.5f
private const val SWITCH_ROTATION_STEP_DEGREES = 180f
private const val SWITCH_PRESS_IN_MS = 110
private const val SWITCH_QUICK_TAP_IN_MS = 75
private const val SWITCH_HALO_ENTER_MS = 120
private const val SWITCH_HALO_EXIT_MS = 260
private const val SWITCH_ROTATION_VISIBILITY_THRESHOLD = 0.5f
private const val SWITCH_SCALE_VISIBILITY_THRESHOLD = 0.001f

private val switchPressInSpec = tween<Float>(
    durationMillis = SWITCH_PRESS_IN_MS,
    easing = FastOutSlowInEasing,
)
private val switchQuickTapInSpec = tween<Float>(
    durationMillis = SWITCH_QUICK_TAP_IN_MS,
    easing = FastOutSlowInEasing,
)
private val switchPressOutSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = SWITCH_SCALE_VISIBILITY_THRESHOLD,
)
private val switchHaloEnterSpec = tween<Float>(
    durationMillis = SWITCH_HALO_ENTER_MS,
    easing = FastOutSlowInEasing,
)
private val switchHaloExitSpec = tween<Float>(
    durationMillis = SWITCH_HALO_EXIT_MS,
    easing = FastOutSlowInEasing,
)
private val switchRotationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = SWITCH_ROTATION_VISIBILITY_THRESHOLD,
)

@Composable
internal fun SwapScene(viewState: GemSwapViewState, pay: AssetData?, receive: AssetData?, payValue: TextFieldState, showsSlippageIndicator: Boolean, onAction: (SwapAction) -> Unit) {
    val focusManager = LocalFocusManager.current
    fun clearAmountFocus() {
        focusManager.clearFocus(force = true)
    }
    val isKeyboardVisible = WindowInsets.isKeyboardVisible
    val isPercentBarVisible = isKeyboardVisible && pay != null && viewState.isInputEmpty

    Scene(
        title = stringResource(id = R.string.wallet_swap),
        actions = {
            IndicatorButton(
                imageVector = AppIcons.Tune,
                contentDescription = stringResource(R.string.swap_slippage),
                showsIndicator = showsSlippageIndicator,
                onClick = { onAction(SwapAction.Slippage) },
            )
        },
        mainActionWidth = if (isPercentBarVisible) MainActionWidth.FillWidth else MainActionWidth.Constrained,
        mainActionPadding = PaddingValues(
            horizontal = sceneContentPadding(),
            vertical = if (isPercentBarVisible) paddingSmall else paddingDefault,
        ),
        mainAction = {
            if (isPercentBarVisible) {
                PercentSuggestionsBar(
                    suggestions = SwapViewModel.percentSuggestions,
                    onPercentSelected = {
                        clearAmountFocus()
                        onAction(SwapAction.SelectPercent(it))
                    },
                )
            } else if (viewState.showsButton) {
                SwapButton(
                    viewState = viewState,
                    pay = pay,
                    onSwap = {
                        clearAmountFocus()
                        onAction(SwapAction.Swap)
                    },
                )
            }
        },
        onClose = { onAction(SwapAction.Cancel) },
    ) {
        LaunchedEffect(pay) {
            if (pay == null) {
                clearAmountFocus()
            }
        }
        LazyColumn {
            item {
                SwapSectionHeader(R.string.swap_you_pay)
            }
            item {
                SwapToken(
                    item = pay,
                    side = viewState.pay,
                    state = payValue,
                    onBalanceClick = {
                        clearAmountFocus()
                        onAction(SwapAction.SelectPercent(100))
                    },
                    onAssetSelect = {
                        clearAmountFocus()
                        onAction(SwapAction.SelectAsset(SwapItemType.Pay))
                    },
                )
            }
            item {
                SwapReceiveHeader(
                    enabled = !viewState.isTransferLoading,
                    onSwitch = { onAction(SwapAction.SwitchAssets) },
                )
            }
            item {
                SwapToken(
                    item = receive,
                    side = viewState.receive,
                    calculating = viewState.isReceiveLoading,
                    quote = viewState.receiveAmount?.text(),
                    isQuote = true,
                    onBalanceClick = {},
                    onAssetSelect = {
                        clearAmountFocus()
                        onAction(SwapAction.SelectAsset(SwapItemType.Receive))
                    },

                )
            }
            item {
                viewState.details?.takeIf { viewState.showsDetails }?.let {
                    SwapDetailsSummaryItem(details = it, onClick = { onAction(SwapAction.ShowDetails) })
                }
            }

            item {
                SwapQuoteError(viewState)
            }
        }
    }
}

@Composable
private fun SwapReceiveHeader(enabled: Boolean, onSwitch: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SwapSectionHeader(
            resId = R.string.swap_you_receive,
            modifier = Modifier.fillMaxWidth(),
            topPadding = space0,
        )
        SwitchButton(enabled = enabled, onClick = onSwitch)
    }
}

@Composable
private fun SwitchButton(enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var rotationTarget by remember { mutableFloatStateOf(0f) }
    var tapCount by remember { mutableIntStateOf(0) }
    var handledTapCount by remember { mutableIntStateOf(0) }
    val pressScale = remember { Animatable(1f) }
    val haloAlpha = remember { Animatable(0f) }
    val haloScale = remember { Animatable(SWITCH_HALO_REST_SCALE) }
    val rotationState = animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = switchRotationSpec,
        label = "swap_switch_rotation",
    )
    val haloColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha10)

    LaunchedEffect(isPressed, tapCount) {
        if (isPressed) {
            coroutineScope {
                launch { pressScale.animateTo(SWITCH_PRESSED_SCALE, switchPressInSpec) }
                launch { haloAlpha.animateTo(1f, switchHaloEnterSpec) }
                launch { haloScale.animateTo(1f, switchHaloEnterSpec) }
            }
            return@LaunchedEffect
        }
        val isNewTap = tapCount != handledTapCount
        if (isNewTap) {
            handledTapCount = tapCount
            if (haloAlpha.value < SWITCH_HALO_PULSE_THRESHOLD) {
                coroutineScope {
                    launch { pressScale.animateTo(SWITCH_PRESSED_SCALE, switchQuickTapInSpec) }
                    launch { haloAlpha.animateTo(1f, switchQuickTapInSpec) }
                    launch { haloScale.animateTo(1f, switchQuickTapInSpec) }
                }
            }
        }
        if (isNewTap || haloAlpha.value > 0f || pressScale.value != 1f) {
            coroutineScope {
                launch { pressScale.animateTo(1f, switchPressOutSpec) }
                launch { haloAlpha.animateTo(0f, switchHaloExitSpec) }
                launch { haloScale.animateTo(SWITCH_HALO_EXIT_SCALE, switchHaloExitSpec) }
            }
            haloScale.snapTo(SWITCH_HALO_REST_SCALE)
        }
    }

    Box(
        modifier = Modifier
            .size(switchButtonSize)
            .clip(CircleShape)
            .testTag("swap_switch")
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    rotationTarget += SWITCH_ROTATION_STEP_DEGREES
                    tapCount++
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(switchHaloSize)
                .graphicsLayer {
                    alpha = haloAlpha.value
                    val scale = haloScale.value
                    scaleX = scale
                    scaleY = scale
                }
                .background(haloColor, CircleShape),
        )
        Icon(
            modifier = Modifier.graphicsLayer {
                val scale = pressScale.value
                scaleX = scale
                scaleY = scale
                rotationZ = rotationState.value
            },
            imageVector = AppIcons.SwapVert,
            contentDescription = stringResource(R.string.wallet_swap),
            tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else alpha40),
        )
    }
}

@Composable
private fun SwapSectionHeader(resId: Int, modifier: Modifier = Modifier, topPadding: Dp? = null) {
    Text(
        modifier = modifier
            .sectionHeaderItem(paddingVertical = topPadding),
        text = stringResource(resId),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}
