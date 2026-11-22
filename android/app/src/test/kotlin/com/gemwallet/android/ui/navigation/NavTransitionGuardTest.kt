package com.gemwallet.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTransitionGuardTest {

    @Test
    fun isScenePop_detectsBackwardTransitionWhenTargetIsPrefixOfInitial() {
        val homeEntry = testEntry("home")
        val buyEntry = testEntry("buy")
        val fiatEntry = testEntry("fiat")

        val homeScene = testScene(entry = homeEntry, previous = emptyList())
        val buyScene = testScene(entry = buyEntry, previous = listOf(homeEntry))
        val fiatScene = testScene(entry = fiatEntry, previous = listOf(homeEntry, buyEntry))

        assertFalse(isScenePop(initialScene = homeScene, targetScene = buyScene))
        assertTrue(isScenePop(initialScene = buyScene, targetScene = homeScene))
        assertTrue(isScenePop(initialScene = fiatScene, targetScene = homeScene))
        assertTrue(isScenePop(initialScene = fiatScene, targetScene = buyScene))
    }

    @Test
    fun isScenePop_returnsFalseForReplaceAndCrossRootReset() {
        val onboardingEntry = testEntry("onboarding")
        val createEntry = testEntry("create")
        val homeEntry = testEntry("home")
        val warningEntry = testEntry("warning")
        val exportEntry = testEntry("export")

        val warningScene = testScene(entry = warningEntry, previous = listOf(homeEntry))
        val exportScene = testScene(entry = exportEntry, previous = listOf(homeEntry))
        val createScene = testScene(entry = createEntry, previous = listOf(onboardingEntry))
        val homeScene = testScene(entry = homeEntry, previous = emptyList())

        assertFalse(isScenePop(initialScene = warningScene, targetScene = exportScene))
        assertFalse(isScenePop(initialScene = createScene, targetScene = homeScene))
        assertFalse(isScenePop(initialScene = homeScene, targetScene = homeScene))
    }

    @Test
    fun navTransitionTracker_reportsRunningUntilOnlyExpectedEntriesAreSettled() {
        val tracker = NavTransitionTracker()
        var homeRunning = false
        var buyRunning = true
        val homeCheck = { homeRunning }
        val buyCheck = { buyRunning }

        assertTrue(tracker.isTransitionRunning(listOf("home")))

        tracker.attach("home", homeCheck)
        assertFalse(tracker.isTransitionRunning(listOf("home")))

        assertTrue(tracker.isTransitionRunning(listOf("buy")))

        homeRunning = true
        tracker.attach("buy", buyCheck)
        assertTrue(tracker.isTransitionRunning(listOf("buy")))

        tracker.detach("home", homeCheck)
        assertTrue(tracker.isTransitionRunning(listOf("buy")))

        buyRunning = false
        assertFalse(tracker.isTransitionRunning(listOf("buy")))

        val replacementBuyCheck = { true }
        tracker.attach("buy", replacementBuyCheck)
        tracker.detach("buy", buyCheck)
        assertTrue(tracker.isTransitionRunning(listOf("buy")))
    }

    @Test
    fun predictiveBackGate_suppressesGestureStartedDuringActiveTransitionAndLatchesUntilIdle() {
        val gate = PredictiveBackGate()
        var isTransitionRunning = true
        var checkCalls = 0
        val checkRunning = {
            checkCalls += 1
            isTransitionRunning
        }

        assertTrue(gate.shouldAllowPredictiveBack(isGestureInProgress = false, isTransitionRunning = checkRunning))
        assertEquals(0, checkCalls)

        assertFalse(gate.shouldAllowPredictiveBack(isGestureInProgress = true, isTransitionRunning = checkRunning))
        assertEquals(1, checkCalls)

        isTransitionRunning = false
        assertFalse(gate.shouldAllowPredictiveBack(isGestureInProgress = true, isTransitionRunning = checkRunning))
        assertEquals(1, checkCalls)

        assertTrue(gate.shouldAllowPredictiveBack(isGestureInProgress = false, isTransitionRunning = checkRunning))

        assertTrue(gate.shouldAllowPredictiveBack(isGestureInProgress = true, isTransitionRunning = checkRunning))
        assertEquals(2, checkCalls)

        isTransitionRunning = true
        assertTrue(gate.shouldAllowPredictiveBack(isGestureInProgress = true, isTransitionRunning = checkRunning))
        assertEquals(2, checkCalls)
    }

    private fun testEntry(key: String): NavEntry<String> = NavEntry(
        key = key,
        contentKey = key,
        content = {},
    )

    private fun testScene(entry: NavEntry<String>, previous: List<NavEntry<String>>): Scene<String> = object : Scene<String> {
        override val key: Any = entry.contentKey
        override val entries: List<NavEntry<String>> = listOf(entry)
        override val previousEntries: List<NavEntry<String>> = previous
        override val content: @Composable () -> Unit = {}
    }
}
