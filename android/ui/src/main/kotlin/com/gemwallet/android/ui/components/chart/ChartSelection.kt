package com.gemwallet.android.ui.components.chart

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FADE_IN_MS = 150
private const val FADE_OUT_MS = 200
private const val HAPTIC_MIN_INTERVAL_MS = 45L
private const val HAPTIC_LEGACY_TICK_MS = 10L

@Stable
class ChartSelection internal constructor() {

    private val fade = Animatable(0f)

    val alpha: Float
        get() = fade.value

    internal suspend fun fadeIn() = fade.animateTo(1f, animationSpec = tween(FADE_IN_MS))

    internal suspend fun fadeOut() = fade.animateTo(0f, animationSpec = tween(FADE_OUT_MS))
}

@Composable
fun rememberChartSelection(selectedIndex: Int?): ChartSelection {
    val currentIndex by rememberUpdatedState(selectedIndex)
    return rememberChartSelection { currentIndex }
}

@Composable
internal fun rememberChartSelection(selectedIndexProvider: () -> Int?): ChartSelection {
    val selection = remember { ChartSelection() }
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val vibrator = remember(context, isInspection) {
        if (isInspection) null else resolveVibrator(context)
    }
    val provider by rememberUpdatedState(selectedIndexProvider)
    LaunchedEffect(selection, vibrator) {
        val hapticTicks = if (vibrator != null) Channel<Unit>(Channel.CONFLATED) else null
        val hapticJob = if (vibrator != null && hapticTicks != null) {
            launch(Dispatchers.IO) {
                var lastTickMs = 0L
                val effect = createTickEffect()
                for (tick in hapticTicks) {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = now - lastTickMs
                    if (elapsed < HAPTIC_MIN_INTERVAL_MS) {
                        delay(HAPTIC_MIN_INTERVAL_MS - elapsed)
                    }
                    lastTickMs = SystemClock.uptimeMillis()
                    triggerScrubTick(context, vibrator, effect)
                }
            }
        } else {
            null
        }
        var previousIndex: Int? = null
        var fadeJob: Job? = null
        try {
            snapshotFlow { provider() }.collect { current ->
                val previous = previousIndex
                previousIndex = current
                if (current != null) {
                    if (previous != null && previous != current) {
                        hapticTicks?.trySend(Unit)
                    }
                    if (previous == null) {
                        fadeJob?.cancel()
                        fadeJob = launch { selection.fadeIn() }
                    }
                } else if (previous != null) {
                    hapticTicks?.tryReceive()
                    fadeJob?.cancel()
                    fadeJob = launch { selection.fadeOut() }
                }
            }
        } finally {
            hapticTicks?.close()
            hapticJob?.cancel()
        }
    }
    return selection
}

private fun resolveVibrator(context: Context): Vibrator? {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
    return vibrator?.takeIf { it.hasVibrator() }
}

private fun createTickEffect(): VibrationEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
} else {
    VibrationEffect.createOneShot(HAPTIC_LEGACY_TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
}

private fun triggerScrubTick(context: Context, vibrator: Vibrator, effect: VibrationEffect) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else if (isLegacyHapticFeedbackEnabled(context)) {
        vibrator.vibrate(effect)
    }
}

@Suppress("DEPRECATION")
private fun isLegacyHapticFeedbackEnabled(context: Context): Boolean = Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
