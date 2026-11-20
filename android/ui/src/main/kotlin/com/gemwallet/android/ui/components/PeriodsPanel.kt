package com.gemwallet.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.sp
import com.gemwallet.android.ui.localization.stringRes
import com.gemwallet.android.ui.theme.alpha60
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space6
import com.wallet.core.primitives.ChartPeriod
import kotlin.math.roundToInt

private const val INDICATOR_LAYOUT_ID = "indicator"
private const val PERIOD_PRESSED_SCALE = 0.94f
private const val PERIOD_ANIMATION_VISIBILITY_THRESHOLD = 0.001f
private const val FULL_SLOT_WEIGHT = 1f
private const val EXIT_ALPHA_CUTOFF = 0.12f

private val periodButtonShape = RoundedCornerShape(paddingSmall)
private val periodIndicatorAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = PERIOD_ANIMATION_VISIBILITY_THRESHOLD,
)
private val periodColorAnimationSpec = spring<Color>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val periodPressAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
    visibilityThreshold = PERIOD_ANIMATION_VISIBILITY_THRESHOLD,
)

internal data class PeriodBounds(val left: Int, val width: Int)

internal fun periodSlotBounds(weights: FloatArray, totalWidth: Int): List<PeriodBounds> {
    val totalWeight = weights.sum()
    if (weights.isEmpty() || totalWidth <= 0 || totalWeight <= 0f) {
        return List(weights.size) { PeriodBounds(0, 0) }
    }
    var cumulative = 0f
    return List(weights.size) { index ->
        val start = (cumulative / totalWeight * totalWidth).roundToInt()
        cumulative += weights[index].coerceAtLeast(0f)
        val end = (cumulative / totalWeight * totalWidth).roundToInt()
        PeriodBounds(left = start, width = (end - start).coerceAtLeast(0))
    }
}

internal fun periodIndicatorBounds(progress: Float, weights: FloatArray, totalWidth: Int): PeriodBounds {
    val totalWeight = weights.sum()
    if (weights.isEmpty() || totalWidth <= 0 || totalWeight <= 0f) {
        return PeriodBounds(0, 0)
    }
    val maxWeight = weights.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val indicatorWidth = (maxWeight / totalWeight * totalWidth).roundToInt().coerceIn(0, totalWidth)
    val clamped = progress.coerceIn(0f, (weights.size - 1).toFloat())
    val lower = clamped.toInt().coerceIn(0, weights.lastIndex)
    val upper = (lower + 1).coerceAtMost(weights.lastIndex)
    val fraction = clamped - lower

    var lowerStartWeight = 0f
    for (i in 0 until lower) {
        lowerStartWeight += weights[i].coerceAtLeast(0f)
    }
    val upperStartWeight = lowerStartWeight + weights[lower].coerceAtLeast(0f)
    val startWeight = lowerStartWeight + (upperStartWeight - lowerStartWeight) * fraction
    val maxLeft = (totalWidth - indicatorWidth).coerceAtLeast(0)
    val left = (startWeight / totalWeight * totalWidth).roundToInt().coerceIn(0, maxLeft)
    return PeriodBounds(left = left, width = indicatorWidth)
}

private fun slotAlpha(weight: Float): Float = ((weight - EXIT_ALPHA_CUTOFF) / (FULL_SLOT_WEIGHT - EXIT_ALPHA_CUTOFF)).coerceIn(0f, 1f)

@Composable
fun PeriodsPanel(period: ChartPeriod, onSelect: (ChartPeriod) -> Unit, periods: List<ChartPeriod>) {
    if (periods.isEmpty()) {
        return
    }
    val allPeriods = ChartPeriod.entries
    val activePeriods = remember(periods) { periods.toSet() }
    val slotWeights = remember { allPeriods.map { Animatable(if (it in activePeriods) FULL_SLOT_WEIGHT else 0f) } }

    allPeriods.forEachIndexed { index, item ->
        val isVisible = item in activePeriods
        LaunchedEffect(item, isVisible) {
            val target = if (isVisible) FULL_SLOT_WEIGHT else 0f
            if (slotWeights[index].value != target) {
                slotWeights[index].animateTo(target, periodIndicatorAnimationSpec)
            }
        }
    }

    val hasSelection = period in activePeriods
    val selectedSlotIndex = allPeriods.indexOf(period).coerceAtLeast(0)
    val animatedSlotState = animateFloatAsState(
        targetValue = selectedSlotIndex.toFloat(),
        animationSpec = periodIndicatorAnimationSpec,
        visibilityThreshold = PERIOD_ANIMATION_VISIBILITY_THRESHOLD,
    )
    val indicatorProgress = remember(animatedSlotState) { { animatedSlotState.value } }

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingDefault, end = paddingDefault, bottom = paddingDefault)
            .clipToBounds()
            .selectableGroup(),
        content = {
            if (hasSelection) {
                Box(
                    modifier = Modifier
                        .layoutId(INDICATOR_LAYOUT_ID)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = periodButtonShape,
                        ),
                )
            }
            allPeriods.forEachIndexed { index, item ->
                val isVisible = item in activePeriods
                val hasWeight by remember(index) { derivedStateOf { slotWeights[index].value > 0f } }
                if (isVisible || hasWeight) {
                    val weightProvider = remember(index) { { slotWeights[index].value } }
                    val baseModifier = if (isVisible) {
                        Modifier.layoutId(item).testTag("period_${item.string}")
                    } else {
                        Modifier.layoutId(item).clearAndSetSemantics {}
                    }
                    PeriodButton(
                        title = stringResource(item.stringRes()),
                        isSelected = isVisible && item == period,
                        hasSelection = hasSelection,
                        enabled = isVisible,
                        weightProvider = weightProvider,
                        modifier = baseModifier,
                        onClick = { onSelect(item) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val weights = FloatArray(allPeriods.size) { index -> slotWeights[index].value.coerceAtLeast(0f) }
        val indicatorMeasurable = measurables.firstOrNull { it.layoutId == INDICATOR_LAYOUT_ID }
        val itemMeasurables = measurables.filter { it.layoutId is ChartPeriod }
        val activeCount = weights.count { it > 0f }.coerceAtLeast(1)
        val totalWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            val desiredSlotWidth = itemMeasurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
            constraints.constrainWidth(desiredSlotWidth * activeCount)
        }
        val slotBounds = periodSlotBounds(weights, totalWidth)
        val totalWeight = weights.sum()
        val maxWeight = weights.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val activeSlotWidth = if (totalWeight > 0f) {
            (maxWeight / totalWeight * totalWidth).roundToInt().coerceIn(0, totalWidth)
        } else {
            0
        }

        val placedItems = itemMeasurables.map { measurable ->
            val slotIndex = allPeriods.indexOf(measurable.layoutId as ChartPeriod)
            val bound = slotBounds[slotIndex]
            val measureWidth = if (weights[slotIndex] < FULL_SLOT_WEIGHT) activeSlotWidth else bound.width
            val placeable = measurable.measure(
                Constraints(
                    minWidth = measureWidth,
                    maxWidth = measureWidth,
                    minHeight = constraints.minHeight,
                    maxHeight = constraints.maxHeight,
                ),
            )
            slotIndex to placeable
        }
        val rowHeight = placedItems.maxOfOrNull { it.second.height }?.coerceAtLeast(constraints.minHeight) ?: constraints.minHeight
        val indicatorPlaceable = indicatorMeasurable?.measure(Constraints.fixed(activeSlotWidth, rowHeight))

        layout(totalWidth, rowHeight) {
            indicatorPlaceable?.let { placeable ->
                val bounds = periodIndicatorBounds(
                    progress = indicatorProgress(),
                    weights = weights,
                    totalWidth = totalWidth,
                )
                placeable.placeRelative(x = bounds.left, y = 0)
            }
            placedItems.forEach { (slotIndex, placeable) ->
                val bound = slotBounds[slotIndex]
                val itemX = bound.left + (bound.width - placeable.width) / 2
                val itemY = (rowHeight - placeable.height) / 2
                placeable.placeRelative(x = itemX, y = itemY)
            }
        }
    }
}

@Composable
private fun PeriodButton(
    title: String,
    isSelected: Boolean,
    hasSelection: Boolean,
    enabled: Boolean,
    weightProvider: () -> Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) PERIOD_PRESSED_SCALE else 1f,
        animationSpec = periodPressAnimationSpec,
        visibilityThreshold = PERIOD_ANIMATION_VISIBILITY_THRESHOLD,
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected || !hasSelection) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha60)
        },
        animationSpec = periodColorAnimationSpec,
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = slotAlpha(weightProvider())
            }
            .clip(periodButtonShape)
            .selectable(
                selected = isSelected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(space6),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.graphicsLayer {
                val scale = pressScaleState.value
                scaleX = scale
                scaleY = scale
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MaterialTheme.typography.labelMedium.fontSize,
                maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                stepSize = 1.sp,
            ),
        )
    }
}
