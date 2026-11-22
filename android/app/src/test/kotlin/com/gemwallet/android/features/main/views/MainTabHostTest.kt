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
}
