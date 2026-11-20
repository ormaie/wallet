package com.gemwallet.android.ui.components.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

private const val NavigationDurationMillis = 300
private const val TabTransitionDurationMillis = 220
private const val TabFadeOutDurationMillis = 90
private const val TabFadeInDelayMillis = 60
private const val TabFadeInDurationMillis = 160
private const val TabSlideOffsetFraction = 0.06f

fun <T> AnimatedContentTransitionScope<T>.tabContentTransition(tabIndex: (T) -> Int): ContentTransform {
    val initialIndex = tabIndex(initialState)
    val targetIndex = tabIndex(targetState)
    if (initialIndex == targetIndex || initialIndex < 0 || targetIndex < 0) {
        return EnterTransition.None togetherWith ExitTransition.None using null
    }
    val direction = if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    val slideSpec = tween<IntOffset>(
        durationMillis = TabTransitionDurationMillis,
        easing = FastOutSlowInEasing,
    )
    val enter = slideIntoContainer(
        towards = direction,
        animationSpec = slideSpec,
        initialOffset = { (it * TabSlideOffsetFraction).roundToInt() },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = TabFadeInDurationMillis,
            delayMillis = TabFadeInDelayMillis,
            easing = LinearOutSlowInEasing,
        ),
    )
    val exit = slideOutOfContainer(
        towards = direction,
        animationSpec = slideSpec,
        targetOffset = { (it * TabSlideOffsetFraction).roundToInt() },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = TabFadeOutDurationMillis,
            easing = FastOutLinearInEasing,
        ),
    )
    return enter togetherWith exit using null
}

fun <T> AnimatedContentTransitionScope<T>.navigationSlideTransition(forward: Boolean): ContentTransform = navigationSlideTransition(
    direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    },
)

fun <T> AnimatedContentTransitionScope<T>.navigationSlideTransition(direction: AnimatedContentTransitionScope.SlideDirection): ContentTransform = slideIntoContainer(
    towards = direction,
    animationSpec = tween(NavigationDurationMillis),
) togetherWith slideOutOfContainer(
    towards = direction,
    animationSpec = tween(NavigationDurationMillis),
)
