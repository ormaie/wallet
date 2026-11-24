package com.gemwallet.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.AvatarPresetView
import com.gemwallet.android.ui.components.image.BoringAvatar
import com.gemwallet.android.ui.components.image.BoringAvatarPalette
import com.gemwallet.android.ui.components.image.BoringAvatarVariant
import com.gemwallet.android.ui.components.image.BoringAvatarView
import com.gemwallet.android.ui.components.image.BoringPaletteMode
import com.gemwallet.android.ui.components.image.StoredAvatarInfo
import com.gemwallet.android.ui.components.image.StoredAvatarSelection
import com.gemwallet.android.ui.components.image.WalletAvatar
import com.gemwallet.android.ui.components.image.inspectStoredAvatar
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.components.screen.SceneTitle
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.SheetSizing
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.hairlineThickness
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.isLightSurface
import com.gemwallet.android.ui.theme.largeIconSize
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space4
import com.gemwallet.android.ui.theme.space8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val PRESET_COLUMNS = 4
private const val GEOMETRIC_PRESET_COUNT = 12
private const val LANDSCAPE_SCROLL_THRESHOLD = 16
private const val LANDSCAPE_INITIAL_SCROLL_INDEX = 8
private const val LIGHT_GLOW_ALPHA = 0.18f
private const val DARK_GLOW_ALPHA = 0.28f
private const val LIGHT_FLOAT_TINT_ALPHA = 0.16f
private const val DARK_FLOAT_TINT_ALPHA = 0.22f
private const val LIGHT_FLOAT_SHADOW_ALPHA = 0.09f
private const val DARK_FLOAT_SHADOW_ALPHA = 0.42f
private const val SEGMENT_THUMB_LAYOUT_ID = "segment_thumb"
private const val PALETTE_RING_LAYOUT_ID = "palette_ring"
private const val SEGMENT_PRESSED_SCALE = 0.95f
private const val PALETTE_DOT_PRESSED_SCALE = 0.88f
private const val PALETTE_DOT_UNSELECTED_SCALE = 0.94f
private const val ANIMATION_VISIBILITY_THRESHOLD = 0.001f
private const val TAB_ENTER_DURATION_MS = 220
private const val TAB_EXIT_DURATION_MS = 160
private const val TAB_SLIDE_DIVISOR = 6
private const val AVATAR_CROSSFADE_DURATION_MS = 200
private const val RESET_ICON_HIDDEN_SCALE = 0.85f

private val lightHeroBase = Color(0xFFECEEF5)
private val darkHeroBase = Color(0xFF151619)
private val lightTrayBackground = Color(0xFFF3F4F8)
private val darkTrayBackground = Color(0xFF1A1B1F)
private val lightSegmentTrack = Color(0xFFEFEFF5)
private val darkSegmentTrack = Color(0xFF1A1B1F)
private val darkSegmentSelected = Color(0xFF32343A)

private val heroFloatTintOffsetY = 6.dp
private val heroFloatShadowOffsetY = 3.dp
private val heroTopSpacing = 12.dp
private val heroBottomSpacing = 24.dp
private val segmentTrackPadding = 3.dp
private val segmentItemVerticalPadding = 8.dp
private val segmentShadowElevation = 2.dp
private val boringAvatarDefaultSize = 76.dp
private val boringAvatarCompactSize = 64.dp
private val boringRowSpacing = 24.dp
private val boringBottomRowGap = 40.dp
private val selectionRingGap = 3.dp
private val selectionRingWidth = 2.dp
private val paletteTrayHorizontalPadding = 10.dp
private val paletteTrayVerticalPadding = 8.dp
private val paletteDotTouchSize = 30.dp
private val paletteDotRingWidth = 2.dp
private val paletteDotInnerGap = 2.dp

private val segmentIndicatorAnimationSpec = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 420f,
    visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
)
private val paletteRingAnimationSpec = spring<Float>(
    dampingRatio = 0.78f,
    stiffness = 460f,
    visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
)
private val pressScaleAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
)
private val labelColorAnimationSpec = spring<Color>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val mixSweepColors = listOf(
    Color(0xFF2D5BE6),
    Color(0xFF2DD4BF),
    Color(0xFFFACC15),
    Color(0xFFF43F5E),
    Color(0xFF7C3AED),
    Color(0xFF2D5BE6),
)

private enum class AvatarPickerTab {
    Unique,
    Presets,
    Nft,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerScene(
    heroImage: ListItemImage,
    onClose: () -> Unit,
    onSelectPreset: (AvatarPreset) -> Unit,
    modifier: Modifier = Modifier,
    badgeImage: ListItemImage? = null,
    addressSeed: String? = null,
    storedAvatarInfo: StoredAvatarInfo? = null,
    snackbar: SnackbarHostState? = null,
    onRemove: (() -> Unit)? = null,
    onSelectBoring: ((BoringAvatar) -> Unit)? = null,
    nftContent: (@Composable () -> Unit)? = null,
) {
    val hasUnique = !addressSeed.isNullOrBlank() && onSelectBoring != null
    val hasNft = nftContent != null
    val tabs = remember(hasUnique, hasNft) {
        buildList {
            if (hasUnique) add(AvatarPickerTab.Unique)
            add(AvatarPickerTab.Presets)
            if (hasNft) add(AvatarPickerTab.Nft)
        }
    }

    val initialSelection: StoredAvatarSelection? = when {
        heroImage is ListItemImage.Boring -> StoredAvatarSelection.Boring(
            variant = heroImage.avatar.variant,
            palette = heroImage.avatar.palette,
            paletteMode = heroImage.avatar.paletteMode,
        )

        heroImage is ListItemImage.Preset -> StoredAvatarSelection.Preset(heroImage.preset)
        heroImage is ListItemImage.Stored -> storedAvatarInfo?.selection
        else -> null
    }
    val initialTab = when {
        initialSelection is StoredAvatarSelection.Preset -> AvatarPickerTab.Presets
        hasUnique -> AvatarPickerTab.Unique
        else -> AvatarPickerTab.Presets
    }
    val initialBoring = initialSelection as? StoredAvatarSelection.Boring
    val initialPaletteMode = initialBoring?.let { boring ->
        resolvePaletteMode(boring.paletteMode, boring.variant, boring.palette, addressSeed)
    } ?: BoringPaletteMode.Mix
    val initialTintArgb = when {
        heroImage is ListItemImage.Boring -> heroImage.avatar.palette.accentColor
        heroImage is ListItemImage.Preset -> heroImage.preset.accentColor
        heroImage is ListItemImage.Stored -> storedAvatarInfo?.dominantColor
        else -> null
    }

    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val activeTab = if (selectedTab in tabs) selectedTab else tabs.first()

    var paletteMode by rememberSaveable { mutableStateOf(initialPaletteMode) }
    var selectedBoringVariant by rememberSaveable { mutableStateOf(initialBoring?.variant) }
    var selectedBoringPalette by rememberSaveable { mutableStateOf(initialBoring?.palette) }
    var selectedPreset by rememberSaveable { mutableStateOf((initialSelection as? StoredAvatarSelection.Preset)?.preset) }
    var heroTintArgb by rememberSaveable { mutableStateOf(initialTintArgb) }
    var hydratedStoredSelection by rememberSaveable {
        mutableStateOf(heroImage !is ListItemImage.Stored || storedAvatarInfo != null)
    }

    val context = LocalContext.current
    LaunchedEffect(heroImage, storedAvatarInfo, addressSeed) {
        when (heroImage) {
            is ListItemImage.Stored -> {
                val info = storedAvatarInfo ?: withContext(Dispatchers.IO) {
                    inspectStoredAvatar(File(context.filesDir, heroImage.name))
                }
                when (val selection = info.selection) {
                    is StoredAvatarSelection.Boring -> {
                        selectedBoringVariant = selection.variant
                        selectedBoringPalette = selection.palette
                        selectedPreset = null
                        heroTintArgb = selection.palette.accentColor
                        if (!hydratedStoredSelection && hasUnique) {
                            paletteMode = resolvePaletteMode(selection.paletteMode, selection.variant, selection.palette, addressSeed)
                            selectedTab = AvatarPickerTab.Unique
                            hydratedStoredSelection = true
                        }
                    }

                    is StoredAvatarSelection.Preset -> {
                        selectedPreset = selection.preset
                        selectedBoringVariant = null
                        selectedBoringPalette = null
                        heroTintArgb = selection.preset.accentColor
                        if (!hydratedStoredSelection) {
                            selectedTab = AvatarPickerTab.Presets
                            hydratedStoredSelection = true
                        }
                    }

                    null -> {
                        selectedPreset = null
                        selectedBoringVariant = null
                        selectedBoringPalette = null
                        heroTintArgb = info.dominantColor
                        hydratedStoredSelection = true
                    }
                }
            }

            is ListItemImage.Boring -> {
                selectedBoringVariant = heroImage.avatar.variant
                selectedBoringPalette = heroImage.avatar.palette
                selectedPreset = null
                heroTintArgb = heroImage.avatar.palette.accentColor
                hydratedStoredSelection = true
            }

            is ListItemImage.Preset -> {
                selectedPreset = heroImage.preset
                selectedBoringVariant = null
                selectedBoringPalette = null
                heroTintArgb = heroImage.preset.accentColor
                hydratedStoredSelection = true
            }

            else -> {
                selectedBoringVariant = null
                selectedBoringPalette = null
                selectedPreset = null
                heroTintArgb = null
                hydratedStoredSelection = true
            }
        }
    }

    val defaultAccent = MaterialTheme.colorScheme.primary
    val targetTint = remember(heroTintArgb, heroImage, defaultAccent) {
        when {
            heroTintArgb != null -> Color(heroTintArgb!!)
            heroImage is ListItemImage.Boring -> Color(heroImage.avatar.palette.accentColor)
            heroImage is ListItemImage.Preset -> Color(heroImage.preset.accentColor)
            else -> defaultAccent
        }
    }
    val animatedTint by animateColorAsState(targetValue = targetTint, label = "avatar_hero_tint")
    val isLight = MaterialTheme.colorScheme.isLightSurface
    val heroBaseColor = if (isLight) lightHeroBase else darkHeroBase
    val glowAlpha = if (isLight) LIGHT_GLOW_ALPHA else DARK_GLOW_ALPHA
    val sheetBackground = MaterialTheme.colorScheme.background
    val sheetShape = RoundedCornerShape(topStart = SheetSizing.cornerSize, topEnd = SheetSizing.cornerSize)
    val handleRemove = onRemove?.let { remove ->
        {
            selectedBoringVariant = null
            selectedBoringPalette = null
            selectedPreset = null
            heroTintArgb = null
            hydratedStoredSelection = true
            remove()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(heroBaseColor)
            .drawBehind {
                val radius = size.width.coerceAtLeast(1f) * 0.9f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(animatedTint.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.16f),
                        radius = radius,
                    ),
                )
            },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { SceneTitle(stringResource(R.string.common_avatar)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = handleRemove != null,
                            enter = fadeIn(tween(TAB_ENTER_DURATION_MS)) + scaleIn(
                                initialScale = RESET_ICON_HIDDEN_SCALE,
                                animationSpec = tween(TAB_ENTER_DURATION_MS),
                            ),
                            exit = fadeOut(tween(TAB_EXIT_DURATION_MS)) + scaleOut(
                                targetScale = RESET_ICON_HIDDEN_SCALE,
                                animationSpec = tween(TAB_EXIT_DURATION_MS),
                            ),
                        ) {
                            IconButton(
                                onClick = { handleRemove?.invoke() },
                                modifier = Modifier.testTag("avatar_reset_action"),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Reset,
                                    contentDescription = stringResource(R.string.common_delete),
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = {
                if (snackbar != null) {
                    SnackbarHost(snackbar)
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConnectionStatusBannerHost()

                HeroStage(
                    heroImage = heroImage,
                    badgeImage = badgeImage,
                    tint = animatedTint,
                    isLight = isLight,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(sheetShape)
                        .background(sheetBackground, sheetShape)
                        .border(
                            width = hairlineThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = sheetShape,
                        )
                        .windowInsetsPadding(
                            WindowInsets.systemBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Bottom),
                        ),
                ) {
                    if (tabs.size > 1) {
                        AvatarSegmentedTabs(
                            tabs = tabs,
                            selectedTab = activeTab,
                            isLight = isLight,
                            onSelect = {
                                selectedTab = it
                                hydratedStoredSelection = true
                            },
                            modifier = Modifier.padding(
                                start = paddingDefault,
                                end = paddingDefault,
                                top = paddingDefault,
                                bottom = paddingSmall,
                            ),
                        )
                    }

                    AnimatedContent(
                        targetState = activeTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val slideIn = slideInHorizontally(animationSpec = tween(TAB_ENTER_DURATION_MS)) { width ->
                                if (forward) width / TAB_SLIDE_DIVISOR else -width / TAB_SLIDE_DIVISOR
                            }
                            val slideOut = slideOutHorizontally(animationSpec = tween(TAB_EXIT_DURATION_MS)) { width ->
                                if (forward) -width / TAB_SLIDE_DIVISOR else width / TAB_SLIDE_DIVISOR
                            }
                            (slideIn + fadeIn(tween(TAB_ENTER_DURATION_MS)))
                                .togetherWith(slideOut + fadeOut(tween(TAB_EXIT_DURATION_MS)))
                        },
                        label = "avatar_picker_tab_content",
                    ) { tab ->
                        when (tab) {
                            AvatarPickerTab.Unique -> if (hasUnique) {
                                UniqueBoringTabContent(
                                    seed = addressSeed,
                                    paletteMode = paletteMode,
                                    selectedVariant = selectedBoringVariant,
                                    selectedPalette = selectedBoringPalette,
                                    sheetBackground = sheetBackground,
                                    isLight = isLight,
                                    onSelectPalette = {
                                        paletteMode = it
                                        hydratedStoredSelection = true
                                    },
                                    onSelectBoring = { avatar ->
                                        selectedBoringVariant = avatar.variant
                                        selectedBoringPalette = avatar.palette
                                        selectedPreset = null
                                        heroTintArgb = avatar.palette.accentColor
                                        hydratedStoredSelection = true
                                        onSelectBoring(avatar)
                                    },
                                )
                            }

                            AvatarPickerTab.Presets -> GeometricPresetsTabContent(
                                selectedPreset = selectedPreset,
                                sheetBackground = sheetBackground,
                                onSelectPreset = { preset ->
                                    selectedPreset = preset
                                    selectedBoringVariant = null
                                    selectedBoringPalette = null
                                    heroTintArgb = preset.accentColor
                                    hydratedStoredSelection = true
                                    onSelectPreset(preset)
                                },
                            )

                            AvatarPickerTab.Nft -> nftContent?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStage(
    heroImage: ListItemImage,
    badgeImage: ListItemImage?,
    tint: Color,
    isLight: Boolean,
) {
    val tintAlpha = if (isLight) LIGHT_FLOAT_TINT_ALPHA else DARK_FLOAT_TINT_ALPHA
    val shadowAlpha = if (isLight) LIGHT_FLOAT_SHADOW_ALPHA else DARK_FLOAT_SHADOW_ALPHA

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = heroTopSpacing, bottom = heroBottomSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.drawBehind {
                val avatarRadius = size.minDimension / 2f
                val tintCenter = Offset(center.x, center.y + heroFloatTintOffsetY.toPx())
                val tintRadius = avatarRadius * 1.28f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to tint.copy(alpha = tintAlpha),
                        0.64f to tint.copy(alpha = tintAlpha * 0.42f),
                        1.0f to Color.Transparent,
                        center = tintCenter,
                        radius = tintRadius,
                    ),
                    radius = tintRadius,
                    center = tintCenter,
                )
                val shadowCenter = Offset(center.x, center.y + heroFloatShadowOffsetY.toPx())
                val shadowRadius = avatarRadius * 1.12f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.Black.copy(alpha = shadowAlpha),
                        0.72f to Color.Black.copy(alpha = shadowAlpha * 0.4f),
                        1.0f to Color.Transparent,
                        center = shadowCenter,
                        radius = shadowRadius,
                    ),
                    radius = shadowRadius,
                    center = shadowCenter,
                )
            },
        ) {
            if (heroImage is ListItemImage.Initials) {
                Box(
                    modifier = Modifier
                        .size(largeIconSize)
                        .background(MaterialTheme.colorScheme.background, CircleShape),
                )
            }
            WalletAvatar(
                image = heroImage,
                size = largeIconSize,
                badgeImage = badgeImage,
            )
        }
    }
}

@Composable
private fun AvatarSegmentedTabs(
    tabs: List<AvatarPickerTab>,
    selectedTab: AvatarPickerTab,
    isLight: Boolean,
    onSelect: (AvatarPickerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (isLight) lightSegmentTrack else darkSegmentTrack
    val selectedColor = if (isLight) MaterialTheme.colorScheme.background else darkSegmentSelected
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = segmentIndicatorAnimationSpec,
        visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
        label = "avatar_segment_index",
    )
    val progressProvider = remember(animatedIndexState) { { animatedIndexState.value } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(trackColor, CircleShape)
            .padding(segmentTrackPadding)
            .selectableGroup(),
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Box(
                    modifier = Modifier
                        .layoutId(SEGMENT_THUMB_LAYOUT_ID)
                        .shadow(elevation = segmentShadowElevation, shape = CircleShape)
                        .background(selectedColor, CircleShape),
                )
                tabs.forEach { tab ->
                    SegmentTabButton(
                        tab = tab,
                        selected = tab == selectedTab,
                        onSelect = { onSelect(tab) },
                    )
                }
            },
        ) { measurables, constraints ->
            val thumbMeasurable = measurables.first { it.layoutId == SEGMENT_THUMB_LAYOUT_ID }
            val itemMeasurables = measurables.filter { it.layoutId != SEGMENT_THUMB_LAYOUT_ID }
            val count = itemMeasurables.size.coerceAtLeast(1)
            val totalWidth = constraints.maxWidth
            val itemWidth = totalWidth / count
            val itemPlaceables = itemMeasurables.map { measurable ->
                measurable.measure(
                    Constraints(
                        minWidth = itemWidth,
                        maxWidth = itemWidth,
                        minHeight = constraints.minHeight,
                        maxHeight = constraints.maxHeight,
                    ),
                )
            }
            val rowHeight = itemPlaceables.maxOfOrNull { it.height } ?: 0
            val thumbPlaceable = thumbMeasurable.measure(Constraints.fixed(itemWidth, rowHeight))

            layout(totalWidth, rowHeight) {
                val maxIndex = (count - 1).toFloat()
                val progress = progressProvider().coerceIn(0f, maxIndex)
                val thumbX = (progress * itemWidth).roundToInt()
                thumbPlaceable.placeRelative(x = thumbX, y = 0)
                itemPlaceables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x = index * itemWidth, y = 0)
                }
            }
        }
    }
}

@Composable
private fun SegmentTabButton(
    tab: AvatarPickerTab,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) SEGMENT_PRESSED_SCALE else 1f,
        animationSpec = pressScaleAnimationSpec,
        visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
        label = "avatar_segment_press",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary,
        animationSpec = labelColorAnimationSpec,
        label = "avatar_segment_color",
    )

    Box(
        modifier = Modifier
            .testTag(tab.testTag())
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(vertical = segmentItemVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(tab.labelRes()),
            modifier = Modifier.graphicsLayer {
                val scale = pressScaleState.value
                scaleX = scale
                scaleY = scale
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun UniqueBoringTabContent(
    seed: String,
    paletteMode: BoringPaletteMode,
    selectedVariant: BoringAvatarVariant?,
    selectedPalette: BoringAvatarPalette?,
    sheetBackground: Color,
    isLight: Boolean,
    onSelectPalette: (BoringPaletteMode) -> Unit,
    onSelectBoring: (BoringAvatar) -> Unit,
) {
    val isCompactWidth = isCompactDimension(WindowDimension.Width)
    val avatarSize = if (isCompactWidth) boringAvatarCompactSize else boringAvatarDefaultSize
    val avatars = remember(seed, paletteMode) {
        BoringAvatarVariant.entries.mapIndexed { index, variant ->
            BoringAvatar(
                seed = seed,
                variant = variant,
                palette = paletteMode.paletteFor(seed, index),
                paletteMode = paletteMode,
            )
        }
    }
    val topRow = avatars.take(3)
    val bottomRow = avatars.drop(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = paddingDefault, vertical = paddingMiddle),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                topRow.forEach { avatar ->
                    BoringAvatarChoice(
                        avatar = avatar,
                        size = avatarSize,
                        selected = selectedVariant == avatar.variant && selectedPalette == avatar.palette,
                        sheetBackground = sheetBackground,
                        onClick = { onSelectBoring(avatar) },
                    )
                }
            }
            Spacer(modifier = Modifier.size(boringRowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(boringBottomRowGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomRow.forEach { avatar ->
                    BoringAvatarChoice(
                        avatar = avatar,
                        size = avatarSize,
                        selected = selectedVariant == avatar.variant && selectedPalette == avatar.palette,
                        sheetBackground = sheetBackground,
                        onClick = { onSelectBoring(avatar) },
                    )
                }
            }
        }

        PaletteDotTray(
            selectedMode = paletteMode,
            isLight = isLight,
            onSelect = onSelectPalette,
            modifier = Modifier.padding(top = paddingSmall, bottom = paddingSmall),
        )
    }
}

@Composable
private fun BoringAvatarChoice(
    avatar: BoringAvatar,
    size: Dp,
    selected: Boolean,
    sheetBackground: Color,
    onClick: () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = labelColorAnimationSpec,
        label = "avatar_boring_ring_color",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        animationSpec = labelColorAnimationSpec,
        label = "avatar_boring_label_color",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space8),
    ) {
        Box(
            modifier = Modifier
                .border(width = selectionRingWidth, color = ringColor, shape = CircleShape)
                .padding(selectionRingGap)
                .background(sheetBackground, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .testTag("avatar_boring_${avatar.variant.name}")
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            ) {
                Crossfade(
                    targetState = avatar,
                    animationSpec = tween(durationMillis = AVATAR_CROSSFADE_DURATION_MS),
                    label = "avatar_boring_crossfade",
                ) { targetAvatar ->
                    BoringAvatarView(
                        avatar = targetAvatar,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Text(
            text = avatar.variant.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun PaletteDotTray(
    selectedMode: BoringPaletteMode,
    isLight: Boolean,
    onSelect: (BoringPaletteMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trayColor = if (isLight) lightTrayBackground else darkTrayBackground
    val selectionColor = MaterialTheme.colorScheme.primary
    val modes = BoringPaletteMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = paletteRingAnimationSpec,
        visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
        label = "avatar_palette_index",
    )
    val progressProvider = remember(animatedIndexState) { { animatedIndexState.value } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(trayColor, CircleShape)
            .padding(horizontal = paletteTrayHorizontalPadding, vertical = paletteTrayVerticalPadding)
            .selectableGroup(),
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Canvas(
                    modifier = Modifier
                        .layoutId(PALETTE_RING_LAYOUT_ID)
                        .size(paletteDotTouchSize),
                ) {
                    val ringPx = paletteDotRingWidth.toPx()
                    val outerRadius = size.minDimension / 2f
                    drawCircle(
                        color = selectionColor,
                        radius = (outerRadius - ringPx / 2f).coerceAtLeast(1f),
                        style = Stroke(width = ringPx),
                    )
                }
                modes.forEach { mode ->
                    PaletteDot(
                        mode = mode,
                        selected = mode == selectedMode,
                        onClick = { onSelect(mode) },
                    )
                }
            },
        ) { measurables, constraints ->
            val ringMeasurable = measurables.first { it.layoutId == PALETTE_RING_LAYOUT_ID }
            val dotMeasurables = measurables.filter { it.layoutId != PALETTE_RING_LAYOUT_ID }
            val count = dotMeasurables.size.coerceAtLeast(1)
            val totalWidth = constraints.maxWidth
            val slotWidth = totalWidth.toFloat() / count
            val touchSizePx = paletteDotTouchSize.roundToPx().coerceAtMost(slotWidth.roundToInt().coerceAtLeast(1))
            val itemConstraints = Constraints.fixed(touchSizePx, touchSizePx)
            val ringPlaceable = ringMeasurable.measure(itemConstraints)
            val dotPlaceables = dotMeasurables.map { it.measure(itemConstraints) }

            layout(totalWidth, touchSizePx) {
                val maxIndex = (count - 1).toFloat()
                val progress = progressProvider().coerceIn(0f, maxIndex)
                val ringX = (progress * slotWidth + (slotWidth - ringPlaceable.width) / 2f).roundToInt()
                ringPlaceable.placeRelative(x = ringX, y = 0)
                dotPlaceables.forEachIndexed { index, placeable ->
                    val dotX = (index * slotWidth + (slotWidth - placeable.width) / 2f).roundToInt()
                    placeable.placeRelative(x = dotX, y = 0)
                }
            }
        }
    }
}

@Composable
private fun PaletteDot(
    mode: BoringPaletteMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette: BoringAvatarPalette? = mode.fixedPaletteOrNull()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val dotScaleState = animateFloatAsState(
        targetValue = when {
            isPressed -> PALETTE_DOT_PRESSED_SCALE
            selected -> 1f
            else -> PALETTE_DOT_UNSELECTED_SCALE
        },
        animationSpec = pressScaleAnimationSpec,
        visibilityThreshold = ANIMATION_VISIBILITY_THRESHOLD,
        label = "avatar_palette_dot_scale",
    )

    Box(
        modifier = Modifier
            .size(paletteDotTouchSize)
            .testTag("avatar_palette_${mode.name}")
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = dotScaleState.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val ringPx = paletteDotRingWidth.toPx()
            val gapPx = paletteDotInnerGap.toPx()
            val outerRadius = size.minDimension / 2f
            val dotRadius = (outerRadius - ringPx - gapPx).coerceAtLeast(1f)

            if (palette == null) {
                drawCircle(
                    brush = Brush.sweepGradient(mixSweepColors),
                    radius = dotRadius,
                )
            } else {
                val topLeft = Offset(center.x - dotRadius, center.y - dotRadius)
                val arcSize = Size(dotRadius * 2f, dotRadius * 2f)
                drawArc(
                    color = Color(palette.primaryDot),
                    startAngle = 135f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                )
                drawArc(
                    color = Color(palette.secondaryDot),
                    startAngle = -45f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                )
            }
        }
    }
}

@Composable
private fun GeometricPresetsTabContent(
    selectedPreset: AvatarPreset?,
    sheetBackground: Color,
    onSelectPreset: (AvatarPreset) -> Unit,
) {
    val geometricPresets = remember { AvatarPreset.entries.take(GEOMETRIC_PRESET_COUNT) }
    val landscapePresets = remember { AvatarPreset.entries.drop(GEOMETRIC_PRESET_COUNT) }
    val initialScrollIndex = if (selectedPreset != null && selectedPreset.ordinal >= LANDSCAPE_SCROLL_THRESHOLD) {
        LANDSCAPE_INITIAL_SCROLL_INDEX
    } else {
        0
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex)

    LazyVerticalGrid(
        columns = GridCells.Fixed(PRESET_COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(paddingDefault),
    ) {
        items(geometricPresets, key = { it.name }) { preset ->
            PresetGridItem(
                preset = preset,
                selected = preset == selectedPreset,
                sheetBackground = sheetBackground,
                onSelect = { onSelectPreset(preset) },
            )
        }
        if (landscapePresets.isNotEmpty()) {
            item(key = "preset_group_divider", span = { GridItemSpan(PRESET_COLUMNS) }) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = space8, vertical = paddingSmall),
                    thickness = hairlineThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            items(landscapePresets, key = { it.name }) { preset ->
                PresetGridItem(
                    preset = preset,
                    selected = preset == selectedPreset,
                    sheetBackground = sheetBackground,
                    onSelect = { onSelectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun PresetGridItem(
    preset: AvatarPreset,
    selected: Boolean,
    sheetBackground: Color,
    onSelect: () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = labelColorAnimationSpec,
        label = "avatar_preset_ring_color",
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(space4)
            .border(width = selectionRingWidth, color = ringColor, shape = CircleShape)
            .padding(selectionRingGap)
            .background(sheetBackground, CircleShape),
    ) {
        AvatarPresetView(
            preset = preset,
            modifier = Modifier
                .fillMaxSize()
                .testTag("avatar_preset_${preset.name}"),
            onClick = onSelect,
        )
    }
}

@StringRes
private fun AvatarPickerTab.labelRes(): Int = when (this) {
    AvatarPickerTab.Unique -> R.string.avatar_unique
    AvatarPickerTab.Presets -> R.string.avatar_presets
    AvatarPickerTab.Nft -> R.string.avatar_nft
}

private fun AvatarPickerTab.testTag(): String = when (this) {
    AvatarPickerTab.Unique -> "avatar_tab_unique"
    AvatarPickerTab.Presets -> "avatar_tab_presets"
    AvatarPickerTab.Nft -> "avatar_tab_nft"
}

private fun resolvePaletteMode(
    mode: BoringPaletteMode,
    variant: BoringAvatarVariant,
    palette: BoringAvatarPalette,
    addressSeed: String?,
): BoringPaletteMode = when {
    mode == BoringPaletteMode.Mix &&
        addressSeed != null &&
        BoringPaletteMode.Mix.paletteFor(addressSeed, variant.ordinal) == palette -> BoringPaletteMode.Mix
    else -> BoringPaletteMode.fromPalette(palette)
}
