package com.gemwallet.android.features.wallet.presents.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.GemDropdownMenuDivider
import com.gemwallet.android.ui.components.GemDropdownMenuGroupDivider
import com.gemwallet.android.ui.components.GemDropdownMenuItem
import com.gemwallet.android.ui.components.list_item.DropDownContextItem
import com.gemwallet.android.ui.components.list_item.WalletItem
import com.gemwallet.android.ui.components.list_item.pinnedHeader
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.models.ListPosition
import uniffi.gemstone.GemWalletRow
import uniffi.gemstone.GemWalletSection
import uniffi.gemstone.GemWalletSectionKind

internal fun LazyListScope.wallets(
    section: GemWalletSection,
    offlineWalletIds: Set<String> = emptySet(),
    longPressedWallet: MutableState<String>,
    onEdit: (GemWalletRow) -> Unit,
    onSelectWallet: (GemWalletRow) -> Unit,
    onDeleteWallet: (GemWalletRow) -> Unit,
    onTogglePin: (GemWalletRow) -> Unit,
) {
    if (section.kind == GemWalletSectionKind.PINNED) {
        pinnedHeader()
    }
    itemsIndexed(
        items = section.rows,
        key = { _, row -> row.id },
        contentType = { _, _ -> "wallet_row" },
    ) { index, row ->
        DropDownContextItem(
            isExpanded = longPressedWallet.value == row.id,
            onDismiss = { longPressedWallet.value = "" },
            content = {
                WalletItem(
                    row = row,
                    isCurrent = row.isCurrent,
                    listPosition = ListPosition.getPosition(index, section.rows.size),
                    isOffline = row.id in offlineWalletIds,
                    onEdit = { onEdit(row) },
                    modifier = it,
                )
            },
            menuItems = {
                GemDropdownMenuItem(
                    text = stringResource(if (row.isPinned) R.string.common_unpin else R.string.common_pin),
                    trailingIcon = if (row.isPinned) AppIcons.KeepOff else AppIcons.PushPin,
                    onClick = {
                        onTogglePin(row)
                        longPressedWallet.value = ""
                    },
                )
                GemDropdownMenuDivider()
                GemDropdownMenuItem(
                    text = stringResource(R.string.common_wallet),
                    trailingIcon = AppIcons.SettingsOutlined,
                    onClick = {
                        onEdit(row)
                        longPressedWallet.value = ""
                    },
                )
                GemDropdownMenuGroupDivider()
                GemDropdownMenuItem(
                    text = stringResource(R.string.common_delete),
                    trailingIcon = AppIcons.Delete,
                    isDestructive = true,
                    onClick = {
                        onDeleteWallet(row)
                        longPressedWallet.value = ""
                    },
                )
            },
            onLongClick = { longPressedWallet.value = row.id },
        ) { onSelectWallet(row) }
    }
}
