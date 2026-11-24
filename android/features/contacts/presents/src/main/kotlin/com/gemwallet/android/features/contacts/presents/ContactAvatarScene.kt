package com.gemwallet.android.features.contacts.presents

import androidx.compose.runtime.Composable
import com.gemwallet.android.ui.components.AvatarPickerScene
import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.BoringAvatar
import com.gemwallet.android.ui.components.image.StoredAvatarInfo
import com.gemwallet.android.ui.components.list_item.ListItemImage

@Composable
internal fun ContactAvatarScene(
    heroImage: ListItemImage,
    hasAvatar: Boolean,
    addressSeed: String?,
    storedAvatarInfo: StoredAvatarInfo?,
    onSelectBoring: (BoringAvatar) -> Unit,
    onSelectPreset: (AvatarPreset) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    AvatarPickerScene(
        heroImage = heroImage,
        addressSeed = addressSeed,
        storedAvatarInfo = storedAvatarInfo,
        onClose = onCancel,
        onRemove = if (hasAvatar) onRemove else null,
        onSelectBoring = onSelectBoring,
        onSelectPreset = onSelectPreset,
    )
}
