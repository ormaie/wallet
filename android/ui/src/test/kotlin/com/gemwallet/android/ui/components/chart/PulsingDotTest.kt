package com.gemwallet.android.ui.components.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class PulsingDotTest {

    @Test
    fun pulseProgressFromElapsedNanos_wrapsCleanlyAcrossCycles() {
        assertEquals(0f, pulseProgressFromElapsedNanos(-100L), 0.0001f)
        assertEquals(0f, pulseProgressFromElapsedNanos(0L), 0.0001f)
        assertEquals(0.5f, pulseProgressFromElapsedNanos(900_000_000L), 0.0001f)
        assertEquals(0f, pulseProgressFromElapsedNanos(1_800_000_000L), 0.0001f)
        assertEquals(0.25f, pulseProgressFromElapsedNanos(2_250_000_000L), 0.0001f)
    }

    @Test
    fun computePulseRingFrame_matchesExpectedScaleAndAlphaCurve() {
        val start = computePulseRingFrame(progress = 0f, maxScale = 3.0f)
        assertEquals(1.0f, start.scale, 0.0001f)
        assertEquals(0.32f, start.alpha, 0.0001f)

        val mid = computePulseRingFrame(progress = 0.5f, maxScale = 3.0f)
        assertEquals(2.5f, mid.scale, 0.0001f)
        assertEquals(0.08f, mid.alpha, 0.0001f)

        val end = computePulseRingFrame(progress = 1.0f, maxScale = 3.0f)
        assertEquals(3.0f, end.scale, 0.0001f)
        assertEquals(0f, end.alpha, 0.0001f)
    }
}
