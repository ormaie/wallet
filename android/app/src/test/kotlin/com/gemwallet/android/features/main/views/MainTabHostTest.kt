package com.gemwallet.android.features.main.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabHostTest {

    private val tabIndices = mapOf("wallet" to 0, "activity" to 1, "settings" to 2)
    private fun indexOf(route: String): Int = tabIndices[route] ?: -1

    @Test
    fun persistentTabHostState_mountsTabsLazilyAndKeepsThemAlive() {
        val state = PersistentTabHostState("wallet")

        assertEquals(listOf("wallet"), state.mountedTabs)
        assertEquals("wallet", state.activeTab)
        assertNull(state.exitingTab)
        assertFalse(state.isTransitionPending)

        assertFalse(state.selectTab("wallet", ::indexOf))
        assertEquals(listOf("wallet"), state.mountedTabs)

        assertTrue(state.selectTab("settings", ::indexOf))
        assertEquals(listOf("wallet", "settings"), state.mountedTabs)
        assertEquals("settings", state.activeTab)
        assertEquals("wallet", state.exitingTab)
        assertEquals(1, state.transitionDirection)
        assertTrue(state.isTransitionPending)

        state.markTransitionStarted()
        assertFalse(state.isTransitionPending)

        state.finishTransition()
        assertNull(state.exitingTab)
        assertEquals(0, state.transitionDirection)

        assertTrue(state.selectTab("activity", ::indexOf))
        assertEquals(listOf("wallet", "settings", "activity"), state.mountedTabs)
        assertEquals("activity", state.activeTab)
        assertEquals("settings", state.exitingTab)
        assertEquals(-1, state.transitionDirection)

        assertTrue(state.selectTab("wallet", ::indexOf))
        assertEquals(listOf("wallet", "settings", "activity"), state.mountedTabs)
        assertEquals("wallet", state.activeTab)
        assertEquals("activity", state.exitingTab)
        assertEquals(-1, state.transitionDirection)
    }

    @Test
    fun persistentTabHostState_skipsSlideWhenRouteIndexIsUnknown() {
        val state = PersistentTabHostState("wallet")

        assertFalse(state.selectTab("unknown", ::indexOf))
        assertEquals("unknown", state.activeTab)
        assertNull(state.exitingTab)
        assertEquals(0, state.transitionDirection)
        assertFalse(state.isTransitionPending)
    }

    @Test
    fun tabCurves_matchExpectedEndpoints() {
        assertEquals(0f, tabEnterAlpha(0f), 0.0001f)
        assertEquals(0f, tabEnterAlpha(0.25f), 0.0001f)
        assertEquals(1f, tabEnterAlpha(1f), 0.0001f)

        assertEquals(1f, tabExitAlpha(0f), 0.0001f)
        assertEquals(0f, tabExitAlpha(0.45f), 0.0001f)
        assertEquals(0f, tabExitAlpha(1f), 0.0001f)

        assertEquals(0f, tabSlideProgress(0f), 0.0001f)
        assertEquals(1f, tabSlideProgress(1f), 0.0001f)
    }

    @Test
    fun frostedBackdropGeometry_downsamplesAndPreservesExactPillMapping() {
        val geometry = computeFrostedBackdropGeometry(
            pillWidth = 936,
            pillHeight = 168,
            blurRadiusPx = 48f,
            downsampleScale = 0.5f,
        )

        assertEquals(516, geometry.surfaceWidth)
        assertEquals(132, geometry.surfaceHeight)
        assertEquals(-48, geometry.placeOffsetX)
        assertEquals(-48, geometry.placeOffsetY)
        assertEquals(24f, geometry.blurRadiusPx, 0.0001f)
        assertEquals(48f, geometry.contentTranslatePadPx, 0.0001f)
        assertEquals(0.5f, geometry.downsampleScale, 0.0001f)
        assertEquals(2f, geometry.upsampleScale, 0.0001f)

        listOf(0f to 0f, 936f to 168f, 317.5f to 83.25f).forEach { (pillX, pillY) ->
            val localX = (pillX + geometry.contentTranslatePadPx) * geometry.downsampleScale
            val localY = (pillY + geometry.contentTranslatePadPx) * geometry.downsampleScale
            val mappedX = geometry.placeOffsetX + localX * geometry.upsampleScale
            val mappedY = geometry.placeOffsetY + localY * geometry.upsampleScale
            assertEquals(pillX, mappedX, 0.001f)
            assertEquals(pillY, mappedY, 0.001f)
        }
    }

    @Test
    fun frostedBackdropGeometry_coversOddDimensionsAndClampsInputs() {
        val odd = computeFrostedBackdropGeometry(
            pillWidth = 935,
            pillHeight = 167,
            blurRadiusPx = 47.2f,
            downsampleScale = 0.5f,
        )

        assertTrue(odd.placeOffsetX + odd.surfaceWidth * odd.upsampleScale >= 935f + 47.2f)
        assertTrue(odd.placeOffsetY + odd.surfaceHeight * odd.upsampleScale >= 167f + 47.2f)
        val mappedOriginX = odd.placeOffsetX + odd.contentTranslatePadPx * odd.downsampleScale * odd.upsampleScale
        assertEquals(0f, mappedOriginX, 0.001f)

        val clamped = computeFrostedBackdropGeometry(
            pillWidth = -10,
            pillHeight = 0,
            blurRadiusPx = -4f,
            downsampleScale = 0.1f,
        )

        assertEquals(0, clamped.surfaceWidth)
        assertEquals(0, clamped.surfaceHeight)
        assertEquals(0.25f, clamped.downsampleScale, 0.0001f)
        assertEquals(4f, clamped.upsampleScale, 0.0001f)
    }
}
