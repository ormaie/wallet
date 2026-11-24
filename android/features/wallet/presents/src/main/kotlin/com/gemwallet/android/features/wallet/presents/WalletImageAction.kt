package com.gemwallet.android.features.wallet.presents

import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.BoringAvatar

internal sealed interface WalletImageAction {
    data class SetBoring(val avatar: BoringAvatar) : WalletImageAction
    data class SetPreset(val preset: AvatarPreset) : WalletImageAction
    data class SetNftImage(val url: String) : WalletImageAction
    data object ResetToDefault : WalletImageAction
    data object Close : WalletImageAction
}
