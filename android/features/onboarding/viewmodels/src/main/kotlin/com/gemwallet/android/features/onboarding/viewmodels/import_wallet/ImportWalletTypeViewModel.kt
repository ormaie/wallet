package com.gemwallet.android.features.onboarding.viewmodels.import_wallet

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uniffi.gemstone.GemChainServiceInterface
import uniffi.gemstone.GemImportWalletTypes
import javax.inject.Inject

private const val CHAIN_FILTER_DEBOUNCE_MILLIS = 60L

@OptIn(FlowPreview::class)
@HiltViewModel
class ImportWalletTypeViewModel @Inject constructor(
    private val chainService: GemChainServiceInterface,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val chainFilter = TextFieldState()
    val types: StateFlow<GemImportWalletTypes?> = snapshotFlow { chainFilter.text.toString() }
        .debounce { query -> if (query.isEmpty()) 0L else CHAIN_FILTER_DEBOUNCE_MILLIS }
        .distinctUntilChanged()
        .map { query -> chainService.importWalletTypes(query) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
