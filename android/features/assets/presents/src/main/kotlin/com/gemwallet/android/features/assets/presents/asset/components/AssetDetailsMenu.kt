package com.gemwallet.android.features.assets.presents.asset.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.GemDropdownMenu
import com.gemwallet.android.ui.components.GemDropdownMenuDivider
import com.gemwallet.android.ui.components.GemDropdownMenuItem
import com.gemwallet.android.ui.components.image.vector
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.text
import com.gemwallet.android.ui.open
import com.gemwallet.android.ui.shareText
import com.gemwallet.android.ui.style.symbol
import uniffi.gemstone.GemAssetDetails
import uniffi.gemstone.GemAssetOption

@Composable
fun RowScope.AssetDetailsMenu(details: GemAssetDetails, onPriceAlert: () -> Unit) {
    val context = LocalContext.current

    var menuExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringResource(id = R.string.common_share)

    val onShare = fun () {
        context.shareText(subject = null, text = details.shareUrl, chooserTitle = shareTitle)
    }

    details.state.priceAlert?.let { priceAlert ->
        IconButton(onClick = onPriceAlert, modifier = Modifier.testTag("price_alert_button")) {
            Icon(priceAlert.symbol().vector(), stringResource(R.string.settings_price_alerts_title))
        }
    }
    IconButton(onClick = { menuExpanded = !menuExpanded }) {
        Icon(
            imageVector = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.wallet_more),
        )
    }
    GemDropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
    ) {
        details.options.forEachIndexed { index, option ->
            if (index > 0) GemDropdownMenuDivider()
            GemDropdownMenuItem(
                text = option.text(context),
                trailingIcon = when (option) {
                    is GemAssetOption.ViewAddress,
                    is GemAssetOption.ViewToken,
                    -> AppIcons.OpenInNew

                    GemAssetOption.Share -> AppIcons.Share
                },
                onClick = {
                    menuExpanded = false
                    when (option) {
                        is GemAssetOption.ViewAddress -> uriHandler.open(context, option.link.link)
                        is GemAssetOption.ViewToken -> uriHandler.open(context, option.link.link)
                        GemAssetOption.Share -> onShare()
                    }
                },
            )
        }
    }
}
