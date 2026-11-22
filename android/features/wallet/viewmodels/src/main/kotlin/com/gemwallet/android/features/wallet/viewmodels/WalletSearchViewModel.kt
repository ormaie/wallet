package com.gemwallet.android.features.wallet.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.IoDispatcher
import com.gemwallet.android.application.session.cases.GetSession
import com.gemwallet.android.data.services.store.queries.AssetsQuery
import com.gemwallet.android.data.services.store.queries.NFTQuery
import com.gemwallet.android.data.services.store.queries.RecentActivityQuery
import com.gemwallet.android.data.services.store.queries.WalletSearchQuery
import com.gemwallet.android.domains.asset.aggregates.AssetInfoDataAggregate
import com.gemwallet.android.domains.search.WalletSearchTag
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.features.assets.viewmodels.select.BaseSelectAssetViewModel
import com.gemwallet.android.features.assets.viewmodels.select.models.BaseSelectSearch
import com.wallet.core.primitives.AssetList
import com.wallet.core.primitives.NFTData
import com.wallet.core.primitives.PerpetualData
import com.wallet.core.primitives.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import uniffi.gemstone.GemAssetSelectionServiceInterface
import uniffi.gemstone.GemNftEntry
import uniffi.gemstone.GemPerpetualMarketItem
import uniffi.gemstone.GemSearchListRow
import uniffi.gemstone.GemSearchScope
import uniffi.gemstone.GemSelectAssetState
import uniffi.gemstone.GemSelectAssetType
import uniffi.gemstone.GemWalletSearchInput
import uniffi.gemstone.GemWalletSearchState
import javax.inject.Inject

@Immutable
data class WalletSearchUiState(
    val state: GemWalletSearchState = emptySearchState(GemSelectAssetState.IDLE),
    val pinnedAssets: List<AssetInfoDataAggregate> = emptyList(),
    val previewAssets: List<AssetInfoDataAggregate> = emptyList(),
    val hasMoreAssets: Boolean = false,
    val pinnedPerpetuals: List<GemPerpetualMarketItem> = emptyList(),
    val previewPerpetuals: List<GemPerpetualMarketItem> = emptyList(),
    val hasMorePerpetuals: Boolean = false,
    val lists: List<GemSearchListRow> = emptyList(),
    val previewNfts: List<GemNftEntry> = emptyList(),
    val hasMoreNfts: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalletSearchViewModel @Inject constructor(
    getSession: GetSession,
    assetsQuery: AssetsQuery,
    walletSearchQuery: WalletSearchQuery,
    recentActivityQuery: RecentActivityQuery,
    service: GemAssetSelectionServiceInterface,
    nftQuery: NFTQuery,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    @ApplicationContext context: Context,
) : BaseSelectAssetViewModel(
    getSession,
    recentActivityQuery,
    service,
    BaseSelectSearch(assetsQuery),
    GemSelectAssetType.WalletSearch,
    ioDispatcher,
    context,
) {

    override suspend fun searchRemote(query: String) {
        service.search(query, GemSearchScope.All)
    }

    private val perpetuals: Flow<List<PerpetualData>> = localQuery
        .flatMapLatest { query -> walletSearchQuery.perpetuals(GemSearchScope.All.searchKey(query), WalletSearchTag.All, assetsSearchLimit(query)) }
        .flowOn(ioDispatcher)

    private val collections: Flow<List<NFTData>> = getSession()
        .filterNotNull()
        .distinctUntilChangedBy { it.wallet.id }
        .flatMapLatest { nftQuery(it.wallet.id.id) }
        .flowOn(ioDispatcher)

    private val searchLists: Flow<List<AssetList>> = localQuery
        .flatMapLatest { query -> walletSearchQuery.lists(query) }
        .flowOn(ioDispatcher)

    private val rows: Flow<WalletSearchRows> = combine(sections, perpetuals, searchLists, collections) { sections, perpetuals, lists, collections ->
        WalletSearchRows(sections.pinned, sections.unpinned, perpetuals, lists, collections)
    }
        .flowOn(ioDispatcher)

    val uiStateSnapshot: StateFlow<WalletSearchUiState> = combine(getSession(), localQuery, searching, recent, rows) { session, query, searching, recents, rows ->
        session?.wallet?.let { wallet ->
            val content = SearchContent(service.walletSearchView(rows.input(wallet, query, searching, recents.size)), rows.assets)
            val view = content.view
            WalletSearchUiState(
                state = view.state,
                pinnedAssets = content.assets(view.pinnedAssetIds),
                previewAssets = content.assets(view.assetIds),
                hasMoreAssets = view.hasMoreAssets,
                pinnedPerpetuals = view.pinnedPerpetuals,
                previewPerpetuals = view.perpetuals,
                hasMorePerpetuals = view.hasMorePerpetuals,
                lists = view.lists,
                previewNfts = view.nfts,
                hasMoreNfts = view.hasMoreNfts,
            )
        } ?: WalletSearchUiState()
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WalletSearchUiState())

    override fun assetsSearchLimit(query: String): Int = service.walletSearchLimits(query).fetch
}

private class WalletSearchRows(val pinned: List<AssetInfoDataAggregate>, unpinned: List<AssetInfoDataAggregate>, val perpetuals: List<PerpetualData>, val lists: List<AssetList>, val collections: List<NFTData>) {
    val assets: List<AssetInfoDataAggregate> = pinned + unpinned

    fun input(wallet: Wallet, query: String, isLoading: Boolean, recents: Int) = GemWalletSearchInput(
        wallet = wallet.toGem(),
        query = query,
        isLoading = isLoading,
        recents = recents,
        assetIds = assets.map { it.asset.id.toIdentifier() },
        pinnedAssetIds = pinned.map { it.asset.id.toIdentifier() },
        perpetuals = perpetuals.map { it.toGem() },
        lists = lists.map { it.toGem() },
        collections = collections.map { it.toGem() },
    )
}
