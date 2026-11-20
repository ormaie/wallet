package com.gemwallet.android.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics

fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

@Composable
fun Modifier.keyboardFollowsFocus(): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    return onFocusChanged { if (it.hasFocus) keyboardController?.show() else keyboardController?.hide() }
}

fun Modifier.sensitiveData(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        semantics { isSensitiveData = true }
    } else {
        clearAndSetSemantics { }
    }
