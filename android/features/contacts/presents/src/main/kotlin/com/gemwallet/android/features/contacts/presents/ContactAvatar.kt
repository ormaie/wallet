package com.gemwallet.android.features.contacts.presents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.gemwallet.android.ui.components.image.ListItemImageView
import com.gemwallet.android.ui.components.image.RemoveBadge
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.theme.listItemIconSize

@Composable
internal fun ContactAvatar(
    image: ListItemImage,
    modifier: Modifier = Modifier,
    size: Dp = listItemIconSize,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = size / 2),
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(modifier = modifier.then(clickModifier)) {
        ListItemImageView(image = image, size = size)
        if (onRemove != null) {
            RemoveBadge(onClick = onRemove)
        }
    }
}
