package com.gemwallet.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodsPanelTest {
    @Test
    fun alignsSixPeriodSlotsAndIndicatorFlushWithEdges() {
        val weights = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f)
        val slots = periodSlotBounds(weights, totalWidth = 1000)

        assertEquals(PeriodBounds(left = 0, width = 167), slots[0])
        assertEquals(PeriodBounds(left = 833, width = 167), slots[5])
        assertEquals(PeriodBounds(left = 0, width = 167), periodIndicatorBounds(progress = 0f, weights = weights, totalWidth = 1000))
        assertEquals(PeriodBounds(left = 833, width = 167), periodIndicatorBounds(progress = 5f, weights = weights, totalWidth = 1000))
    }

    @Test
    fun keepsIndicatorLockedToSelectedPeriodWhileLeadingSlotCollapses() {
        val weights = floatArrayOf(0.5f, 1f, 1f, 1f, 1f, 1f)
        val slots = periodSlotBounds(weights, totalWidth = 1000)
        val weekIndicator = periodIndicatorBounds(progress = 2f, weights = weights, totalWidth = 1000)
        val allIndicator = periodIndicatorBounds(progress = 5f, weights = weights, totalWidth = 1000)

        assertEquals(slots[2], weekIndicator)
        assertEquals(slots[5], allIndicator)
        assertEquals(1000, allIndicator.left + allIndicator.width)
    }

    @Test
    fun mapsFiveCandlePeriodsWithLeadingSlotHidden() {
        val weights = floatArrayOf(0f, 1f, 1f, 1f, 1f, 1f)
        val slots = periodSlotBounds(weights, totalWidth = 1000)

        assertEquals(PeriodBounds(left = 0, width = 0), slots[0])
        assertEquals(PeriodBounds(left = 0, width = 200), slots[1])
        assertEquals(PeriodBounds(left = 200, width = 200), slots[2])
        assertEquals(PeriodBounds(left = 800, width = 200), slots[5])
        assertEquals(PeriodBounds(left = 0, width = 200), periodIndicatorBounds(progress = 1f, weights = weights, totalWidth = 1000))
        assertEquals(PeriodBounds(left = 200, width = 200), periodIndicatorBounds(progress = 2f, weights = weights, totalWidth = 1000))
        assertEquals(PeriodBounds(left = 800, width = 200), periodIndicatorBounds(progress = 5f, weights = weights, totalWidth = 1000))
    }

    @Test
    fun interpolatesMidTransitionAndClampsOutOfRangeProgress() {
        val weights = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f)

        assertEquals(PeriodBounds(left = 417, width = 167), periodIndicatorBounds(progress = 2.5f, weights = weights, totalWidth = 1000))
        assertEquals(PeriodBounds(left = 0, width = 167), periodIndicatorBounds(progress = -1f, weights = weights, totalWidth = 1000))
        assertEquals(PeriodBounds(left = 833, width = 167), periodIndicatorBounds(progress = 9f, weights = weights, totalWidth = 1000))
    }

    @Test
    fun handlesEmptyWidthOrZeroWeightsSafely() {
        val weights = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f)
        val zeroWeights = floatArrayOf(0f, 0f, 0f)

        assertEquals(PeriodBounds(left = 0, width = 0), periodIndicatorBounds(progress = 2f, weights = weights, totalWidth = 0))
        assertEquals(PeriodBounds(left = 0, width = 0), periodIndicatorBounds(progress = 1f, weights = zeroWeights, totalWidth = 600))
        assertEquals(listOf(PeriodBounds(0, 0), PeriodBounds(0, 0), PeriodBounds(0, 0)), periodSlotBounds(zeroWeights, totalWidth = 600))
    }
}
