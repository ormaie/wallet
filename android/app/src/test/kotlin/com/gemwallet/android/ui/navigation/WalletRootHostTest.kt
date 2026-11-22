package com.gemwallet.android.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRootHostTest {

    @Test
    fun walletRootLifecycleState_capsHiddenRootAtCreatedAndMirrorsVisibleRoot() {
        assertEquals(Lifecycle.State.INITIALIZED, walletRootLifecycleState(Lifecycle.State.INITIALIZED, isRootVisible = true))
        assertEquals(Lifecycle.State.INITIALIZED, walletRootLifecycleState(Lifecycle.State.INITIALIZED, isRootVisible = false))
        assertEquals(Lifecycle.State.CREATED, walletRootLifecycleState(Lifecycle.State.CREATED, isRootVisible = true))
        assertEquals(Lifecycle.State.CREATED, walletRootLifecycleState(Lifecycle.State.CREATED, isRootVisible = false))
        assertEquals(Lifecycle.State.STARTED, walletRootLifecycleState(Lifecycle.State.STARTED, isRootVisible = true))
        assertEquals(Lifecycle.State.CREATED, walletRootLifecycleState(Lifecycle.State.STARTED, isRootVisible = false))
        assertEquals(Lifecycle.State.RESUMED, walletRootLifecycleState(Lifecycle.State.RESUMED, isRootVisible = true))
        assertEquals(Lifecycle.State.CREATED, walletRootLifecycleState(Lifecycle.State.RESUMED, isRootVisible = false))
        assertEquals(Lifecycle.State.DESTROYED, walletRootLifecycleState(Lifecycle.State.DESTROYED, isRootVisible = true))
        assertEquals(Lifecycle.State.DESTROYED, walletRootLifecycleState(Lifecycle.State.DESTROYED, isRootVisible = false))
    }

    @Test
    fun walletRootLifecycleOwner_pausesWhenOccludedAndResumesWhenVisible() {
        val owner = WalletRootLifecycleOwner(LifecycleRegistry::createUnsafe)

        owner.updateParentState(Lifecycle.State.RESUMED)
        owner.updateVisibility(true)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.updateVisibility(false)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.updateParentState(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.updateParentState(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.updateVisibility(true)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)

        owner.updateVisibility(false)
        owner.updateParentState(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun walletRootLifecycleOwner_destroyFromInitializedDoesNotThrow() {
        val owner = WalletRootLifecycleOwner(LifecycleRegistry::createUnsafe)

        owner.destroy()

        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)
    }

    @Test
    fun walletRootHostState_ignoresStaleProxyDetachAfterReattach() {
        val state = WalletRootHostState(initiallyActivated = false)
        val visibilityEvents = mutableListOf<Boolean>()
        state.bindVisibilityListener(visibilityEvents::add)

        var firstRunning = true
        val firstTransition = { firstRunning }
        val secondTransition = { false }

        assertFalse(state.hasActivatedRoot)
        assertFalse(state.isProxyMounted)
        assertFalse(state.isNavTransitionRunning())

        state.attachProxy(firstTransition)
        assertTrue(state.hasActivatedRoot)
        assertTrue(state.isProxyMounted)
        assertTrue(state.isNavTransitionRunning())

        firstRunning = false
        assertFalse(state.isNavTransitionRunning())

        state.attachProxy(secondTransition)
        state.detachProxy(firstTransition)
        assertTrue(state.isProxyMounted)

        state.detachProxy(secondTransition)
        assertFalse(state.isProxyMounted)
        assertTrue(state.hasActivatedRoot)
        assertFalse(state.isNavTransitionRunning())
        assertEquals(listOf(false, true, false), visibilityEvents)

        state.attachProxy(secondTransition)
        state.activeContentKey = "wallet-root"
        state.reset()
        assertFalse(state.hasActivatedRoot)
        assertFalse(state.isProxyMounted)
        assertFalse(state.isNavTransitionRunning())
        assertEquals(null, state.activeContentKey)
        assertEquals(listOf(false, true, false, true, false), visibilityEvents)
    }

    @Test
    fun walletRootHostState_resetsOnlyActiveIndicationsOnceOnReturnAndReattach() {
        val state = WalletRootHostState(initiallyActivated = true)
        val transition = { false }
        var firstResets = 0
        var secondResets = 0
        val firstHandle = object : ResettableIndicationHandle {
            override fun reset() {
                firstResets += 1
            }
        }
        val secondHandle = object : ResettableIndicationHandle {
            override fun reset() {
                secondResets += 1
            }
        }

        state.attachProxy(transition)
        state.updateRootTop(true)
        state.indicationResetBus.markActive(firstHandle)
        assertEquals(1, state.indicationResetBus.activeCount)

        state.updateRootTop(false)
        assertEquals(0, firstResets)
        assertEquals(0, secondResets)

        state.updateRootTop(true)
        assertEquals(1, firstResets)
        assertEquals(0, secondResets)
        assertEquals(0, state.indicationResetBus.activeCount)

        state.indicationResetBus.markActive(firstHandle)
        state.indicationResetBus.markActive(secondHandle)
        state.indicationResetBus.unregister(secondHandle)
        state.updateRootTop(false)
        state.detachProxy(transition)

        state.attachProxy(transition)
        assertEquals(2, firstResets)
        assertEquals(0, secondResets)

        state.updateRootTop(true)
        assertEquals(2, firstResets)
        assertEquals(0, secondResets)
    }
}
