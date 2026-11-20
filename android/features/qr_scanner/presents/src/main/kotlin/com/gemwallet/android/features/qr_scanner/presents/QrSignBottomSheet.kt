package com.gemwallet.android.features.qr_scanner.presents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.qrcode.AnimatedQRCode
import com.gemwallet.android.ui.components.screen.ModalBottomSheet
import com.gemwallet.android.ui.components.screen.SheetExpansion
import com.gemwallet.android.ui.models.QrSignSheetState
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingLarge
import com.gemwallet.android.ui.theme.space12
import com.wallet.core.primitives.QRScanType

@Composable
fun QrSignBottomSheet(
    state: QrSignSheetState?,
    onScanFrame: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isScanningSignature by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state?.stepIndex, state == null) {
        isScanningSignature = false
    }

    ModalBottomSheet(
        item = state,
        onDismissRequest = onDismiss,
        expansion = SheetExpansion.Full,
        title = { stringResource(R.string.qr_sign_title) },
    ) { item ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingDefault),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space12),
        ) {
            if (item.totalSteps > 1) {
                Text(
                    text = stringResource(R.string.qr_sign_step, item.stepIndex, item.totalSteps),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            AnimatedQRCode(frames = item.urFrames)
            Text(
                text = stringResource(R.string.qr_sign_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = paddingLarge),
            )
            Spacer(modifier = Modifier.weight(1f))
            MainActionButton(
                title = stringResource(R.string.qr_sign_scan_signature),
                onClick = { isScanningSignature = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val receivedParts = state?.receivedParts
    val expectedParts = state?.expectedParts
    val progressText = if (receivedParts != null && expectedParts != null && expectedParts > 1) {
        stringResource(R.string.qr_sign_progress, receivedParts, expectedParts)
    } else {
        null
    }

    QRScannerModal(
        isVisible = isScanningSignature && state != null,
        scanType = QRScanType.Address,
        onDismissRequest = { isScanningSignature = false },
        continuous = true,
        hintText = stringResource(R.string.qr_sign_scan_instruction),
        progressText = progressText,
        errorMessage = state?.errorMessage,
        onResult = onScanFrame,
    )
}
