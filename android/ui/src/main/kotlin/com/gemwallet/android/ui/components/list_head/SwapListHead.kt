package com.gemwallet.android.ui.components.list_head

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.gemwallet.android.ext.toPrimitives
import com.gemwallet.android.model.text
import com.gemwallet.android.ui.components.image.AssetIcon
import com.gemwallet.android.ui.components.list_item.ListItemDefaults
import com.gemwallet.android.ui.components.list_item.listItem
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.theme.Spacer8
import com.gemwallet.android.ui.theme.compactIconSize
import com.gemwallet.android.ui.theme.listItemIconSize
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.wallet.core.primitives.AssetId
import uniffi.gemstone.GemHeaderAmount

@Composable
fun SwapListHead(from: GemHeaderAmount, to: GemHeaderAmount, onSwapClick: (() -> Unit)? = null, onAssetClick: ((AssetId) -> Unit)? = null) {
    Column {
        Column(
            modifier = Modifier
                .listItem(ListPosition.Single)
                .fillMaxWidth()
                .padding(horizontal = ListItemDefaults.contentSpacing, vertical = paddingDefault),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwapItem(
                amount = from,
                onSwapClick = onSwapClick,
                onAssetClick = onAssetClick,
            )
            Spacer8()
            Box(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(compactIconSize),
                    imageVector = AppIcons.ArrowDownward,
                    contentDescription = null,
                )
            }
            Spacer8()
            SwapItem(
                amount = to,
                onSwapClick = onSwapClick,
                onAssetClick = onAssetClick,
            )
        }
    }
}

@Composable
private fun SwapItem(amount: GemHeaderAmount, onSwapClick: (() -> Unit)?, onAssetClick: ((AssetId) -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onSwapClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(paddingSmall))
                            .clickable { onSwapClick() }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = amount.amount.text(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
            amount.fiat?.let { fiat ->
                Text(
                    text = fiat.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Box(
            modifier = if (onAssetClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = listItemIconSize / 2),
                ) {
                    onAssetClick(amount.asset.toPrimitives().id)
                }
            } else {
                Modifier
            },
        ) {
            AssetIcon(amount.icon, size = listItemIconSize)
        }
    }
}
