package com.gemwallet.android.features.wallet.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.IoDispatcher
import com.gemwallet.android.data.services.store.queries.NFTQuery
import com.gemwallet.android.data.services.store.queries.WalletQuery
import com.gemwallet.android.ext.errorText
import com.gemwallet.android.ext.runCatchingCancellable
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.AvatarPresetRenderer
import com.gemwallet.android.ui.components.image.BoringAvatar
import com.gemwallet.android.ui.components.image.BoringAvatarRenderer
import com.gemwallet.android.ui.components.image.StoredAvatarInfo
import com.gemwallet.android.ui.components.image.inspectStoredAvatar
import com.gemwallet.android.ui.localization.text
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.gemstone.GemAvatarList
import uniffi.gemstone.GemWalletDetails
import uniffi.gemstone.GemWalletServiceInterface
import javax.inject.Inject

@Immutable
data class WalletAvatarDetails(
    val wallet: GemWalletDetails,
    val storedAvatar: StoredAvatarInfo? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalletImageViewModel @Inject constructor(
    walletQuery: WalletQuery,
    nftQuery: NFTQuery,
    private val walletService: GemWalletServiceInterface,
    savedStateHandle: SavedStateHandle,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val walletId = savedStateHandle.requireWalletId()

    val details: StateFlow<WalletAvatarDetails?> = walletQuery(walletId)
        .mapLatest { wallet ->
            wallet?.let {
                val gemDetails = walletService.walletDetails(it.toGem())
                val storedAvatar = withContext(ioDispatcher) {
                    inspectStoredAvatar(context, gemDetails.row.imageUrl)
                }
                WalletAvatarDetails(wallet = gemDetails, storedAvatar = storedAvatar)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val avatars: StateFlow<GemAvatarList?> = nftQuery(walletId.id)
        .map { data -> walletService.avatarList(data.map { it.toGem() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val errorState = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorState.asStateFlow()

    fun setBoring(avatar: BoringAvatar) = viewModelScope.launch(ioDispatcher) {
        runCatchingCancellable { walletService.setAvatarImage(walletId.id, BoringAvatarRenderer.render(context, avatar)) }
            .onFailure { errorState.value = it.errorText().text(context) }
    }

    fun setPreset(preset: AvatarPreset) = viewModelScope.launch(ioDispatcher) {
        runCatchingCancellable { walletService.setAvatarImage(walletId.id, AvatarPresetRenderer.render(context, preset)) }
            .onFailure { errorState.value = it.errorText().text(context) }
    }

    fun setNftImage(url: String) = viewModelScope.launch(ioDispatcher) {
        runCatchingCancellable { walletService.setAvatarImageUrl(walletId.id, url) }
            .onFailure { errorState.value = it.errorText().text(context) }
    }

    fun resetToDefault() = viewModelScope.launch(ioDispatcher) {
        runCatchingCancellable { walletService.removeAvatarImage(walletId.id) }
            .onFailure { errorState.value = it.errorText().text(context) }
    }

    fun clearError() = errorState.update { null }
}
