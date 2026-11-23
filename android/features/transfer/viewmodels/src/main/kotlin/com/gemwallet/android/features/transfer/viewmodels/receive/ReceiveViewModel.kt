package com.gemwallet.android.features.transfer.viewmodels.receive

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.IoDispatcher
import com.gemwallet.android.application.assets.cases.GetWalletAssets
import com.gemwallet.android.application.session.cases.GetSession
import com.gemwallet.android.data.services.store.queries.AssetQueryOptional
import com.gemwallet.android.domains.asset.chain
import com.gemwallet.android.ext.getAccount
import com.gemwallet.android.ext.runCatchingCancellable
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.ui.localization.text
import com.wallet.core.primitives.Asset
import com.wallet.core.primitives.AssetData
import com.wallet.core.primitives.AssetId
import com.wallet.core.primitives.Wallet
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uniffi.gemstone.GemCopy
import uniffi.gemstone.GemReceiveAssetState
import uniffi.gemstone.GemReceiveNetwork
import uniffi.gemstone.GemReceiveNetworks
import uniffi.gemstone.GemReceiveServiceInterface
import uniffi.gemstone.addressCopy
import uniffi.gemstone.chainRow
import uniffi.gemstone.newReceiveSession

data class ReceiveUiState(
    val assetInfo: AssetData,
    val networks: GemReceiveNetworks,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReceiveViewModel.Factory::class)
class ReceiveViewModel @AssistedInject constructor(
    @Assisted private val sourceAssetId: AssetId,
    private val assetQuery: AssetQueryOptional,
    private val getWalletAssets: GetWalletAssets,
    private val service: GemReceiveServiceInterface,
    getSession: GetSession,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val selectedAssetId = MutableStateFlow(sourceAssetId)
    private val session = getSession()

    private val initialState = storedAsset(sourceAssetId)?.let { source ->
        val wallet = session.value?.wallet
        if (wallet != null) {
            ReceiveUiState(
                assetInfo = resolveAsset(source, wallet),
                networks = computeNetworks(source, wallet),
            )
        } else {
            ReceiveUiState(
                assetInfo = source,
                networks = fallbackNetworks(),
            )
        }
    }

    val uiState: StateFlow<ReceiveUiState?> = session
        .filterNotNull()
        .flatMapLatest { currentSession ->
            val wallet = currentSession.wallet
            val sourceFlow = assetQuery(wallet.id.id, sourceAssetId)
                .filterNotNull()
                .map { source ->
                    resolveAsset(source, wallet) to computeNetworks(source, wallet)
                }
            selectedAssetId.flatMapLatest { assetId ->
                if (assetId == sourceAssetId) {
                    sourceFlow.map { (assetInfo, networks) ->
                        ReceiveUiState(assetInfo = assetInfo, networks = networks)
                    }
                } else {
                    combine(
                        sourceFlow,
                        assetQuery(wallet.id.id, assetId).filterNotNull().map { resolveAsset(it, wallet) },
                    ) { (_, networks), selected ->
                        ReceiveUiState(assetInfo = selected, networks = networks)
                    }
                }
            }
        }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialState)

    init {
        viewModelScope.launch(ioDispatcher) {
            runCatchingCancellable { service.updateAsset(sourceAssetId.toIdentifier()) }
                .onFailure { Log.d(TAG, "asset update failed (${it.javaClass.simpleName})") }
        }
    }

    fun assetState(asset: Asset): GemReceiveAssetState = service.assetState(asset.toGem())

    fun warnings(state: GemReceiveAssetState): List<String> = state.warnings.map { it.text(context) }

    fun selectAsset(assetId: AssetId) {
        selectedAssetId.value = assetId
    }

    fun shareAddress(assetInfo: AssetData? = uiState.value?.assetInfo): String? = assetInfo?.account?.address?.takeIf { it.isNotEmpty() }

    fun copyAddress(assetInfo: AssetData? = uiState.value?.assetInfo): GemCopy? = assetInfo?.let { info ->
        info.account.address.takeIf { it.isNotEmpty() }?.let { addressCopy(info.asset.id.chain.string, it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(assetId: AssetId): ReceiveViewModel
    }

    fun setVisible() = viewModelScope.launch(ioDispatcher) {
        val assetId = uiState.value?.assetInfo?.asset?.id ?: return@launch
        val wallet = session.filterNotNull().first().wallet
        runCatchingCancellable { service.enableAsset(wallet.id.id, assetId.toIdentifier()) }
            .onFailure { Log.e(TAG, "enabling ${assetId.toIdentifier()} failed", it) }
    }

    private fun storedAsset(assetId: AssetId) = getWalletAssets().value.firstOrNull { it.asset.id == assetId }

    private fun resolveAsset(info: AssetData, wallet: Wallet): AssetData {
        val account = info.account.takeIf { it.address.isEmpty() }?.let { wallet.getAccount(info.asset.chain) }
        return if (account == null) info else info.copy(account = account)
    }

    private fun computeNetworks(source: AssetData, wallet: Wallet): GemReceiveNetworks = newReceiveSession(source.asset.toGem())
        .onAssociations(source.associations.map { it.assetId.toIdentifier() })
        .networks(wallet.toGem())

    private fun fallbackNetworks(): GemReceiveNetworks = GemReceiveNetworks(
        networks = listOf(GemReceiveNetwork(sourceAssetId.toIdentifier(), row = chainRow(sourceAssetId.chain.string))),
        showsSelector = false,
    )
}

private const val TAG = "Receive"
