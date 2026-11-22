package com.gemwallet.android.ui.navigation

import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation3.scene.Scene

internal class NavTransitionTracker {
    private val activeEntries = mutableStateMapOf<Any, () -> Boolean>()

    fun attach(contentKey: Any, isTransitionRunning: () -> Boolean) {
        activeEntries[contentKey] = isTransitionRunning
    }

    fun detach(contentKey: Any, isTransitionRunning: () -> Boolean) {
        if (activeEntries[contentKey] === isTransitionRunning) {
            activeEntries.remove(contentKey)
        }
    }

    fun isTransitionRunning(expectedKeys: List<Any>): Boolean {
        if (activeEntries.size != expectedKeys.size) return true
        return expectedKeys.any { key ->
            val running = activeEntries[key] ?: return true
            running()
        }
    }
}

internal class PredictiveBackGate {
    private var wasGestureInProgress = false
    private var suppressCurrentGesture = false

    fun shouldAllowPredictiveBack(isGestureInProgress: Boolean, isTransitionRunning: () -> Boolean): Boolean {
        if (!isGestureInProgress) {
            wasGestureInProgress = false
            suppressCurrentGesture = false
            return true
        }
        if (!wasGestureInProgress) {
            wasGestureInProgress = true
            suppressCurrentGesture = isTransitionRunning()
        }
        return !suppressCurrentGesture
    }
}

internal fun <T : Any> isScenePop(initialScene: Scene<T>, targetScene: Scene<T>): Boolean {
    val initialKeys = initialScene.stackContentKeys()
    val targetKeys = targetScene.stackContentKeys()
    if (targetKeys.size >= initialKeys.size || targetKeys.isEmpty()) return false
    return targetKeys.indices.all { index -> targetKeys[index] == initialKeys[index] }
}

private fun <T : Any> Scene<T>.stackContentKeys(): List<Any> = previousEntries.map { it.contentKey } + entries.map { it.contentKey }
