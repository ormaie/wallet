package com.gemwallet.android.ui.components.list_item.property

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.GemDropdownMenuDivider
import com.gemwallet.android.ui.components.GemDropdownMenuGroupDivider
import com.gemwallet.android.ui.components.GemDropdownMenuItem
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.components.image.ListItemImageView
import com.gemwallet.android.ui.components.list_item.DropDownContextItem
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.components.list_item.ListItemImageStyle
import com.gemwallet.android.ui.components.list_item.listItemImage
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.open
import com.wallet.core.primitives.BlockExplorerLink
import uniffi.gemstone.GemAddressRow
import uniffi.gemstone.GemRecipient
import uniffi.gemstone.GemRowMenuItem

@Composable
fun AddressPropertyItem(row: GemAddressRow, listPosition: ListPosition = ListPosition.Middle, onClick: (() -> Unit)? = null, onCreateContact: ((GemRecipient) -> Unit)? = null, onAddToContact: ((GemRecipient) -> Unit)? = null) {
    var isExpanded by remember { mutableStateOf(false) }
    var showsAddress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = rememberClipboard()
    val uriHandler = LocalUriHandler.current
    val select = onClick.takeIf { row.isSelectable }
    val image = row.avatar?.listItemImage()

    DropDownContextItem(
        isExpanded = isExpanded,
        onDismiss = { isExpanded = false },
        onLongClick = { isExpanded = row.menu.isNotEmpty() || (row.contact != null && onCreateContact != null) },
        onClick = select ?: { showsAddress = row.shortAddress != null && !showsAddress },
        content = { modifier ->
            PropertyItem(
                modifier = modifier,
                title = { PropertyTitleText(text = row.title.string(context)) },
                data = {
                    PropertyDataText(
                        text = row.shortAddress?.takeIf { showsAddress } ?: row.text.string(context),
                        badge = when {
                            image != null -> {
                                { DataBadgeChevron(select != null) { ListItemImageView(image = image, style = ListItemImageStyle.Glyph) } }
                            }

                            select != null -> {
                                { DataBadgeChevron() }
                            }

                            else -> null
                        },
                    )
                },
                listPosition = listPosition,
            )
        },
        menuItems = {
            row.menu.forEachIndexed { index, item ->
                if (index > 0) GemDropdownMenuDivider()
                when (item) {
                    is GemRowMenuItem.Copy -> GemDropdownMenuItem(
                        text = stringResource(id = R.string.common_copy),
                        trailingIcon = AppIcons.ContentCopyOutlined,
                        onClick = {
                            isExpanded = false
                            clipboard.copy(item.copy)
                        },
                    )

                    is GemRowMenuItem.Open -> GemDropdownMenuItem(
                        text = item.title.string(context),
                        trailingIcon = AppIcons.OpenInNew,
                        onClick = {
                            isExpanded = false
                            uriHandler.open(context, item.url)
                        },
                    )
                }
            }
            val contact = row.contact
            if (contact != null && onCreateContact != null && onAddToContact != null) {
                if (row.menu.isNotEmpty()) GemDropdownMenuGroupDivider()
                GemDropdownMenuItem(
                    text = stringResource(id = R.string.contacts_create_new_contact),
                    trailingIcon = AppIcons.Add,
                    onClick = {
                        isExpanded = false
                        onCreateContact(contact)
                    },
                )
                GemDropdownMenuDivider()
                GemDropdownMenuItem(
                    text = stringResource(id = R.string.contacts_add_to_existing_contact),
                    trailingIcon = AppIcons.PersonOutlined,
                    onClick = {
                        isExpanded = false
                        onAddToContact(contact)
                    },
                )
            }
        },
    )
}

@Composable
fun AddressPropertyItem(title: String, displayText: String, copyValue: String, image: ListItemImage? = null, explorerLink: BlockExplorerLink? = null, listPosition: ListPosition = ListPosition.Middle, onClick: (() -> Unit)? = null) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboard = rememberClipboard()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    DropDownContextItem(
        isExpanded = isExpanded,
        onDismiss = { isExpanded = false },
        onLongClick = { isExpanded = true },
        onClick = onClick ?: { explorerLink?.let { link -> uriHandler.open(context, link.link) } ?: Unit },
        content = { modifier ->
            PropertyItem(
                modifier = modifier,
                title = { PropertyTitleText(text = title) },
                data = {
                    PropertyDataText(
                        text = displayText,
                        badge = when {
                            image != null -> {
                                { DataBadgeChevron(onClick != null || explorerLink != null) { ListItemImageView(image = image, style = ListItemImageStyle.Glyph) } }
                            }

                            onClick != null || explorerLink != null -> {
                                { DataBadgeChevron() }
                            }

                            else -> null
                        },
                    )
                },
                listPosition = listPosition,
            )
        },
        menuItems = {
            GemDropdownMenuItem(
                text = stringResource(id = R.string.wallet_copy_address),
                trailingIcon = AppIcons.ContentCopyOutlined,
                onClick = {
                    isExpanded = false
                    clipboard.copy(copyValue)
                },
            )
            if (explorerLink != null) {
                GemDropdownMenuDivider()
                GemDropdownMenuItem(
                    text = stringResource(id = R.string.transaction_view_on, explorerLink.name),
                    trailingIcon = AppIcons.OpenInNew,
                    onClick = {
                        isExpanded = false
                        uriHandler.open(context, explorerLink.link)
                    },
                )
            }
        },
    )
}
