package com.gemwallet.android.features.main.views

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.main.ScanReceiveModal
import com.gemwallet.android.features.main.models.BottomNavItem
import com.gemwallet.android.features.main.viewmodels.MainScreenViewModel
import com.gemwallet.android.features.settings.presents.SettingsScreen
import com.gemwallet.android.features.settings.viewmodels.SettingsViewModel
import com.gemwallet.android.features.transactions.presents.list.TransactionsScreen
import com.gemwallet.android.features.transactions.viewmodels.TransactionsViewModel
import com.gemwallet.android.features.wallet.presents.WalletAction
import com.gemwallet.android.features.wallet.presents.WalletScreen
import com.gemwallet.android.features.wallet.viewmodels.WalletViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.ConnectionStatusBannerHost
import com.gemwallet.android.ui.components.LocalConnectionBannerHandled
import com.gemwallet.android.ui.components.animation.tabContentTransition
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val floatingNavHeight = 56.dp
private val floatingNavIconSize = 22.dp
private val floatingNavBlurRadius = 16.dp
private val floatingNavBorderWidth = 1.dp
private val floatingNavShadowRadius = 18.dp
private val floatingNavShadowOffsetY = 6.dp
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
private const val lightShadowAlpha = 0.10f
private const val darkShadowAlpha = 0.42f
private const val lightIndicatorAlpha = 0.11f
private const val darkIndicatorAlpha = 0.22f
private const val indicatorDampingRatio = 0.78f
private const val selectedIconScale = 1.06f
private const val scrollIndexStride = 100_000

@Composable
fun MainScreen(navigator: WalletNavigator, currentTab: MutableState<String>, onWalletContentReady: () -> Unit = {}, viewModel: MainScreenViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingTxCount.collectAsStateWithLifecycle()
    val showsScanReceiveSelector by viewModel.showsScanReceiveSelector.collectAsStateWithLifecycle()
    val walletViewModel: WalletViewModel = hiltViewModel()
    val transactionsViewModel: TransactionsViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val isRootRouteActive = navigator.backStack.lastOrNull() == WalletRootRoute
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

    BackHandler(isRootRouteActive && currentTab.value != WalletRoute) {
        currentTab.value = WalletRoute
    }
    val assetsListState = rememberLazyListState()
    val activitiesListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    val tabStateHolder = rememberSaveableStateHolder()
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

    val navItems = listOf(
        BottomNavItem(
            label = stringResource(R.string.common_wallet),
            icon = AppIcons.TabWallet,
            selectedIcon = AppIcons.TabWalletFilled,
            route = WalletRoute,
            testTag = "mainTab",
        ),
        BottomNavItem(
            label = stringResource(R.string.activity_title),
            icon = AppIcons.TabActivity,
            selectedIcon = AppIcons.TabActivityFilled,
            route = TransactionsRoute,
            badge = pendingCount.takeIf { it > 0 }?.toString(),
            testTag = "activitiesTab",
        ),
        BottomNavItem(
            label = stringResource(R.string.settings_title),
            icon = AppIcons.TabSettings,
            selectedIcon = AppIcons.TabSettingsFilled,
            route = SettingsRoute,
            testTag = "settingsTab",
        ),
    )
    val contentBackdropLayer = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
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
                backdropOrigin = { contentOrigin },
                backdropInvalidationKey = backdropInvalidationKey,
                onSelect = { route ->
                    if (route == currentTab.value) {
                        scrollTabToTop(route)
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        currentTab.value = route
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        val bottomPadding = scaffoldPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars)
                .onGloballyPositioned { coordinates ->
                    contentOrigin = coordinates.positionInRoot()
                }
                .drawWithContent {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        contentBackdropLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(contentBackdropLayer)
                    } else {
                        drawContent()
                    }
                },
        ) {
            CompositionLocalProvider(LocalConnectionBannerHandled provides true) {
                AnimatedContent(
                    targetState = currentTab.value,
                    transitionSpec = { tabContentTransition(::tabIndex) },
                    label = "MainTabContent",
                ) { tab ->
                    tabStateHolder.SaveableStateProvider(tab) {
                        when (tab) {
                            WalletRoute -> WalletScreen(
                                onAction = { action ->
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
                                },
                                onContentReady = onWalletContentReady,
                                listState = assetsListState,
                                bottomPadding = bottomPadding,
                                viewModel = walletViewModel,
                            )

                            TransactionsRoute -> TransactionsScreen(
                                listState = activitiesListState,
                                bottomPadding = bottomPadding,
                                onTransaction = navigator::openTransaction,
                                onBuy = navigator::openBuy,
                                onReceive = navigator::openReceive,
                                viewModel = transactionsViewModel,
                            )

                            else -> SettingsScreen(
                                scrollState = settingsScrollState,
                                bottomPadding = bottomPadding,
                                viewModel = settingsViewModel,
                                onAction = { action ->
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
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomNavigationBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    backdropLayer: GraphicsLayer,
    backdropOrigin: () -> Offset,
    backdropInvalidationKey: () -> Int,
    onSelect: (String) -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f
    val frostedBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var pillOrigin by remember { mutableStateOf(Offset.Zero) }
    val pillFillBrush = if (isDark) {
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
    val pillBorderBrush = if (isDark) {
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
    val pillShadowColor = if (isDark) {
        Color.Black.copy(alpha = darkShadowAlpha)
    } else {
        lightPillShadowColor.copy(alpha = lightShadowAlpha)
    }
    val indicatorColor = if (isDark) {
        darkSelectedIconColor.copy(alpha = darkIndicatorAlpha)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = lightIndicatorAlpha)
    }
    val selectedIconColor = if (isDark) darkSelectedIconColor else MaterialTheme.colorScheme.primary
    val selectedLabelColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
    val unselectedColor = if (isDark) darkUnselectedNavColor else lightUnselectedNavColor

    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        surfaceColor.copy(alpha = frostedScrimMidAlpha),
                        surfaceColor.copy(alpha = frostedScrimBottomAlpha),
                    ),
                ),
            )
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
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = floatingNavShadowRadius,
                        color = pillShadowColor,
                        offset = DpOffset(x = space0, y = floatingNavShadowOffsetY),
                    ),
                )
                .onGloballyPositioned { coordinates ->
                    pillOrigin = coordinates.positionInRoot()
                }
                .clip(CircleShape)
                .border(
                    width = floatingNavBorderWidth,
                    brush = pillBorderBrush,
                    shape = CircleShape,
                ),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                FrostedBackdropSurface(
                    backdropLayer = backdropLayer,
                    backdropOrigin = backdropOrigin,
                    pillOrigin = { pillOrigin },
                    invalidationKey = backdropInvalidationKey,
                    tabIndicatorPosition = { animatedIndex },
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
                            val slot = if (layoutDirection == LayoutDirection.Rtl) {
                                items.lastIndex - animatedIndex
                            } else {
                                animatedIndex
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
    backdropOrigin: () -> Offset,
    pillOrigin: () -> Offset,
    invalidationKey: () -> Int,
    tabIndicatorPosition: () -> Float,
    surfaceColor: Color,
    blurRadius: Dp,
    saturation: Float,
) {
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val padPx = blurRadiusPx.roundToInt()
    val renderEffect = remember(blurRadiusPx, saturation) {
        val blur = RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
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
                val placeable = measurable.measure(
                    Constraints.fixed(width + padPx * 2, height + padPx * 2),
                )
                layout(width, height) {
                    placeable.place(-padPx, -padPx)
                }
            }
            .graphicsLayer {
                this.renderEffect = renderEffect
                clip = true
            }
            .drawBehind {
                invalidationKey()
                tabIndicatorPosition()
                drawRect(color = surfaceColor)
                if (backdropLayer.size != IntSize.Zero) {
                    val delta = backdropOrigin() - pillOrigin()
                    translate(
                        left = delta.x + padPx.toFloat(),
                        top = delta.y + padPx.toFloat(),
                    ) {
                        drawLayer(backdropLayer)
                    }
                }
            },
    )
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
    val iconScale by animateFloatAsState(
        targetValue = if (selected) selectedIconScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "FloatingTabIconScale",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedColor,
        label = "FloatingTabIconTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) selectedLabelColor else unselectedColor,
        label = "FloatingTabLabelColor",
    )

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
                        scaleX = iconScale
                        scaleY = iconScale
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
