package com.gemwallet.android.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.gemwallet.android.ui.localization.stringRes
import com.wallet.core.primitives.ScanReceiveMode

@Composable
fun ScanReceiveSwitcher(mode: ScanReceiveMode, onModeChange: (ScanReceiveMode) -> Unit, progress: (() -> Float)? = null) {
    TabsBar(
        tabs = ScanReceiveMode.entries,
        selected = mode,
        onSelect = onModeChange,
        progress = progress,
    ) { entry ->
        Text(
            text = stringResource(id = entry.stringRes()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
