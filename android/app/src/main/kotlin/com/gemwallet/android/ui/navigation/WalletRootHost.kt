package com.gemwallet.android.ui.navigation

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DelegatableNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.gemwallet.android.features.main.views.MainScreen
import com.gemwallet.android.ui.LocalNavTransitionRunning

internal class WalletRootHostState(initiallyActivated: Boolean) {
    var containerCoordinates: LayoutCoordinates? = null
    var activeContentKey: Any? = null
    var hasActivatedRoot by mutableStateOf(initiallyActivated)
        private set
    var isProxyMounted by mutableStateOf(false)
        private set
    var slideOffsetX by mutableFloatStateOf(0f)
        private set
    var rippleResetGeneration by mutableIntStateOf(0)
        private set

    private var activeTransitionRunning by mutableStateOf<(() -> Boolean)?>(null)
    private var onVisibilityChanged: ((Boolean) -> Unit)? = null
    private var wasRootTop = initiallyActivated
    private var hasDetachedProxy = false

    val isNavTransitionRunning: () -> Boolean = {
        activeTransitionRunning?.invoke() ?: false
    }

    fun bindVisibilityListener(listener: ((Boolean) -> Unit)?) {
        onVisibilityChanged = listener
        listener?.invoke(isProxyMounted)
    }

    fun updateRootTop(isRootTop: Boolean) {
        if (isRootTop && !wasRootTop) {
            rippleResetGeneration += 1
        }
        wasRootTop = isRootTop
    }

    fun attachProxy(transitionRunning: () -> Boolean) {
        hasActivatedRoot = true
        activeTransitionRunning = transitionRunning
        if (!isProxyMounted) {
            if (hasDetachedProxy) {
                rippleResetGeneration += 1
                hasDetachedProxy = false
            }
            isProxyMounted = true
            onVisibilityChanged?.invoke(true)
        }
    }

    fun detachProxy(transitionRunning: () -> Boolean) {
        if (activeTransitionRunning === transitionRunning) {
            activeTransitionRunning = null
            if (isProxyMounted) {
                isProxyMounted = false
                hasDetachedProxy = true
                slideOffsetX = 0f
                onVisibilityChanged?.invoke(false)
            }
        }
    }

    fun reset() {
        hasActivatedRoot = false
        isProxyMounted = false
        hasDetachedProxy = false
        wasRootTop = false
        slideOffsetX = 0f
        activeTransitionRunning = null
        activeContentKey = null
        onVisibilityChanged?.invoke(false)
    }

    fun updateProxyPlacement(coordinates: LayoutCoordinates) {
        val container = containerCoordinates
        val nextOffset = if (container != null && container.isAttached && coordinates.isAttached) {
            container.localPositionOf(coordinates, Offset.Zero).x
        } else {
            coordinates.positionInRoot().x
        }
        if (slideOffsetX != nextOffset) {
            slideOffsetX = nextOffset
        }
    }
}

internal fun walletRootLifecycleState(parentState: Lifecycle.State, isRootVisible: Boolean): Lifecycle.State = when {
    parentState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
    !isRootVisible && parentState.isAtLeast(Lifecycle.State.CREATED) -> Lifecycle.State.CREATED
    else -> parentState
}

internal class WalletRootLifecycleOwner(createRegistry: (LifecycleOwner) -> LifecycleRegistry = ::LifecycleRegistry) : LifecycleOwner {
    private val registry: LifecycleRegistry = createRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    private var parentState: Lifecycle.State = Lifecycle.State.INITIALIZED
    private var isRootVisible: Boolean = false
    private var isDestroyed: Boolean = false

    fun updateParentState(state: Lifecycle.State) {
        parentState = state
        sync()
    }

    fun updateVisibility(visible: Boolean) {
        isRootVisible = visible
        sync()
    }

    fun destroy() {
        isDestroyed = true
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun sync() {
        if (isDestroyed) return
        val target = walletRootLifecycleState(parentState = parentState, isRootVisible = isRootVisible)
        if (target == Lifecycle.State.DESTROYED && registry.currentState == Lifecycle.State.INITIALIZED) {
            return
        }
        if (registry.currentState != target) {
            registry.currentState = target
        }
    }
}

@Composable
internal fun WalletRootProxy(hostState: WalletRootHostState) {
    val isNavTransitionRunning = LocalNavTransitionRunning.current
    DisposableEffect(hostState, isNavTransitionRunning) {
        hostState.attachProxy(isNavTransitionRunning)
        onDispose {
            hostState.detachProxy(isNavTransitionRunning)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced(hostState::updateProxyPlacement),
    )
}

@Composable
internal fun PersistentWalletRoot(
    navigator: WalletNavigator,
    hostState: WalletRootHostState,
    contentKey: Any,
    saveableStateHolder: SaveableStateHolder,
    onWalletContentReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    saveableStateHolder.SaveableStateProvider(contentKey) {
        val rootViewModelStoreOwner = rememberNavEntryViewModelStoreOwner(contentKey = contentKey)
        val parentLifecycle = LocalLifecycleOwner.current.lifecycle
        val rootLifecycleOwner = remember(parentLifecycle, hostState) {
            WalletRootLifecycleOwner().apply {
                updateParentState(parentLifecycle.currentState)
                updateVisibility(hostState.isProxyMounted)
            }
        }
        val currentIndication = LocalIndication.current
        val rootIndication = remember(currentIndication, hostState.rippleResetGeneration) {
            if (currentIndication is IndicationNodeFactory) {
                ResettableIndicationNodeFactory(currentIndication, hostState.rippleResetGeneration)
            } else {
                currentIndication
            }
        }

        DisposableEffect(parentLifecycle, rootLifecycleOwner, hostState) {
            val observer = LifecycleEventObserver { source, _ ->
                rootLifecycleOwner.updateParentState(source.lifecycle.currentState)
            }
            parentLifecycle.addObserver(observer)
            hostState.bindVisibilityListener(rootLifecycleOwner::updateVisibility)
            onDispose {
                hostState.bindVisibilityListener(null)
                parentLifecycle.removeObserver(observer)
                rootLifecycleOwner.destroy()
            }
        }

        Box(
            modifier = modifier.layout { measurable, constraints ->
                if (!hostState.isProxyMounted) {
                    layout(constraints.maxWidth, constraints.maxHeight) {}
                } else {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeWithLayer(0, 0) {
                            translationX = hostState.slideOffsetX
                        }
                    }
                }
            },
        ) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides rootViewModelStoreOwner,
                LocalLifecycleOwner provides rootLifecycleOwner,
                LocalNavTransitionRunning provides hostState.isNavTransitionRunning,
                LocalIndication provides rootIndication,
            ) {
                MainScreen(
                    navigator = navigator,
                    currentTab = navigator.currentTab,
                    onWalletContentReady = onWalletContentReady,
                )
            }
        }
    }
}

private class ResettableIndicationNodeFactory(private val delegate: IndicationNodeFactory, private val generation: Int) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = delegate.create(interactionSource)

    override fun equals(other: Any?): Boolean = this === other || (other is ResettableIndicationNodeFactory && delegate == other.delegate && generation == other.generation)

    override fun hashCode(): Int = 31 * delegate.hashCode() + generation
}
