package com.gemwallet.android.ui.components.fields

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import com.gemwallet.android.ui.LocalNavTransitionRunning
import kotlinx.coroutines.flow.first

fun FocusRequester.requestFocusIfAttached() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
    }
}

@Composable
fun RequestFocusAfterNavTransition(focusRequester: FocusRequester, enabled: Boolean = true, key: Any? = Unit) {
    val isNavTransitionRunning = LocalNavTransitionRunning.current
    if (enabled) {
        LaunchedEffect(key, focusRequester, isNavTransitionRunning) {
            snapshotFlow(isNavTransitionRunning).first { !it }
            focusRequester.requestFocusIfAttached()
        }
    }
}
