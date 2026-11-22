package com.gemwallet.android.features.wallet.presents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.ext.toPrimitives
import com.gemwallet.android.features.assets.presents.select.RecentsScreen
import com.gemwallet.android.features.assets.presents.select.SelectAssetAction
import com.gemwallet.android.features.assets.presents.select.SelectAssetScene
import com.gemwallet.android.features.assets.viewmodels.select.RecentsViewModel
import com.gemwallet.android.features.perpetuals.presents.components.PerpetualListItem
import com.gemwallet.android.features.wallet.viewmodels.WalletSearchViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.SearchBar
import com.gemwallet.android.ui.components.list_item.AssetContextActions
import com.gemwallet.android.ui.components.list_item.ListItem
import com.gemwallet.android.ui.components.list_item.ListItemDefaults
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.components.list_item.ListItemModel
import com.gemwallet.android.ui.components.list_item.NftListItem
import com.gemwallet.android.ui.components.list_item.SubheaderItem
import com.gemwallet.android.ui.components.list_item.listItem
import com.gemwallet.android.ui.components.list_item.property.DataBadgeChevron
import com.gemwallet.android.ui.components.list_item.property.itemsPositioned
import com.gemwallet.android.ui.components.screen.ToastEffect
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.models.NftItemTarget
import com.gemwallet.android.ui.models.target
import com.gemwallet.android.ui.theme.space0
import com.wallet.core.primitives.PerpetualId
import uniffi.gemstone.GemSearchListRow

@Composable
fun WalletSearchScreen(onAction: (WalletSearchAction) -> Unit, viewModel: WalletSearchViewModel = hiltViewModel(), recentsViewModel: RecentsViewModel = hiltViewModel()) {
    val searchEmptyState by viewModel.searchEmptyState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiStateSnapshot.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()

    val state = uiState.state
    val pinned = uiState.pinnedAssets
    val previewAssets = uiState.previewAssets
    val hasMoreAssets = uiState.hasMoreAssets
    val previewPerpetuals = uiState.previewPerpetuals
    val hasMorePerpetuals = uiState.hasMorePerpetuals
    val pinnedPerpetuals = uiState.pinnedPerpetuals
    val previewNfts = uiState.previewNfts
    val hasMoreNfts = uiState.hasMoreNfts
    val lists = uiState.lists

    val longPressedPerpetual = remember { mutableStateOf<PerpetualId?>(null) }
    val snackbar = remember { SnackbarHostState() }
    ToastEffect(viewModel.toastEvents, snackbar)

    val handleAction = remember<(WalletSearchAction) -> Unit>(viewModel, recentsViewModel, onAction) {
        { action ->
            when (action) {
                is WalletSearchAction.PinAsset -> viewModel.onTogglePin(action.assetId)

                is WalletSearchAction.AddToWallet -> viewModel.onAddToWallet(action.assetId)

                is WalletSearchAction.TogglePerpetualPin -> viewModel.onTogglePerpetualPin(action.item)

                WalletSearchAction.OpenRecentsSheet -> recentsViewModel.show(filters = viewModel.assetFilters(), types = viewModel.recentTypes)

                is WalletSearchAction.OpenAsset -> {
                    viewModel.openRecent(action.asset)
                    onAction(action)
                }

                is WalletSearchAction.OpenPerpetual -> {
                    viewModel.openRecent(action.asset)
                    onAction(action)
                }

                WalletSearchAction.AddAsset,
                WalletSearchAction.Cancel,
                WalletSearchAction.OpenPerpetuals,
                WalletSearchAction.OpenCollections,
                is WalletSearchAction.OpenNftCollection,
                is WalletSearchAction.OpenNftAsset,
                is WalletSearchAction.OpenList,
                is WalletSearchAction.OpenRecent,
                is WalletSearchAction.ShowAllAssets,
                -> onAction(action)
            }
        }
    }

    val contextActions = remember(handleAction) {
        AssetContextActions(
            onTogglePin = { handleAction(WalletSearchAction.PinAsset(it)) },
            onAddToWallet = { handleAction(WalletSearchAction.AddToWallet(it)) },
        )
    }

    val visiblePinnedPerpetuals = if (state.showsPinned) pinnedPerpetuals else emptyList()
    val pinnedPerpetualContent: (LazyListScope.(Int) -> Unit)? = if (visiblePinnedPerpetuals.isNotEmpty()) {
        { totalCount ->
            itemsPositioned(
                items = visiblePinnedPerpetuals,
                totalCount = totalCount,
                key = { _, item -> "pinned_perpetual-${item.data.perpetual.id}" },
                contentType = { _, _ -> "perpetual_row" },
            ) { position, item ->
                PerpetualListItem(
                    item = item,
                    listPosition = position,
                    longPressState = longPressedPerpetual,
                    onTogglePin = { handleAction(WalletSearchAction.TogglePerpetualPin(item)) },
                    onClick = { handleAction(WalletSearchAction.OpenPerpetual(item.data.asset.toPrimitives())) },
                )
            }
        }
    } else {
        null
    }

    val perpetualsContent: (LazyListScope.() -> Unit)? = if (state.showsPerpetuals) {
        {
            item(key = "search_perpetuals_header", contentType = "subheader") {
                SubheaderItem(R.string.perpetuals_title, if (hasMorePerpetuals) ({ handleAction(WalletSearchAction.OpenPerpetuals) }) else null)
            }
            itemsPositioned(
                items = previewPerpetuals,
                key = { _, item -> "perpetual-${item.data.perpetual.id}" },
                contentType = { _, _ -> "perpetual_row" },
            ) { position, item ->
                PerpetualListItem(
                    item = item,
                    listPosition = position,
                    longPressState = longPressedPerpetual,
                    onTogglePin = { handleAction(WalletSearchAction.TogglePerpetualPin(item)) },
                    onClick = { handleAction(WalletSearchAction.OpenPerpetual(item.data.asset.toPrimitives())) },
                )
            }
        }
    } else {
        null
    }

    val listsContent: (LazyListScope.() -> Unit)? = if (state.showsLists) {
        {
            item(key = "search_lists_header", contentType = "subheader") {
                SubheaderItem(R.string.common_lists)
            }
            itemsPositioned(
                items = lists,
                key = { _, item -> "search_list-${item.list.id}" },
                contentType = { _, _ -> "search_list_row" },
            ) { position, item ->
                SearchListItem(
                    row = item,
                    listPosition = position,
                    onClick = { handleAction(WalletSearchAction.OpenList(item.list.id, item.list.name)) },
                )
            }
        }
    } else {
        null
    }

    val nftsContent: (LazyListScope.() -> Unit)? = if (state.showsNfts) {
        {
            item(key = "search_nfts_header", contentType = "subheader") {
                SubheaderItem(R.string.nft_collections, if (hasMoreNfts) ({ handleAction(WalletSearchAction.OpenCollections) }) else null)
            }
            itemsPositioned(
                items = previewNfts,
                key = { _, item ->
                    when (val target = item.target) {
                        is NftItemTarget.Collection -> "nft_collection-${target.id}"
                        is NftItemTarget.Asset -> "nft_asset-${target.id}"
                    }
                },
                contentType = { _, _ -> "nft_row" },
            ) { position, item ->
                NftListItem(
                    row = item.row,
                    listPosition = position,
                    onClick = {
                        when (val target = item.target) {
                            is NftItemTarget.Collection -> handleAction(WalletSearchAction.OpenNftCollection(target.id))
                            is NftItemTarget.Asset -> handleAction(WalletSearchAction.OpenNftAsset(target.id))
                        }
                    },
                )
            }
        }
    } else {
        null
    }

    SelectAssetScene(
        title = {
            SearchBar(
                query = viewModel.queryState,
                modifier = Modifier.listItem(ListPosition.Single, paddingHorizontal = space0),
                autoFocus = true,
            )
        },
        query = viewModel.queryState,
        pinned = if (state.showsPinned) pinned else emptyList(),
        popular = emptyList(),
        unpinned = if (state.showsAssets) previewAssets else emptyList(),
        recent = if (state.showsRecents) recent else emptyList(),
        state = state.phase,
        rowAction = viewModel.flow.rowAction,
        empty = searchEmptyState,
        searchable = false,
        onAction = { action ->
            when (action) {
                SelectAssetAction.Cancel -> handleAction(WalletSearchAction.Cancel)

                SelectAssetAction.AddAsset -> handleAction(WalletSearchAction.AddAsset)

                SelectAssetAction.OpenRecentsSheet -> handleAction(WalletSearchAction.OpenRecentsSheet)

                SelectAssetAction.ShowAllAssets -> handleAction(
                    WalletSearchAction.ShowAllAssets(viewModel.queryState.text.toString()),
                )

                is SelectAssetAction.Select -> handleAction(WalletSearchAction.OpenAsset(action.asset))

                is SelectAssetAction.SelectRecent -> handleAction(WalletSearchAction.OpenRecent(action.asset))

                is SelectAssetAction.ChainFilter,
                is SelectAssetAction.BalanceFilter,
                SelectAssetAction.ClearFilters,
                -> Unit
            }
        },
        recentsSheetEnabled = true,
        contextActions = contextActions,
        pinnedPerpetualCount = visiblePinnedPerpetuals.size,
        pinnedPerpetualContent = pinnedPerpetualContent,
        perpetualsContent = perpetualsContent,
        listsContent = listsContent,
        nftsContent = nftsContent,
        assetsHeaderRes = R.string.assets_title,
        assetsHeaderClickable = hasMoreAssets,
        snackbar = snackbar,
    )

    RecentsScreen(viewModel = recentsViewModel, onSelect = { handleAction(WalletSearchAction.OpenRecent(it)) })
}

@Composable
private fun SearchListItem(row: GemSearchListRow, listPosition: ListPosition, onClick: () -> Unit) {
    ListItem(
        model = ListItemModel(title = row.list.name, subtitle = row.subtitle, image = ListItemImage.Url(row.imageUrl, placeholder = row.list.name)),
        listPosition = listPosition,
        modifier = Modifier.clickable(onClick = onClick).testTag(row.list.id),
        minHeight = ListItemDefaults.plainMinHeight,
        accessory = { DataBadgeChevron() },
    )
}
