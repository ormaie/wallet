package com.gemwallet.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalNavTransitionRunning = staticCompositionLocalOf<() -> Boolean> { { false } }
