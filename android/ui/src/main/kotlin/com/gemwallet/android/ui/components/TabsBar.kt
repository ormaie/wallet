package com.gemwallet.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.gemwallet.android.ui.theme.alpha60
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space2
import kotlin.math.roundToInt

private const val THUMB_LAYOUT_ID = "thumb"
private const val DARK_LUMINANCE_THRESHOLD = 0.5f
private val tabHeight = 36.dp
private val trackPadding = 3.dp
private val equalTabHorizontalPadding = 18.dp
private val minEqualTabWidth = 72.dp
private val tabsBarAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun <T> rememberTabsBarProgress(tabs: List<T>, selected: T): () -> Float {
    val targetIndex = tabs.indexOf(selected).coerceAtLeast(0).toFloat()
    val progressState = animateFloatAsState(
        targetValue = targetIndex,
        animationSpec = tabsBarAnimationSpec,
    )
    return remember(progressState) { { progressState.value } }
}

@Composable
fun <T> TabsBar(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    scrollable: Boolean = false,
    equalWidth: Boolean = true,
    progress: (() -> Float)? = null,
    itemContent: @Composable RowScope.(T) -> Unit,
) {
    if (tabs.isEmpty()) {
        return
    }
    val selectedIndex = tabs.indexOf(selected)
    val hasSelection = selectedIndex >= 0
    val resolvedProgress = progress ?: rememberTabsBarProgress(tabs, selected)
    val scrollState = rememberScrollState()

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < DARK_LUMINANCE_THRESHOLD
    val trackColor = colorScheme.surfaceContainerHighest
    val thumbColor = if (isDark) colorScheme.outlineVariant else colorScheme.background
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val itemHorizontalPadding = if (equalWidth) equalTabHorizontalPadding else paddingMiddle

    Box(
        modifier = Modifier
            .padding(horizontal = paddingDefault)
            .then(
                if (scrollable) {
                    Modifier.horizontalScroll(scrollState)
                } else {
                    Modifier
                },
            )
            .height(tabHeight)
            .then(
                if (hasSelection) {
                    Modifier
                        .clip(CircleShape)
                        .background(trackColor)
                        .padding(trackPadding)
                } else {
                    Modifier
                },
            ),
    ) {
        Layout(
            content = {
                if (hasSelection) {
                    Box(
                        modifier = Modifier
                            .layoutId(THUMB_LAYOUT_ID)
                            .shadow(elevation = space2, shape = CircleShape)
                            .background(color = thumbColor, shape = CircleShape),
                    )
                }
                tabs.forEach { item ->
                    val isSelected = item == selected
                    val contentColor = if (isSelected || !hasSelection) {
                        colorScheme.onSurface
                    } else {
                        colorScheme.onSurface.copy(alpha = alpha60)
                    }
                    val itemModifier = if (hasSelection) {
                        Modifier
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { onSelect(item) },
                            )
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(trackColor)
                            .clickable { onSelect(item) }
                    }
                    Box(
                        modifier = itemModifier.padding(horizontal = itemHorizontalPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides contentColor,
                            LocalTextStyle provides textStyle,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                content = { itemContent(item) },
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val thumbMeasurable = measurables.firstOrNull { it.layoutId == THUMB_LAYOUT_ID }
            val itemMeasurables = measurables.filter { it.layoutId != THUMB_LAYOUT_ID }
            val count = itemMeasurables.size
            val fallbackHeight = if (hasSelection) tabHeight - trackPadding * 2 else tabHeight
            val itemHeight = (if (constraints.hasBoundedHeight) constraints.maxHeight else fallbackHeight.roundToPx()).coerceAtLeast(0)

            if (equalWidth) {
                val minWidthPx = minEqualTabWidth.roundToPx()
                val desiredItemWidth = itemMeasurables.maxOf { it.maxIntrinsicWidth(itemHeight) }.coerceAtLeast(minWidthPx)
                val totalWidth = constraints.constrainWidth(desiredItemWidth * count)
                val itemWidth = (totalWidth / count).coerceAtLeast(0)
                val finalWidth = (itemWidth * count).coerceAtLeast(constraints.minWidth)
                val itemConstraints = Constraints.fixed(itemWidth, itemHeight)
                val itemPlaceables = itemMeasurables.map { it.measure(itemConstraints) }
                val thumbPlaceable = thumbMeasurable?.measure(itemConstraints)

                layout(finalWidth, itemHeight) {
                    if (thumbPlaceable != null) {
                        val currentProgress = resolvedProgress().coerceIn(0f, (count - 1).toFloat())
                        val thumbX = (currentProgress * itemWidth).roundToInt()
                        thumbPlaceable.placeRelative(x = thumbX, y = 0)
                    }
                    itemPlaceables.forEachIndexed { index, placeable ->
                        placeable.placeRelative(x = index * itemWidth, y = 0)
                    }
                }
            } else {
                val spacingPx = if (hasSelection) 0 else paddingSmall.roundToPx()
                val itemConstraints = Constraints(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = itemHeight,
                    maxHeight = itemHeight,
                )
                val itemPlaceables = itemMeasurables.map { it.measure(itemConstraints) }
                val itemOffsets = IntArray(count)
                var currentX = 0
                itemPlaceables.forEachIndexed { index, placeable ->
                    itemOffsets[index] = currentX
                    currentX += placeable.width
                    if (index < count - 1) {
                        currentX += spacingPx
                    }
                }
                val finalWidth = constraints.constrainWidth(currentX)
                val currentProgress = resolvedProgress().coerceIn(0f, (count - 1).toFloat())
                val lower = currentProgress.toInt().coerceIn(0, count - 1)
                val upper = (lower + 1).coerceAtMost(count - 1)
                val fraction = currentProgress - lower
                val startWidth = itemPlaceables[lower].width
                val endWidth = itemPlaceables[upper].width
                val thumbWidth = (startWidth + (endWidth - startWidth) * fraction).roundToInt().coerceAtLeast(0)
                val thumbX = (itemOffsets[lower] + (itemOffsets[upper] - itemOffsets[lower]) * fraction).roundToInt()
                val thumbPlaceable = thumbMeasurable?.measure(Constraints.fixed(thumbWidth, itemHeight))

                layout(finalWidth, itemHeight) {
                    thumbPlaceable?.placeRelative(x = thumbX, y = 0)
                    itemPlaceables.forEachIndexed { index, placeable ->
                        placeable.placeRelative(x = itemOffsets[index], y = 0)
                    }
                }
            }
        }
    }
}
