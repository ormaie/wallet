package com.gemwallet.android.ui.components.empty

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gemwallet.android.ui.icons.cachedDrawablePainter
import com.gemwallet.android.ui.icons.cachedVectorResource
import com.gemwallet.android.ui.localization.text
import com.gemwallet.android.ui.localization.title
import com.gemwallet.android.ui.style.image
import uniffi.gemstone.GemEmptyState
import uniffi.gemstone.GemEmptyStateAction
import uniffi.gemstone.GemEmptyStateKind
import uniffi.gemstone.emptyState

@Composable
fun EmptyContentView(kind: GemEmptyStateKind, modifier: Modifier = Modifier, symbol: String = "", onAction: ((GemEmptyStateAction) -> Unit)? = null) {
    EmptyContentView(state = emptyState(kind), modifier = modifier, symbol = symbol, onAction = onAction)
}

@Composable
fun EmptyContentView(state: GemEmptyState, modifier: Modifier = Modifier, symbol: String = "", onAction: ((GemEmptyStateAction) -> Unit)? = null) {
    val context = LocalContext.current
    val (icon, iconVector) = when (val image = state.image.image()) {
        is EmptyStateImage.Drawable -> cachedDrawablePainter(image.id) to null
        is EmptyStateImage.Vector -> null to cachedVectorResource(image.id)
    }
    EmptyStateView(
        title = state.title.text(context, symbol),
        description = state.description?.text(context, symbol),
        icon = icon,
        iconVector = iconVector,
        buttons = onAction?.let { onAction ->
            state.actions.map { action -> EmptyAction(title = stringResource(action.title()), onClick = { onAction(action) }, style = EmptyActionStyle.Secondary) }
        }.orEmpty(),
        modifier = modifier,
    )
}
