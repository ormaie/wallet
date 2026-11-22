package com.gemwallet.android.ui.components.chart

import com.gemwallet.android.testkit.mockGemChartData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.gemstone.ChartDateValue

class ChartSectionTest {

    private val chart = mockGemChartData(
        values = (0..4).map { ChartDateValue(date = it * 1_000L, value = 10.0 + it) },
        start = 1_000L,
        end = 4_000L,
    )

    @Test
    fun `points sit at their time in the zoom window`() {
        assertEquals(listOf(-1f / 3, 0f, 1f / 3, 2f / 3, 1f), chart.linePoints().map { it.x })
        assertEquals(listOf(10f, 11f, 12f, 13f, 14f), chart.linePoints().map { it.y })
    }

    @Test
    fun `nearestDateIndex clamps edges excludes outside window and breaks ties left`() {
        val dates = longArrayOf(0L, 60_000L, 120_000L)

        assertEquals(1, nearestDateIndex(dates, start = 0L, end = 120_000L, fraction = 0.4f))
        assertEquals(2, nearestDateIndex(dates, start = 0L, end = 120_000L, fraction = 4.0f))
        assertEquals(0, nearestDateIndex(dates, start = 0L, end = 120_000L, fraction = -1.0f))
        assertEquals(1, nearestDateIndex(dates, start = 30_000L, end = 120_000L, fraction = 0.0f))
        assertEquals(0, nearestDateIndex(dates, start = 0L, end = 120_000L, fraction = 0.25f))
        assertNull(nearestDateIndex(longArrayOf(), start = 0L, end = 120_000L, fraction = 0.5f))
        assertNull(nearestDateIndex(dates, start = 200_000L, end = 300_000L, fraction = 0.5f))
    }
}
