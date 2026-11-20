package com.gemwallet.android.features.onboarding.viewmodels.import_wallet

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemwallet.android.application.IoDispatcher
import com.gemwallet.android.application.device.cases.EnablePushForNewWallet
import com.gemwallet.android.ext.errorText
import com.gemwallet.android.ext.networkName
import com.gemwallet.android.ext.toChain
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.localization.text
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.gemstone.GemQrPairingPreview
import uniffi.gemstone.GemQrPairingSession
import uniffi.gemstone.GemWalletImportResult
import uniffi.gemstone.GemWalletServiceInterface
import uniffi.gemstone.WalletType
import javax.inject.Inject

@HiltViewModel
class ImportQrWalletViewModel @Inject constructor(
    private val service: GemWalletServiceInterface,
    private val enablePushForNewWallet: EnablePushForNewWallet,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val pairingSession: GemQrPairingSession = service.qrPairingSession()
    private val state = MutableStateFlow(ImportQrWalletUIState())
    private val isImporting = MutableStateFlow(false)
    private val _isImported = MutableStateFlow(false)
    private var hasEditedWalletName = false

    val isImported: StateFlow<Boolean> = _isImported.asStateFlow()

    val uiState: StateFlow<ImportQrWalletUIState> = combine(state, isImporting) { current, importing ->
        current.copy(loading = importing)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ImportQrWalletUIState())

    fun setWalletName(name: String) {
        hasEditedWalletName = true
        state.update { it.copy(walletName = name) }
    }

    fun startScanning() {
        state.update {
            it.copy(
                isScanning = true,
                receivedParts = 0,
                expectedParts = 0,
                scannerError = null,
            )
        }
    }

    fun stopScanning() {
        state.update {
            it.copy(
                isScanning = false,
                receivedParts = 0,
                expectedParts = 0,
                scannerError = null,
            )
        }
    }

    fun onFrameScanned(payload: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val preview = pairingSession.receiveFrame(payload)
                if (preview.isComplete) {
                    val defaultChain = preview.chains.singleOrNull()?.takeIf { preview.walletType == WalletType.SINGLE }
                    val suggestedName = if (!hasEditedWalletName) {
                        service.defaultWalletName(defaultChain).string(context)
                    } else {
                        null
                    }
                    val networkNames = preview.chains.map { it.toChain().networkName() }
                    state.update { current ->
                        current.copy(
                            completedPreview = preview,
                            receivedParts = 0,
                            expectedParts = 0,
                            networkNames = networkNames,
                            walletName = suggestedName ?: current.walletName,
                            isScanning = false,
                            scannerError = null,
                        )
                    }
                } else {
                    state.update { current ->
                        current.copy(
                            receivedParts = preview.receivedParts.toInt(),
                            expectedParts = preview.expectedParts.toInt(),
                            scannerError = null,
                        )
                    }
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                state.update { it.copy(scannerError = err.errorText().text(context)) }
            }
        }
    }

    fun import() {
        if (isImporting.value) {
            return
        }
        val preview = state.value.completedPreview ?: return
        isImporting.value = true

        viewModelScope.launch(ioDispatcher) {
            try {
                val defaultChain = preview.chains.singleOrNull()?.takeIf { preview.walletType == WalletType.SINGLE }
                val resolvedName = state.value.walletName.trim().ifEmpty {
                    service.defaultWalletName(defaultChain).string(context)
                }
                val imported = service.importQrWallet(pairingSession, resolvedName)
                isImporting.value = false
                when (imported) {
                    is GemWalletImportResult.New -> {
                        enablePushForNewWallet.enablePushForNewWallet()
                        _isImported.value = true
                    }
                    is GemWalletImportResult.Existing -> {
                        state.update { it.copy(existingWalletName = imported.wallet.name) }
                    }
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                isImporting.value = false
                state.update { it.copy(errorAlert = err.errorText().text(context)) }
            }
        }
    }

    fun onImportedHandled() {
        _isImported.value = false
    }

    fun dismissError() {
        state.update { it.copy(errorAlert = null) }
    }

    fun dismissExistingWallet() {
        state.update { it.copy(existingWalletName = null) }
    }

    fun continueExistingWallet(onImported: () -> Unit) {
        dismissExistingWallet()
        enablePushForNewWallet.enablePushForNewWallet()
        onImported()
    }
}

@Immutable
data class ImportQrWalletUIState(
    val loading: Boolean = false,
    val isScanning: Boolean = false,
    val walletName: String = "",
    val completedPreview: GemQrPairingPreview? = null,
    val receivedParts: Int = 0,
    val expectedParts: Int = 0,
    val networkNames: List<String> = emptyList(),
    val scannerError: String? = null,
    val errorAlert: String? = null,
    val existingWalletName: String? = null,
)
