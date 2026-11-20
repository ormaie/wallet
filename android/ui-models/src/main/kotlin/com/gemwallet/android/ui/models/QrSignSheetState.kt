package com.gemwallet.android.ui.models

import androidx.compose.runtime.Immutable

@Immutable
data class QrSignSheetState(
    val urFrames: List<String>,
    val stepIndex: Int = 1,
    val totalSteps: Int = 1,
    val receivedParts: Int? = null,
    val expectedParts: Int? = null,
    val errorMessage: String? = null,
)
