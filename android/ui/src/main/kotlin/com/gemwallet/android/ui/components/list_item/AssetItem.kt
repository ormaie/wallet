package com.gemwallet.android.ui.components.list_item

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.gemwallet.android.domains.asset.aggregates.AssetInfoDataAggregate
import com.gemwallet.android.domains.balance.hiddenWhen
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.NumericText
import com.gemwallet.android.ui.components.image.AssetIcon
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.style.color
import com.gemwallet.android.ui.theme.adaptivePadding
import com.gemwallet.android.ui.theme.alpha20
import com.gemwallet.android.ui.theme.alpha60
import com.gemwallet.android.ui.theme.compactIconSize
import com.gemwallet.android.ui.theme.iconSize
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.space0
import com.gemwallet.android.ui.theme.space6
import com.wallet.core.primitives.Asset
import kotlinx.coroutines.delay
import uniffi.gemstone.GemAssetIcon
import uniffi.gemstone.GemAssetItemRow
import uniffi.gemstone.GemAssetItemTrailing
import uniffi.gemstone.GemFormattedNumber
import uniffi.gemstone.GemRowText
import uniffi.gemstone.GemValueTone

@Composable
private fun assetListItemContentPadding(): Dp = adaptivePadding(default = paddingMiddle, compact = space6)

sealed interface AssetItemAction {
    data class Switch(val enabled: Boolean) : AssetItemAction
    data object Copy : AssetItemAction
}

@Composable
fun AssetListItem(asset: AssetInfoDataAggregate, listPosition: ListPosition, modifier: Modifier = Modifier, onAction: ((AssetItemAction) -> Unit)? = null, accessory: (@Composable () -> Unit)? = null) {
    AssetListItem(row = asset.row, listPosition = listPosition, modifier = modifier, hideBalance = asset.hideBalance, onAction = onAction, accessory = accessory)
}

@Composable
fun AssetListItem(row: GemAssetItemRow, listPosition: ListPosition, modifier: Modifier = Modifier, hideBalance: Boolean = false, onAction: ((AssetItemAction) -> Unit)? = null, accessory: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val hidden = hideBalance && row.masksBalance
    val trailing: (@Composable () -> Unit)? = when (val value = row.trailing) {
        is GemAssetItemTrailing.Value -> getBalanceInfo(value.value, value.extra, hidden)

        is GemAssetItemTrailing.Toggle -> {
            { Switch(checked = value.isOn, onCheckedChange = { onAction?.invoke(AssetItemAction.Switch(it)) }, modifier = Modifier.testTag("asset_toggle").semantics { contentDescription = row.title }) }
        }

        GemAssetItemTrailing.Copy -> {
            {
                AssetCopyButton(onClick = { onAction?.invoke(AssetItemAction.Copy) })
            }
        }

        GemAssetItemTrailing.None -> null
    }
    ListItem(
        modifier = modifier,
        listPosition = listPosition,
        minHeight = ListItemDefaults.iconMinHeight,
        contentPadding = assetListItemContentPadding(),
        titleSubtitleSpacing = space0,
        leading = @Composable { AssetIcon(row.icon) },
        title = @Composable { ListItemTitleText(row.title, { Badge(text = row.titleExtra) }) },
        subtitle = row.subtitle?.let { subtitle -> { AssetItemSupport(subtitle.string(context), subtitle.tone, row.subtitleExtra?.let { it.string(context) to it.tone }) } },
        trailing = if (trailing == null && accessory == null) {
            null
        } else {
            {
                trailing?.invoke()
                accessory?.invoke()
            }
        },
    )
}

@Composable
private fun AssetItemSupport(text: String, tone: GemValueTone, extra: Pair<String, GemValueTone>?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(paddingHalfSmall),
    ) {
        NumericText(
            modifier = Modifier.weight(1f, false),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            color = tone.color(),
            style = MaterialTheme.typography.bodyMedium,
        )
        extra?.let { (extraText, extraTone) ->
            NumericText(
                text = extraText,
                maxLines = 1,
                color = extraTone.color(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun AssetListItem(
    asset: Asset,
    listPosition: ListPosition,
    modifier: Modifier = Modifier,
    icon: GemAssetIcon? = null,
    title: String = asset.name,
    support: @Composable (() -> Unit)? = null,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        listPosition = listPosition,
        minHeight = ListItemDefaults.iconMinHeight,
        contentPadding = assetListItemContentPadding(),
        titleSubtitleSpacing = space0,
        leading = @Composable { icon?.let { AssetIcon(it) } ?: AssetIcon(asset) },
        title = @Composable { ListItemTitleText(title, { Badge(text = badge) }) },
        subtitle = support,
        trailing = if (trailing == null) {
            null
        } else {
            { trailing.invoke() }
        },
    )
}

@Composable
fun Badge(text: String?) {
    if (text.isNullOrEmpty()) {
        return
    }
    Text(
        modifier = Modifier,
        text = text,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W400,
    )
}

fun getBalanceInfo(crypto: String, equivalent: String): @Composable () -> Unit = (
    @Composable {
        BalanceInfo(crypto = crypto, equivalent = equivalent, color = MaterialTheme.colorScheme.onSurface)
    }
    )

fun getBalanceInfo(value: GemRowText, extra: GemRowText?, hidden: Boolean = false): @Composable () -> Unit = (
    @Composable {
        val context = LocalContext.current
        BalanceInfo(
            crypto = value.string(context).hiddenWhen(hidden),
            equivalent = extra?.string(context)?.hiddenWhen(hidden).orEmpty(),
            color = value.tone.color(),
            equivalentColor = extra?.tone?.color() ?: MaterialTheme.colorScheme.secondary,
        )
    }
    )

fun getBalanceInfo(amount: GemFormattedNumber, equivalent: GemFormattedNumber?): @Composable () -> Unit = (
    @Composable {
        BalanceInfo(crypto = amount.text(), equivalent = equivalent?.text().orEmpty(), color = amount.tone.color())
    }
    )

@Composable
private fun BalanceInfo(crypto: String, equivalent: String, color: Color, equivalentColor: Color = MaterialTheme.colorScheme.secondary) {
    Column(
        horizontalAlignment = Alignment.End,
    ) {
        NumericText(
            modifier = Modifier,
            text = crypto,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        if (equivalent.isNotEmpty()) {
            NumericText(
                modifier = Modifier,
                text = equivalent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                color = equivalentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val COPY_FEEDBACK_DURATION_MS = 1_500L
private const val COPY_ICON_ANIMATION_MS = 180
private const val COPY_ICON_INITIAL_SCALE = 0.6f

private class AssetCopyFeedbackState(
    val transition: Transition<Boolean>,
    val containerColor: Color,
    val iconTint: Color,
)

@Composable
private fun rememberAssetCopyFeedback(
    copyTrigger: Int,
    isCopied: Boolean,
    onReset: (Int) -> Unit,
    idleContainerColor: Color,
    idleIconTint: Color,
): AssetCopyFeedbackState {
    LaunchedEffect(copyTrigger) {
        delay(COPY_FEEDBACK_DURATION_MS)
        onReset(copyTrigger)
    }

    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = isCopied
    val transition = rememberTransition(transitionState, label = "asset_copy_feedback")
    val copiedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha20)
    val copiedIconTint = MaterialTheme.colorScheme.tertiary

    val containerColor by transition.animateColor(
        transitionSpec = { tween(COPY_ICON_ANIMATION_MS) },
        label = "asset_copy_container",
    ) { copied ->
        if (copied) copiedContainerColor else idleContainerColor
    }
    val iconTint by transition.animateColor(
        transitionSpec = { tween(COPY_ICON_ANIMATION_MS) },
        label = "asset_copy_tint",
    ) { copied ->
        if (copied) copiedIconTint else idleIconTint
    }
    return AssetCopyFeedbackState(
        transition = transition,
        containerColor = containerColor,
        iconTint = iconTint,
    )
}

@Composable
private fun AssetCopyButton(onClick: () -> Unit) {
    var copyTrigger by remember { mutableIntStateOf(0) }
    var isCopied by remember { mutableStateOf(false) }
    val idleContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha60)
    val idleIconTint = MaterialTheme.colorScheme.secondary
    val copyDescription = stringResource(R.string.common_copy)

    val feedback = if (copyTrigger > 0) {
        rememberAssetCopyFeedback(
            copyTrigger = copyTrigger,
            isCopied = isCopied,
            onReset = { trigger ->
                if (copyTrigger == trigger) {
                    isCopied = false
                }
            },
            idleContainerColor = idleContainerColor,
            idleIconTint = idleIconTint,
        )
    } else {
        null
    }
    val containerColor = feedback?.containerColor ?: idleContainerColor
    val iconTint = feedback?.iconTint ?: idleIconTint
    val description = if (isCopied) stringResource(R.string.common_copied_to_clipboard) else copyDescription

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                role = Role.Button,
                onClickLabel = copyDescription,
            ) {
                isCopied = true
                copyTrigger++
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (feedback == null) {
            Icon(
                imageVector = AppIcons.ContentCopyOutlined,
                contentDescription = null,
                modifier = Modifier.size(compactIconSize),
                tint = iconTint,
            )
        } else {
            feedback.transition.AnimatedContent(
                transitionSpec = {
                    (fadeIn(tween(COPY_ICON_ANIMATION_MS)) + scaleIn(tween(COPY_ICON_ANIMATION_MS), initialScale = COPY_ICON_INITIAL_SCALE))
                        .togetherWith(fadeOut(tween(COPY_ICON_ANIMATION_MS)) + scaleOut(tween(COPY_ICON_ANIMATION_MS), targetScale = COPY_ICON_INITIAL_SCALE))
                },
            ) { copied ->
                Icon(
                    imageVector = if (copied) AppIcons.Check else AppIcons.ContentCopyOutlined,
                    contentDescription = null,
                    modifier = Modifier.size(compactIconSize),
                    tint = iconTint,
                )
            }
        }
    }
}
