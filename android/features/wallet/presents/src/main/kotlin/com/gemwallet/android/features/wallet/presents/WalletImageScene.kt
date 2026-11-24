package com.gemwallet.android.features.wallet.presents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.gemwallet.android.ui.components.AvatarPickerScene
import com.gemwallet.android.ui.components.empty.EmptyContentView
import com.gemwallet.android.ui.components.image.NftImage
import com.gemwallet.android.ui.components.image.StoredAvatarInfo
import com.gemwallet.android.ui.components.image.toImageSource
import com.gemwallet.android.ui.components.image.walletAvatarSeed
import com.gemwallet.android.ui.style.badgeImage
import com.gemwallet.android.ui.style.listItemImage
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import uniffi.gemstone.GemAvatarList
import uniffi.gemstone.GemListPhase
import uniffi.gemstone.GemNftEntry
import uniffi.gemstone.GemWalletDetails

private const val NFT_COLUMNS = 2

@Composable
internal fun WalletImageScene(
    wallet: GemWalletDetails?,
    avatars: GemAvatarList?,
    storedAvatarInfo: StoredAvatarInfo? = null,
    snackbar: SnackbarHostState? = null,
    onAction: (WalletImageAction) -> Unit,
) {
    wallet ?: return
    val addressSeed = remember(wallet.row.id) { walletAvatarSeed(wallet.row.id) }

    AvatarPickerScene(
        heroImage = wallet.row.listItemImage(),
        badgeImage = wallet.row.badgeImage(),
        addressSeed = addressSeed,
        storedAvatarInfo = storedAvatarInfo,
        snackbar = snackbar,
        onClose = { onAction(WalletImageAction.Close) },
        onRemove = if (wallet.row.hasAvatar) {
            { onAction(WalletImageAction.ResetToDefault) }
        } else {
            null
        },
        onSelectBoring = { onAction(WalletImageAction.SetBoring(it)) },
        onSelectPreset = { onAction(WalletImageAction.SetPreset(it)) },
        nftContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val phase = avatars?.phase) {
                    is GemListPhase.Empty -> EmptyContentView(
                        state = phase.state,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(paddingDefault),
                    )

                    GemListPhase.Rows -> NftGrid(
                        nftImages = avatars.items,
                        onNftImage = { onAction(WalletImageAction.SetNftImage(it)) },
                    )

                    is GemListPhase.Error, null -> Unit
                }
            }
        },
    )
}

@Composable
private fun NftGrid(nftImages: List<GemNftEntry>, onNftImage: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(NFT_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(paddingDefault),
    ) {
        items(nftImages) { item ->
            val source = item.row.toImageSource()
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(paddingSmall),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(paddingDefault))
                        .clickable { onNftImage(source.url) },
                ) {
                    NftImage(
                        source = source,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
