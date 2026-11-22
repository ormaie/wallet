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
    fun walletRootHostState_incrementsRippleResetGenerationOnReturnAndReattach() {
        val state = WalletRootHostState(initiallyActivated = true)
        val transition = { false }

        state.attachProxy(transition)
        state.updateRootTop(true)
        assertEquals(0, state.rippleResetGeneration)

        state.updateRootTop(false)
        assertEquals(0, state.rippleResetGeneration)

        state.updateRootTop(true)
        assertEquals(1, state.rippleResetGeneration)

        state.updateRootTop(false)
        state.detachProxy(transition)
        assertEquals(1, state.rippleResetGeneration)

        state.attachProxy(transition)
        assertEquals(2, state.rippleResetGeneration)

        state.updateRootTop(true)
        assertEquals(3, state.rippleResetGeneration)
    }
}
