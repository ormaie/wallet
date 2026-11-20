package com.gemwallet.android.ui.components.fields

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFieldTest {
    @Test
    fun keepsMaxFontSizeWhenTextFits() {
        val fitted = fitAmountFontSize(
            maxFontSizeSp = 36f,
            minFontSizeSp = 18f,
            availableWidthPx = 400f,
            measureWidthPx = { sizeSp -> sizeSp * 8f },
        )

        assertEquals(36f, fitted, 0f)
    }

    @Test
    fun shrinksToLargestFittingFontSize() {
        val fitted = fitAmountFontSize(
            maxFontSizeSp = 36f,
            minFontSizeSp = 18f,
            availableWidthPx = 300f,
            measureWidthPx = { sizeSp -> sizeSp * 12f },
        )

        assertEquals(25f, fitted, 0f)
    }

    @Test
    fun clampsAtMinFontSizeWhenStillTooWide() {
        val fitted = fitAmountFontSize(
            maxFontSizeSp = 45f,
            minFontSizeSp = 18f,
            availableWidthPx = 200f,
            measureWidthPx = { sizeSp -> sizeSp * 16f },
        )

        assertEquals(18f, fitted, 0f)
    }

    @Test
    fun returnsMaxFontSizeWhenAvailableWidthIsNonPositive() {
        val fitted = fitAmountFontSize(
            maxFontSizeSp = 36f,
            minFontSizeSp = 18f,
            availableWidthPx = 0f,
            measureWidthPx = { sizeSp -> sizeSp * 20f },
        )

        assertEquals(36f, fitted, 0f)
    }
}
