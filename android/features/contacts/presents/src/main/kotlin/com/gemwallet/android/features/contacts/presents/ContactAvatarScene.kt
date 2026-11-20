package com.gemwallet.android.features.contacts.presents

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.EmojiPickerGrid
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.theme.AvatarEmoji
import com.gemwallet.android.ui.theme.secondaryFaded
import uniffi.gemstone.GemJdenticon

@Composable
internal fun ContactAvatarScene(
    identicons: List<GemJdenticon>,
    onSelect: (String, Int) -> Unit,
    onSelectIdenticon: (GemJdenticon) -> Unit,
    onCancel: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.secondaryFaded
    Scene(
        title = stringResource(R.string.common_emoji),
        onClose = onCancel,
    ) {
        EmojiPickerGrid(
            emojis = AvatarEmoji.all,
            onSelect = { onSelect(it, background.toArgb()) },
            background = background,
            identicons = identicons,
            onSelectIdenticon = onSelectIdenticon,
        )
    }
}
