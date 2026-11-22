package com.gemwallet.android.ui.components.list_item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.components.GemDropdownMenuDivider
import com.gemwallet.android.ui.components.GemDropdownMenuGroupDivider
import com.gemwallet.android.ui.components.GemDropdownMenuItem
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.icons.cachedDrawablePainter
import com.wallet.core.primitives.AssetId

@Immutable
data class AssetContextActions(val onTogglePin: ((AssetId) -> Unit)? = null, val onHide: ((AssetId) -> Unit)? = null, val onAddToWallet: ((AssetId) -> Unit)? = null) {
    val isEmpty: Boolean
        get() = onTogglePin == null && onHide == null && onAddToWallet == null

    companion object {
        val Empty = AssetContextActions()
    }
}

@Composable
private fun ColumnScope.AssetContextMenuItems(assetId: AssetId, address: String?, isPinned: Boolean, isBalanceEnabled: Boolean, actions: AssetContextActions, onDismiss: () -> Unit) {
    val clipboard = rememberClipboard()
    val items = remember(clipboard, assetId, address, isPinned, isBalanceEnabled, actions) {
        assetContextMenuItems(clipboard, assetId, address, isPinned, isBalanceEnabled, actions)
    }
    items.forEachIndexed { index, item ->
        if (index > 0) {
            if (item.group != items[index - 1].group) {
                GemDropdownMenuGroupDivider()
            } else {
                GemDropdownMenuDivider()
            }
        }
        GemDropdownMenuItem(
            text = stringResource(item.titleRes),
            trailingIcon = cachedDrawablePainter(item.iconRes),
            onClick = {
                item.onClick()
                onDismiss()
            },
        )
    }
}

@Composable
fun AssetContextMenuRow(
    assetId: AssetId,
    address: String?,
    isPinned: Boolean,
    isBalanceEnabled: Boolean,
    longPressed: MutableState<AssetId?>,
    actions: AssetContextActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (actions.isEmpty) {
        content(modifier.clickable(onClick = onClick))
        return
    }
    val onDismiss = { longPressed.value = null }
    DropDownContextItem(
        modifier = modifier,
        isExpanded = longPressed.value == assetId,
        onDismiss = onDismiss,
        menuItems = { AssetContextMenuItems(assetId, address, isPinned, isBalanceEnabled, actions, onDismiss) },
        content = content,
        onLongClick = { longPressed.value = assetId },
        onClick = onClick,
    )
}
