package com.gemwallet.android.features.onboarding.presents.import_wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.onboarding.presents.components.LoadingDialog
import com.gemwallet.android.features.onboarding.viewmodels.import_wallet.ImportQrWalletUIState
import com.gemwallet.android.features.onboarding.viewmodels.import_wallet.ImportQrWalletViewModel
import com.gemwallet.android.features.qr_scanner.presents.QRScannerModal
import com.gemwallet.android.features.qr_scanner.presents.QRScannerScreen
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.GemTextField
import com.gemwallet.android.ui.components.InfoBottomSheet
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.buttons.mainActionButtonColors
import com.gemwallet.android.ui.components.dialog.ErrorDialog
import com.gemwallet.android.ui.components.infoSheet
import com.gemwallet.android.ui.components.list_item.ListItem
import com.gemwallet.android.ui.components.list_item.ListItemModel
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.models.buttonState
import com.gemwallet.android.ui.theme.Spacer16
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.wallet.core.primitives.QRScanType
import uniffi.gemstone.GemInfoTopic

@Composable
fun ImportQrWalletScreen(onImported: () -> Unit, onCancel: () -> Unit) {
    val viewModel: ImportQrWalletViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImported by viewModel.isImported.collectAsStateWithLifecycle()

    LaunchedEffect(isImported) {
        if (isImported) {
            viewModel.onImportedHandled()
            onImported()
        }
    }

    val progressText = if (uiState.expectedParts > 1) {
        stringResource(R.string.qr_sign_progress, uiState.receivedParts, uiState.expectedParts)
    } else {
        null
    }

    if (uiState.completedPreview == null) {
        QRScannerScreen(
            scanType = QRScanType.Address,
            onCancel = onCancel,
            continuous = true,
            hintText = stringResource(R.string.wallet_import_qr_description),
            progressText = progressText,
            errorMessage = uiState.scannerError,
            onResult = viewModel::onFrameScanned,
        )
    } else {
        ImportQrWalletScene(
            uiState = uiState,
            onNameChange = viewModel::setWalletName,
            onStartScan = viewModel::startScanning,
            onImport = viewModel::import,
            onCancel = onCancel,
        )

        QRScannerModal(
            isVisible = uiState.isScanning,
            scanType = QRScanType.Address,
            onDismissRequest = viewModel::stopScanning,
            continuous = true,
            hintText = stringResource(R.string.wallet_import_qr_description),
            progressText = progressText,
            errorMessage = uiState.scannerError,
            onResult = viewModel::onFrameScanned,
        )
    }

    if (uiState.loading) {
        LoadingDialog()
    }

    ErrorDialog(
        message = uiState.errorAlert,
        onDismiss = viewModel::dismissError,
        title = stringResource(R.string.errors_validation, ""),
    )

    uiState.existingWalletName?.let { walletName ->
        InfoBottomSheet(
            item = GemInfoTopic.ExistingWalletImported(walletName).infoSheet {
                viewModel.continueExistingWallet(onImported)
            },
            onClose = viewModel::dismissExistingWallet,
        )
    }
}

@Composable
private fun ImportQrWalletScene(
    uiState: ImportQrWalletUIState,
    onNameChange: (String) -> Unit,
    onStartScan: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    val preview = uiState.completedPreview ?: return
    val deviceName = preview.device
    val rows = buildList {
        if (!deviceName.isNullOrEmpty()) {
            add(ListItemModel(title = stringResource(id = R.string.common_type), subtitle = deviceName))
        }
        add(
            ListItemModel(
                title = stringResource(id = R.string.settings_networks_title),
                subtitle = uiState.networkNames.joinToString(", "),
            ),
        )
    }
    Scene(
        title = stringResource(id = R.string.wallet_import_qr_wallet),
        onClose = onCancel,
        mainAction = {
            Column(verticalArrangement = Arrangement.spacedBy(paddingSmall)) {
                MainActionButton(
                    title = stringResource(id = R.string.wallet_scan_another_qr),
                    colors = mainActionButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    onClick = onStartScan,
                )
                MainActionButton(
                    title = stringResource(id = R.string.wallet_import_action),
                    state = buttonState(loading = uiState.loading),
                    onClick = onImport,
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingDefault),
        ) {
            item {
                GemTextField(
                    modifier = Modifier.fillMaxWidth().testTag("qrWalletNameField"),
                    label = stringResource(id = R.string.wallet_name),
                    value = uiState.walletName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    listPosition = ListPosition.Single,
                )
            }
            item { Spacer16() }
            items(rows.size) { index ->
                ListItem(
                    model = rows[index],
                    listPosition = ListPosition.getPosition(index, rows.size),
                )
            }
            item { Spacer(modifier = Modifier.size(paddingValues.calculateBottomPadding())) }
        }
    }
}
