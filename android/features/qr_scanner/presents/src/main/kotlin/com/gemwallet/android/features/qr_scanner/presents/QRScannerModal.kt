package com.gemwallet.android.features.qr_scanner.presents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.gemwallet.android.ui.components.screen.ModalBottomSheet
import com.gemwallet.android.ui.components.screen.SheetExpansion
import com.wallet.core.primitives.QRScanType

@Composable
fun QRScannerModal(
    isVisible: Boolean,
    scanType: QRScanType,
    onDismissRequest: () -> Unit,
    continuous: Boolean = false,
    hintText: String? = null,
    progressText: String? = null,
    errorMessage: String? = null,
    onResult: (String) -> Unit,
) {
    ModalBottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismissRequest,
        expansion = SheetExpansion.Full,
        shape = RectangleShape,
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            QRScannerScreen(
                scanType = scanType,
                onCancel = onDismissRequest,
                continuous = continuous,
                hintText = hintText,
                progressText = progressText,
                errorMessage = errorMessage,
                onResult = onResult,
            )
        }
    }
}
