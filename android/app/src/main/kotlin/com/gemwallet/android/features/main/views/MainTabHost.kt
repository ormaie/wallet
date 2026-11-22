package com.gemwallet.android.features.main.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gemwallet.android.ui.navigation.WalletRootLifecycleOwner

private const val TAB_TRANSITION_DURATION_MS = 220
private const val TAB_FADE_OUT_FRACTION = 80f / 220f
private const val TAB_FADE_IN_DELAY_FRACTION = 80f / 220f
private const val TAB_FADE_IN_SPAN_FRACTION = 140f / 220f
private const val TAB_SLIDE_OFFSET_FRACTION = 0.05f

internal class PersistentTabHostState(initialTab: String) {
    private val mounted = mutableStateListOf(initialTab)
    val mountedTabs: List<String>
        get() = mounted

    var activeTab by mutableStateOf(initialTab)
        private set
    var exitingTab by mutableStateOf<String?>(null)
        private set
    var transitionDirection by mutableIntStateOf(0)
        private set
    var isTransitionPending by mutableStateOf(false)
        private set

    fun selectTab(nextTab: String, tabIndex: (String) -> Int): Boolean {
        if (nextTab !in mounted) {
            mounted.add(nextTab)
        }
        val previous = activeTab
        if (nextTab == previous) {
            return false
        }
        val previousIndex = tabIndex(previous)
        val nextIndex = tabIndex(nextTab)
        activeTab = nextTab
        if (previousIndex >= 0 && nextIndex >= 0 && previousIndex != nextIndex) {
            exitingTab = previous
            transitionDirection = if (nextIndex > previousIndex) 1 else -1
            isTransitionPending = true
            return true
        }
        exitingTab = null
        transitionDirection = 0
        isTransitionPending = false
        return false
    }

    fun markTransitionStarted() {
        isTransitionPending = false
    }

    fun finishTransition() {
        exitingTab = null
        transitionDirection = 0
        isTransitionPending = false
    }
}

internal fun tabEnterAlpha(progress: Float): Float {
    val fraction = ((progress - TAB_FADE_IN_DELAY_FRACTION) / TAB_FADE_IN_SPAN_FRACTION).coerceIn(0f, 1f)
    return LinearOutSlowInEasing.transform(fraction)
}

internal fun tabExitAlpha(progress: Float): Float {
    val fraction = (progress / TAB_FADE_OUT_FRACTION).coerceIn(0f, 1f)
    return 1f - FastOutLinearInEasing.transform(fraction)
}

internal fun tabSlideProgress(progress: Float): Float = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))

internal fun shouldPlaceTab(isActive: Boolean, isExiting: Boolean): Boolean = isActive || isExiting

internal fun isTabVisibleDuringTransition(progress: Float, isActive: Boolean): Boolean = if (isActive) {
    progress >= TAB_FADE_IN_DELAY_FRACTION
} else {
    progress < TAB_FADE_OUT_FRACTION
}

internal fun tabSurfaceVeilAlpha(progress: Float, isActive: Boolean): Float {
    val contentAlpha = if (isActive) tabEnterAlpha(progress) else tabExitAlpha(progress)
    return (1f - contentAlpha).coerceIn(0f, 1f)
}

@Composable
internal fun PersistentTabHost(
    hostState: PersistentTabHostState,
    selectedTab: String,
    tabIndex: (String) -> Int,
    saveableStateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val transitionProgress = remember { Animatable(1f) }
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val surfaceColor = MaterialTheme.colorScheme.surface

    LaunchedEffect(selectedTab) {
        if (hostState.activeTab != selectedTab) {
            hostState.selectTab(selectedTab, tabIndex)
        }
        if (hostState.exitingTab != null) {
            transitionProgress.snapTo(0f)
            hostState.markTransitionStarted()
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TAB_TRANSITION_DURATION_MS, easing = LinearEasing),
            )
            hostState.finishTransition()
        } else {
            transitionProgress.snapTo(1f)
            hostState.markTransitionStarted()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        hostState.mountedTabs.forEach { tab ->
            key(tab) {
                val isActive = tab == hostState.activeTab
                val isExiting = tab == hostState.exitingTab
                val isPlaced = shouldPlaceTab(isActive = isActive, isExiting = isExiting)
                val tabLifecycleOwner = remember(parentLifecycle, tab) {
                    WalletRootLifecycleOwner().apply {
                        updateParentState(parentLifecycle.currentState)
                        updateVisibility(isActive)
                    }
                }

                DisposableEffect(parentLifecycle, tabLifecycleOwner) {
                    val observer = LifecycleEventObserver { source, _ ->
                        tabLifecycleOwner.updateParentState(source.lifecycle.currentState)
                    }
                    parentLifecycle.addObserver(observer)
                    onDispose {
                        parentLifecycle.removeObserver(observer)
                        tabLifecycleOwner.destroy()
                    }
                }

                DisposableEffect(tabLifecycleOwner, isActive) {
                    tabLifecycleOwner.updateVisibility(isActive)
                    onDispose {}
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = isActive }
                        .then(if (isActive) Modifier else Modifier.clearAndSetSemantics {})
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            if (!isPlaced) {
                                layout(placeable.width, placeable.height) {}
                            } else if (hostState.exitingTab == null) {
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, 0, zIndex = 1f)
                                }
                            } else {
                                val zIndex = if (isActive) 1f else 0f
                                val rtlSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                layout(placeable.width, placeable.height) {
                                    placeable.placeWithLayer(0, 0, zIndex = zIndex) {
                                        val rawProgress = if (hostState.isTransitionPending) 0f else transitionProgress.value
                                        val visible = isTabVisibleDuringTransition(rawProgress, isActive)
                                        alpha = if (visible) 1f else 0f
                                        if (visible) {
                                            val slide = tabSlideProgress(rawProgress)
                                            val distance = size.width * TAB_SLIDE_OFFSET_FRACTION * hostState.transitionDirection * rtlSign
                                            translationX = if (isActive) {
                                                distance * (1f - slide)
                                            } else {
                                                -distance * slide
                                            }
                                        } else {
                                            translationX = 0f
                                        }
                                    }
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (hostState.exitingTab != null && isPlaced) {
                                val rawProgress = if (hostState.isTransitionPending) 0f else transitionProgress.value
                                if (isTabVisibleDuringTransition(rawProgress, isActive)) {
                                    val veilAlpha = tabSurfaceVeilAlpha(rawProgress, isActive)
                                    if (veilAlpha > 0f) {
                                        drawRect(color = surfaceColor.copy(alpha = veilAlpha))
                                    }
                                }
                            }
                        },
                ) {
                    CompositionLocalProvider(LocalLifecycleOwner provides tabLifecycleOwner) {
                        saveableStateHolder.SaveableStateProvider(tab) {
                            content(tab)
                        }
                    }
                }
            }
        }
    }
}
