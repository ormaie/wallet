package com.gemwallet.android.features.main.views

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.main.ScanReceiveModal
import com.gemwallet.android.features.main.models.BottomNavItem
import com.gemwallet.android.features.main.viewmodels.MainScreenViewModel
import com.gemwallet.android.features.settings.presents.SettingsScreen
import com.gemwallet.android.features.transactions.presents.list.TransactionsScreen
import com.gemwallet.android.features.wallet.presents.WalletAction
import com.gemwallet.android.features.wallet.presents.WalletScreen
import com.gemwallet.android.ui.LocalNavTransitionRunning
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.ConnectionStatusBannerHost
import com.gemwallet.android.ui.components.LocalConnectionBannerHandled
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.models.actions.SettingsAction
import com.gemwallet.android.ui.navigation.WalletNavigator
import com.gemwallet.android.ui.navigation.WalletRootRoute
import com.gemwallet.android.ui.navigation.routes.SettingsRoute
import com.gemwallet.android.ui.navigation.routes.TransactionsRoute
import com.gemwallet.android.ui.navigation.routes.WalletRoute
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space0
import com.gemwallet.android.ui.theme.space2
import com.gemwallet.android.ui.theme.space24
import com.gemwallet.android.ui.theme.space6
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val floatingNavHeight = 56.dp
private val floatingNavIconSize = 22.dp
private val floatingNavBlurRadius = 16.dp
private const val frostedBackdropDownsampleScale = 0.5f
private val floatingNavBorderWidth = 1.dp
private val lightFloatingNavShadowElevation = 8.dp
private val darkFloatingNavShadowElevation = 12.dp
private val lightFrostedBottomTint = Color(0xFFFAFBFF)
private val darkFrostedTopTint = Color(0xFF282B31)
private val darkFrostedBottomTint = Color(0xFF24262C)
private val lightPillShadowColor = Color(0xFF18274B)
private val darkSelectedIconColor = Color(0xFF6D93FF)
private val lightUnselectedNavColor = Color(0xFF7E8490)
private val darkUnselectedNavColor = Color(0xFF8A909D)
private const val frostedScrimMidAlpha = 0.30f
private const val frostedScrimBottomAlpha = 0.70f
private const val frostedBackdropSaturation = 1.20f
private const val lightFrostedTopAlpha = 0.78f
private const val lightFrostedBottomAlpha = 0.70f
private const val darkFrostedTopAlpha = 0.75f
private const val darkFrostedBottomAlpha = 0.70f
private const val fallbackFrostedTopAlpha = 0.94f
private const val fallbackFrostedBottomAlpha = 0.90f
private const val lightTopBorderAlpha = 0.88f
private const val lightBottomBorderAlpha = 0.08f
private const val darkTopBorderAlpha = 0.18f
private const val darkBottomBorderAlpha = 0.05f
private const val lightShadowAlpha = 0.32f
private const val darkShadowAlpha = 0.70f
private const val lightIndicatorAlpha = 0.11f
private const val darkIndicatorAlpha = 0.22f
private const val indicatorDampingRatio = 0.78f
private const val selectedIconScale = 1.06f
private const val scrollIndexStride = 100_000

internal fun shouldUseFrostedBackdrop(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

@Composable
fun MainScreen(navigator: WalletNavigator, currentTab: MutableState<String>, onWalletContentReady: () -> Unit = {}, viewModel: MainScreenViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingTxCount.collectAsStateWithLifecycle()
    val showsScanReceiveSelector by viewModel.showsScanReceiveSelector.collectAsStateWithLifecycle()
    var isPresentingScanner by rememberSaveable { mutableStateOf(false) }

    ScanReceiveModal(
        isVisible = isPresentingScanner,
        showsSelector = showsScanReceiveSelector,
        onDismissRequest = { isPresentingScanner = false },
        onScan = { code ->
            isPresentingScanner = false
            viewModel.onScan(code)
        },
    )

    RootTabBackHandler(navigator = navigator, currentTab = currentTab)
    val assetsListState = rememberLazyListState()
    val activitiesListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    val tabStateHolder = rememberSaveableStateHolder()
    val tabHostState = remember { PersistentTabHostState(currentTab.value) }
    val coroutineScope = rememberCoroutineScope()
    val scrollTabToTop: (String) -> Unit = { route ->
        coroutineScope.launch {
            when (route) {
                WalletRoute -> assetsListState.animateScrollToItem(0)
                TransactionsRoute -> activitiesListState.animateScrollToItem(0)
                SettingsRoute -> settingsScrollState.animateScrollTo(0)
            }
        }
    }

    val walletLabel = stringResource(R.string.common_wallet)
    val activityLabel = stringResource(R.string.activity_title)
    val settingsLabel = stringResource(R.string.settings_title)
    val tabWallet = AppIcons.TabWallet
    val tabWalletFilled = AppIcons.TabWalletFilled
    val tabActivity = AppIcons.TabActivity
    val tabActivityFilled = AppIcons.TabActivityFilled
    val tabSettings = AppIcons.TabSettings
    val tabSettingsFilled = AppIcons.TabSettingsFilled
    val pendingBadge = pendingCount.takeIf { it > 0 }?.toString()
    val navItems = remember(
        walletLabel,
        activityLabel,
        settingsLabel,
        tabWallet,
        tabWalletFilled,
        tabActivity,
        tabActivityFilled,
        tabSettings,
        tabSettingsFilled,
        pendingBadge,
    ) {
        listOf(
            BottomNavItem(
                label = walletLabel,
                icon = tabWallet,
                selectedIcon = tabWalletFilled,
                route = WalletRoute,
                testTag = "mainTab",
            ),
            BottomNavItem(
                label = activityLabel,
                icon = tabActivity,
                selectedIcon = tabActivityFilled,
                route = TransactionsRoute,
                badge = pendingBadge,
                testTag = "activitiesTab",
            ),
            BottomNavItem(
                label = settingsLabel,
                icon = tabSettings,
                selectedIcon = tabSettingsFilled,
                route = SettingsRoute,
                testTag = "settingsTab",
            ),
        )
    }
    val frostedBlurSupported = shouldUseFrostedBackdrop()
    val contentBackdropLayer = if (frostedBlurSupported) rememberGraphicsLayer() else null
    val backdropAlignment = if (frostedBlurSupported) remember { BackdropAlignmentState() } else null
    val isNavTransitionRunning = LocalNavTransitionRunning.current
    val backdropInvalidationKey: () -> Int = {
        when (currentTab.value) {
            WalletRoute -> assetsListState.firstVisibleItemIndex * scrollIndexStride + assetsListState.firstVisibleItemScrollOffset
            TransactionsRoute -> activitiesListState.firstVisibleItemIndex * scrollIndexStride + activitiesListState.firstVisibleItemScrollOffset
            else -> settingsScrollState.value
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            val haptic = LocalHapticFeedback.current
            FloatingBottomNavigationBar(
                items = navItems,
                selectedRoute = currentTab.value,
                backdropLayer = contentBackdropLayer,
                backdropOffset = { backdropAlignment?.offset ?: Offset.Zero },
                onPillPlaced = backdropAlignment?.let { it::onPillPlaced },
                backdropInvalidationKey = backdropInvalidationKey,
                isNavTransitionRunning = isNavTransitionRunning,
                onSelect = { route ->
                    if (route == currentTab.value) {
                        scrollTabToTop(route)
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        tabHostState.selectTab(route, ::tabIndex)
                        currentTab.value = route
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        val bottomPadding = scaffoldPadding.calculateBottomPadding()
        val contentModifier = if (contentBackdropLayer != null && backdropAlignment != null) {
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars)
                .onPlaced(backdropAlignment::onContentPlaced)
                .drawWithContent {
                    if (!isNavTransitionRunning() || contentBackdropLayer.size == IntSize.Zero) {
                        contentBackdropLayer.record {
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawLayer(contentBackdropLayer)
                }
        } else {
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars)
        }
        Box(modifier = contentModifier) {
            val onWalletAction = remember<(WalletAction.Navigation) -> Unit>(navigator) {
                { action ->
                    when (action) {
                        WalletAction.ShowWallets -> navigator.openWallets()
                        WalletAction.Manage -> navigator.openAssetsManage()
                        WalletAction.Scan -> isPresentingScanner = true
                        WalletAction.Search -> navigator.openAssetsSearch()
                        WalletAction.Send -> navigator.openRecipient()
                        WalletAction.Receive -> navigator.openReceive()
                        WalletAction.Buy -> navigator.openBuy()
                        WalletAction.Swap -> navigator.openSwap()
                        WalletAction.Portfolio -> navigator.openPortfolio()
                        WalletAction.Perpetuals -> navigator.openPerpetuals()
                        is WalletAction.OpenPerpetual -> navigator.openPerpetual(action.assetId)
                        is WalletAction.OpenAsset -> navigator.openAsset(action.assetId)
                        WalletAction.OpenCollections -> navigator.openCollections()
                        is WalletAction.OpenNftCollection -> navigator.openCollection(action.collectionId)
                        is WalletAction.OpenNftAsset -> navigator.openCollectible(action.assetId)
                    }
                }
            }
            val onSettingsAction = remember<(SettingsAction) -> Unit>(navigator) {
                { action ->
                    when (action) {
                        SettingsAction.Wallets -> navigator.openWallets()
                        SettingsAction.Security -> navigator.openSecurity()
                        SettingsAction.Notifications -> navigator.openNotifications()
                        SettingsAction.Preferences -> navigator.openPreferences()
                        SettingsAction.Connections -> navigator.openConnections()
                        SettingsAction.Support -> navigator.openSupport()
                        SettingsAction.Rewards -> navigator.openRewards()
                        SettingsAction.AboutUs -> navigator.openAboutUs()
                        SettingsAction.Developer -> navigator.openDeveloper()
                    }
                }
            }
            CompositionLocalProvider(LocalConnectionBannerHandled provides true) {
                PersistentTabHost(
                    hostState = tabHostState,
                    selectedTab = currentTab.value,
                    tabIndex = ::tabIndex,
                    saveableStateHolder = tabStateHolder,
                ) { tab ->
                    when (tab) {
                        WalletRoute -> WalletScreen(
                            onAction = onWalletAction,
                            onContentReady = onWalletContentReady,
                            listState = assetsListState,
                            bottomPadding = bottomPadding,
                        )

                        TransactionsRoute -> TransactionsScreen(
                            listState = activitiesListState,
                            bottomPadding = bottomPadding,
                            onTransaction = navigator::openTransaction,
                            onBuy = navigator::openBuy,
                            onReceive = navigator::openReceive,
                        )

                        else -> SettingsScreen(
                            scrollState = settingsScrollState,
                            bottomPadding = bottomPadding,
                            onAction = onSettingsAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootTabBackHandler(navigator: WalletNavigator, currentTab: MutableState<String>) {
    val isRootRouteActive = navigator.backStack.lastOrNull() == WalletRootRoute
    BackHandler(isRootRouteActive && currentTab.value != WalletRoute) {
        currentTab.value = WalletRoute
    }
}

internal data class FrostedBackdropGeometry(
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val placeOffsetX: Int,
    val placeOffsetY: Int,
    val blurRadiusPx: Float,
    val contentTranslatePadPx: Float,
    val downsampleScale: Float,
    val upsampleScale: Float,
)

internal fun computeFrostedBackdropGeometry(pillWidth: Int, pillHeight: Int, blurRadiusPx: Float, downsampleScale: Float): FrostedBackdropGeometry {
    val scale = downsampleScale.coerceIn(0.25f, 1f)
    val upsample = 1f / scale
    val scaledBlur = blurRadiusPx.coerceAtLeast(0f) * scale
    val scaledPad = ceil(scaledBlur).toInt()
    val fullPad = (scaledPad * upsample).roundToInt()
    val surfaceWidth = (ceil(pillWidth.coerceAtLeast(0) * scale).toInt() + scaledPad * 2).coerceAtLeast(0)
    val surfaceHeight = (ceil(pillHeight.coerceAtLeast(0) * scale).toInt() + scaledPad * 2).coerceAtLeast(0)
    return FrostedBackdropGeometry(
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
        placeOffsetX = -fullPad,
        placeOffsetY = -fullPad,
        blurRadiusPx = scaledBlur,
        contentTranslatePadPx = fullPad.toFloat(),
        downsampleScale = scale,
        upsampleScale = upsample,
    )
}

private class BackdropAlignmentState {
    private var contentCoordinates: LayoutCoordinates? = null
    private var pillCoordinates: LayoutCoordinates? = null
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun onContentPlaced(coordinates: LayoutCoordinates) {
        contentCoordinates = coordinates
        updateOffset()
    }

    fun onPillPlaced(coordinates: LayoutCoordinates) {
        pillCoordinates = coordinates
        updateOffset()
    }

    private fun updateOffset() {
        val content = contentCoordinates ?: return
        val pill = pillCoordinates ?: return
        if (!content.isAttached || !pill.isAttached) return
        val next = pill.localPositionOf(content, Offset.Zero)
        if (next != offset) {
            offset = next
        }
    }
}

@Composable
private fun FloatingBottomNavigationBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    backdropLayer: GraphicsLayer?,
    backdropOffset: () -> Offset,
    onPillPlaced: ((LayoutCoordinates) -> Unit)?,
    backdropInvalidationKey: () -> Int,
    isNavTransitionRunning: () -> Boolean,
    onSelect: (String) -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val frostedBlurSupported = backdropLayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val pillFillBrush = remember(isDark, frostedBlurSupported) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    darkFrostedTopTint.copy(alpha = if (frostedBlurSupported) darkFrostedTopAlpha else fallbackFrostedTopAlpha),
                    darkFrostedBottomTint.copy(alpha = if (frostedBlurSupported) darkFrostedBottomAlpha else fallbackFrostedBottomAlpha),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (frostedBlurSupported) lightFrostedTopAlpha else fallbackFrostedTopAlpha),
                    lightFrostedBottomTint.copy(alpha = if (frostedBlurSupported) lightFrostedBottomAlpha else fallbackFrostedBottomAlpha),
                ),
            )
        }
    }
    val pillBorderBrush = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = darkTopBorderAlpha),
                    Color.White.copy(alpha = darkBottomBorderAlpha),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = lightTopBorderAlpha),
                    Color.Black.copy(alpha = lightBottomBorderAlpha),
                ),
            )
        }
    }
    val scrimBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = frostedScrimMidAlpha),
                surfaceColor.copy(alpha = frostedScrimBottomAlpha),
            ),
        )
    }
    val pillShadowColor = if (isDark) {
        Color.Black.copy(alpha = darkShadowAlpha)
    } else {
        lightPillShadowColor.copy(alpha = lightShadowAlpha)
    }
    val pillShadowElevation = if (isDark) darkFloatingNavShadowElevation else lightFloatingNavShadowElevation
    val indicatorColor = if (isDark) {
        darkSelectedIconColor.copy(alpha = darkIndicatorAlpha)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = lightIndicatorAlpha)
    }
    val selectedIconColor = if (isDark) darkSelectedIconColor else MaterialTheme.colorScheme.primary
    val selectedLabelColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
    val unselectedColor = if (isDark) darkUnselectedNavColor else lightUnselectedNavColor

    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = indicatorDampingRatio,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "FloatingTabIndicator",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scrimBrush)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionStatusBannerHost(
            modifier = Modifier
                .padding(horizontal = paddingDefault, vertical = paddingHalfSmall)
                .clip(RoundedCornerShape(paddingDefault)),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = space24, vertical = paddingSmall)
                .widthIn(max = SceneSizing.buttonMaxWidth)
                .fillMaxWidth()
                .height(floatingNavHeight)
                .shadow(
                    elevation = pillShadowElevation,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = pillShadowColor,
                    spotColor = pillShadowColor,
                )
                .then(if (onPillPlaced != null) Modifier.onPlaced(onPillPlaced) else Modifier)
                .clip(CircleShape)
                .border(
                    width = floatingNavBorderWidth,
                    brush = pillBorderBrush,
                    shape = CircleShape,
                ),
        ) {
            if (backdropLayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                FrostedBackdropSurface(
                    backdropLayer = backdropLayer,
                    backdropOffset = backdropOffset,
                    invalidationKey = backdropInvalidationKey,
                    tabIndicatorPosition = { animatedIndexState.value },
                    isNavTransitionRunning = isNavTransitionRunning,
                    surfaceColor = surfaceColor,
                    blurRadius = floatingNavBlurRadius,
                    saturation = frostedBackdropSaturation,
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(pillFillBrush),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingHalfSmall)
                    .selectableGroup()
                    .drawBehind {
                        if (items.isNotEmpty()) {
                            val itemWidth = size.width / items.size
                            val currentIndex = animatedIndexState.value
                            val slot = if (layoutDirection == LayoutDirection.Rtl) {
                                items.lastIndex - currentIndex
                            } else {
                                currentIndex
                            }
                            val radius = size.height / 2f
                            drawRoundRect(
                                color = indicatorColor,
                                topLeft = Offset(x = slot * itemWidth, y = 0f),
                                size = Size(width = itemWidth, height = size.height),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    FloatingBottomNavItem(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = item.route == selectedRoute,
                        selectedIconColor = selectedIconColor,
                        selectedLabelColor = selectedLabelColor,
                        unselectedColor = unselectedColor,
                        onClick = { onSelect(item.route) },
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BoxScope.FrostedBackdropSurface(
    backdropLayer: GraphicsLayer,
    backdropOffset: () -> Offset,
    invalidationKey: () -> Int,
    tabIndicatorPosition: () -> Float,
    isNavTransitionRunning: () -> Boolean,
    surfaceColor: Color,
    blurRadius: Dp,
    saturation: Float,
) {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val baseGeometry = remember(blurRadiusPx) {
        computeFrostedBackdropGeometry(
            pillWidth = 0,
            pillHeight = 0,
            blurRadiusPx = blurRadiusPx,
            downsampleScale = frostedBackdropDownsampleScale,
        )
    }
    val renderEffect = remember(baseGeometry.blurRadiusPx, saturation) {
        val blur = RenderEffect.createBlurEffect(
            baseGeometry.blurRadiusPx,
            baseGeometry.blurRadiusPx,
            Shader.TileMode.CLAMP,
        )
        val colorMatrix = ColorMatrix().apply { setSaturation(saturation) }
        val saturate = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(colorMatrix),
        )
        RenderEffect.createChainEffect(saturate, blur).asComposeRenderEffect()
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val geometry = computeFrostedBackdropGeometry(
                    pillWidth = width,
                    pillHeight = height,
                    blurRadiusPx = blurRadiusPx,
                    downsampleScale = frostedBackdropDownsampleScale,
                )
                val placeable = measurable.measure(
                    Constraints.fixed(geometry.surfaceWidth, geometry.surfaceHeight),
                )
                layout(width, height) {
                    placeable.place(geometry.placeOffsetX, geometry.placeOffsetY)
                }
            }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = baseGeometry.upsampleScale
                scaleY = baseGeometry.upsampleScale
                clip = true
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.renderEffect = renderEffect
                    clip = true
                }
                .drawBehind {
                    if (!isNavTransitionRunning()) {
                        invalidationKey()
                        tabIndicatorPosition()
                    }
                    drawRect(color = surfaceColor)
                    if (backdropLayer.size != IntSize.Zero) {
                        val delta = backdropOffset()
                        scale(scale = baseGeometry.downsampleScale, pivot = Offset.Zero) {
                            translate(
                                left = delta.x + baseGeometry.contentTranslatePadPx,
                                top = delta.y + baseGeometry.contentTranslatePadPx,
                            ) {
                                drawLayer(backdropLayer)
                            }
                        }
                    }
                },
        )
    }
}

@Composable
private fun FloatingBottomNavItem(
    item: BottomNavItem,
    selected: Boolean,
    selectedIconColor: Color,
    selectedLabelColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconScaleState = animateFloatAsState(
        targetValue = if (selected) selectedIconScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "FloatingTabIconScale",
    )
    val iconTint = if (selected) selectedIconColor else unselectedColor
    val labelColor = if (selected) selectedLabelColor else unselectedColor

    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag(item.testTag)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space2, Alignment.CenterVertically),
    ) {
        BadgedBox(
            badge = {
                if (!item.badge.isNullOrEmpty()) {
                    Badge(
                        modifier = Modifier.offset(x = space6, y = space0),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ) {
                        Text(text = item.badge)
                    }
                }
            },
        ) {
            Icon(
                modifier = Modifier
                    .size(floatingNavIconSize)
                    .graphicsLayer {
                        val scale = iconScaleState.value
                        scaleX = scale
                        scaleY = scale
                    },
                imageVector = if (selected) item.selectedIcon else item.icon,
                tint = iconTint,
                contentDescription = item.label,
            )
        }
        Text(
            text = item.label,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
        )
    }
}

private fun tabIndex(route: String): Int = when (route) {
    WalletRoute -> 0
    TransactionsRoute -> 1
    SettingsRoute -> 2
    else -> -1
}
