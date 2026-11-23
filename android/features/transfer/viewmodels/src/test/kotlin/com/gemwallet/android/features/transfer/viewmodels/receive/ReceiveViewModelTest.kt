package com.gemwallet.android.features.transfer.viewmodels.receive

import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.assets.cases.GetWalletAssets
import com.gemwallet.android.application.session.cases.GetSession
import com.gemwallet.android.data.services.store.queries.AssetQueryOptional
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.testkit.MainDispatcherRule
import com.gemwallet.android.testkit.mockAccount
import com.gemwallet.android.testkit.mockAsset
import com.gemwallet.android.testkit.mockAssetData
import com.gemwallet.android.testkit.mockAssetId
import com.gemwallet.android.testkit.mockSession
import com.gemwallet.android.testkit.mockWallet
import com.wallet.core.primitives.AssetAssociation
import com.wallet.core.primitives.AssetAssociationType
import com.wallet.core.primitives.AssetData
import com.wallet.core.primitives.Chain
import com.wallet.core.primitives.WalletId
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.gemstone.GemReceiveAssetState
import uniffi.gemstone.GemReceiveServiceInterface
import uniffi.gemstone.GemReceiveWarning
import uniffi.gemstone.assetText

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<androidx.lifecycle.ViewModel>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @After
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        models.clear()
    }

    private val bitcoin = mockAsset()
    private val ethereum = mockAsset(id = mockAssetId(chain = Chain.Ethereum), name = "Ethereum", symbol = "ETH", decimals = 18u)
    private val wallet = mockWallet(
        id = WalletId("multicoin_0xabc"),
        accounts = listOf(mockAccount(chain = Chain.Bitcoin, address = "bc1q"), mockAccount(chain = Chain.Ethereum, address = "0xabc")),
    )

    private fun receiveModel(
        service: GemReceiveServiceInterface,
        assets: Map<com.wallet.core.primitives.AssetId, AssetData> = mapOf(bitcoin.id to mockAssetData(asset = bitcoin)),
        cachedAssets: List<AssetData> = assets.values.toList(),
    ): ReceiveViewModel {
        val assetQuery: AssetQueryOptional = mockk {
            every { this@mockk.invoke(wallet.id.id, any()) } answers { flowOf(assets[secondArg()]) }
        }
        val walletAssets: GetWalletAssets = mockk {
            every { this@mockk.invoke() } returns MutableStateFlow(cachedAssets)
        }
        val session: GetSession = mockk { every { this@mockk.invoke() } returns MutableStateFlow(mockSession(wallet = wallet)) }
        return ReceiveViewModel(bitcoin.id, assetQuery, walletAssets, service, session, dispatcher, mockk(relaxed = true)).also { models.add(it) }
    }

    @Test
    fun `initial state derives networks and account synchronously from cached wallet asset`() = runTest(dispatcher) {
        val source = mockAssetData(asset = bitcoin, associations = listOf(AssetAssociation(ethereum.id, AssetAssociationType.Bridged)))
        val model = receiveModel(mockk(relaxed = true), assets = mapOf(bitcoin.id to source))

        val initial = requireNotNull(model.uiState.value)
        assertEquals("bc1q", initial.assetInfo.account.address)
        assertTrue(initial.networks.showsSelector)
        assertEquals(listOf(bitcoin.id.toIdentifier(), ethereum.id.toIdentifier()), initial.networks.networks.map { it.assetId })
    }

    @Test
    fun `uncached asset waits and delivers asset and networks together in one emission`() = runTest(dispatcher) {
        val source = mockAssetData(asset = bitcoin, associations = listOf(AssetAssociation(ethereum.id, AssetAssociationType.Bridged)))
        val model = receiveModel(mockk(relaxed = true), assets = mapOf(bitcoin.id to source), cachedAssets = emptyList())

        assertNull(model.uiState.value)
        val loaded = requireNotNull(model.uiState.first { it != null })
        assertEquals(bitcoin.id, loaded.assetInfo.asset.id)
        assertTrue(loaded.networks.showsSelector)
        assertEquals(listOf(bitcoin.id.toIdentifier(), ethereum.id.toIdentifier()), loaded.networks.networks.map { it.assetId })
    }

    @Test
    fun `the networks come from the stored associations and the warnings from Core`() = runTest(dispatcher) {
        val service: GemReceiveServiceInterface = mockk(relaxed = true) {
            every { assetState(any()) } answers { GemReceiveAssetState(assetText(firstArg()), listOf(GemReceiveWarning.NoMemoRequired)) }
        }
        val source = mockAssetData(asset = bitcoin, associations = listOf(AssetAssociation(ethereum.id, AssetAssociationType.Bridged)))
        val model = receiveModel(service, assets = mapOf(bitcoin.id to source))

        assertEquals(
            listOf(bitcoin.id.toIdentifier(), ethereum.id.toIdentifier()),
            requireNotNull(model.uiState.first { it?.networks?.showsSelector == true }).networks.networks.map { it.assetId },
        )
        assertEquals(listOf(GemReceiveWarning.NoMemoRequired), model.assetState(bitcoin).warnings)
        assertEquals(bitcoin.name, model.assetState(bitcoin).asset.asset.name)
    }

    @Test
    fun `picking another network keeps the networks of the asset the screen opened on`() = runTest(dispatcher) {
        val source = mockAssetData(asset = bitcoin, associations = listOf(AssetAssociation(ethereum.id, AssetAssociationType.Bridged)))
        val model = receiveModel(mockk(relaxed = true), assets = mapOf(bitcoin.id to source, ethereum.id to mockAssetData(asset = ethereum)))
        model.uiState.first { it?.networks?.showsSelector == true }

        model.selectAsset(ethereum.id)
        val switched = requireNotNull(model.uiState.first { it?.assetInfo?.asset?.id == ethereum.id })

        assertEquals(listOf(bitcoin.id.toIdentifier(), ethereum.id.toIdentifier()), switched.networks.networks.map { it.assetId })
    }

    @Test
    fun `picking another network swaps the asset the screen shows`() = runTest(dispatcher) {
        val service: GemReceiveServiceInterface = mockk(relaxed = true) {
        }
        val model = receiveModel(
            service,
            assets = mapOf(bitcoin.id to mockAssetData(asset = bitcoin), ethereum.id to mockAssetData(asset = ethereum)),
        )
        model.uiState.first { it?.assetInfo?.asset?.id == bitcoin.id }

        model.selectAsset(ethereum.id)

        assertEquals(ethereum.id, model.uiState.first { it?.assetInfo?.asset?.id == ethereum.id }?.assetInfo?.asset?.id)
    }

    @Test
    fun `an asset without a stored account receives on the session wallet account`() = runTest(dispatcher) {
        val service: GemReceiveServiceInterface = mockk(relaxed = true) {
        }
        val model = receiveModel(service, assets = mapOf(bitcoin.id to mockAssetData(asset = bitcoin)))

        assertEquals("bc1q", model.uiState.first { it?.assetInfo?.account?.address?.isNotEmpty() == true }?.assetInfo?.account?.address)
        assertEquals("bc1q", model.shareAddress())
    }

    @Test
    fun `copy and share use the rendered asset snapshot after another network is selected`() = runTest(dispatcher) {
        val bitcoinData = mockAssetData(asset = bitcoin, account = mockAccount(chain = Chain.Bitcoin, address = "bc1q"))
        val ethereumData = mockAssetData(asset = ethereum, account = mockAccount(chain = Chain.Ethereum, address = "0xabc"))
        val model = receiveModel(mockk(relaxed = true), assets = mapOf(bitcoin.id to bitcoinData, ethereum.id to ethereumData))
        val rendered = requireNotNull(model.uiState.first { it?.assetInfo?.asset?.id == bitcoin.id }).assetInfo

        model.selectAsset(ethereum.id)
        model.uiState.first { it?.assetInfo?.asset?.id == ethereum.id }

        assertEquals("bc1q", model.shareAddress(rendered))
        assertEquals("bc1q", model.copyAddress(rendered)?.value)
    }

    @Test
    fun `showing the screen enables the asset for the wallet`() = runTest(dispatcher) {
        val service: GemReceiveServiceInterface = mockk(relaxed = true) {
        }
        val model = receiveModel(service)
        model.uiState.first { it != null }

        model.setVisible().join()

        coVerify { service.enableAsset(wallet.id.id, bitcoin.id.toIdentifier()) }
    }

    @Test
    fun `opening the screen updates the asset`() = runTest(dispatcher) {
        val service: GemReceiveServiceInterface = mockk(relaxed = true) {
        }
        receiveModel(service)
        advanceUntilIdle()

        coVerify(exactly = 1) { service.updateAsset(bitcoin.id.toIdentifier()) }
    }
}
