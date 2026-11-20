package com.gemwallet.android.features.transfer.viewmodels.recipient

import androidx.lifecycle.SavedStateHandle
import com.gemwallet.android.application.session.cases.GetCurrentWalletId
import com.gemwallet.android.data.services.store.queries.AssetQuery
import com.gemwallet.android.data.services.store.queries.ContactsQuery
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.features.transfer.viewmodels.recipient.models.RecipientUIState
import com.gemwallet.android.testkit.MainDispatcherRule
import com.gemwallet.android.testkit.NameServiceMock
import com.gemwallet.android.testkit.mockAsset
import com.gemwallet.android.testkit.mockAssetData
import com.gemwallet.android.testkit.mockAssetId
import com.gemwallet.android.testkit.mockContact
import com.gemwallet.android.testkit.mockContactAddress
import com.gemwallet.android.testkit.mockContactData
import com.gemwallet.android.testkit.mockWalletId
import com.gemwallet.android.ui.models.navigation.RouteArgument
import com.wallet.core.primitives.Chain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uniffi.gemstone.GemNameRecordState
import uniffi.gemstone.GemRecipient
import uniffi.gemstone.GemRecipientServiceInterface
import uniffi.gemstone.GemRecipientType
import uniffi.gemstone.InternalException

@OptIn(ExperimentalCoroutinesApi::class)
class RecipientViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val asset = mockAsset()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `an unexpected failure picking a recipient shows it in the address field`() = runTest(dispatcher) {
        val service = mockk<GemRecipientServiceInterface>(relaxed = true)
        every { service.select(any(), any()) } throws InternalException("panic")
        val viewModel = RecipientViewModel(
            walletAddressItemsQuery = mockk(relaxed = true),
            contactsQuery = mockk(relaxed = true),
            getCurrentWalletId = mockk<GetCurrentWalletId> { every { this@mockk() } returns emptyFlow() },
            assetQuery = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf(RouteArgument.AssetId.key to asset.id.toIdentifier())),
            service = service,
            nameService = mockk(relaxed = true),
            context = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )

        viewModel.onDestination(
            recipient = RecipientUIState.Ready(asset, GemRecipientType.Asset(asset.toGem())),
            destination = GemRecipient(address = "address"),
            amountAction = {},
            confirmAction = {},
        )

        assertEquals("panic", viewModel.addressError.value)
    }

    @Test
    fun `typing an address surfaces the matching contact and clears it when removed`() = runTest(dispatcher) {
        val walletId = mockWalletId()
        val ethereumAsset = mockAsset(id = mockAssetId(Chain.Ethereum))
        val contact = mockContactData(
            contact = mockContact(id = "1", name = "Plasma One"),
            addresses = listOf(mockContactAddress(id = "a", contactId = "1", address = "0x999EB", chain = Chain.Ethereum)),
        )
        val matched = GemRecipient(address = "0x999EB", name = "Plasma One")
        val service = mockk<GemRecipientServiceInterface>(relaxed = true)
        every { service.matchContact(Chain.Ethereum.string, any(), any(), any()) } returns null
        every { service.matchContact(Chain.Ethereum.string, "0x999EB", GemNameRecordState.None, listOf(contact.toGem())) } returns matched
        val viewModel = RecipientViewModel(
            walletAddressItemsQuery = mockk(relaxed = true),
            contactsQuery = mockk<ContactsQuery> { every { this@mockk() } returns flowOf(listOf(contact)) },
            getCurrentWalletId = mockk<GetCurrentWalletId> { every { this@mockk() } returns flowOf(walletId) },
            assetQuery = mockk<AssetQuery> { every { this@mockk(walletId.id, ethereumAsset.id) } returns flowOf(mockAssetData(asset = ethereumAsset)) },
            savedStateHandle = SavedStateHandle(mapOf(RouteArgument.AssetId.key to ethereumAsset.id.toIdentifier())),
            service = service,
            nameService = NameServiceMock(),
            context = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )

        assertNull(viewModel.matchedContact.value)

        viewModel.onAddress("0x999EB")
        assertEquals(matched, viewModel.matchedContact.value)

        viewModel.onAddress("")
        assertNull(viewModel.matchedContact.value)
    }
}
