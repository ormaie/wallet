package com.gemwallet.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.hairlineThickness
import com.gemwallet.android.ui.theme.space0

private val menuCornerRadius = 16.dp
private val menuShadowElevation = 8.dp
private val menuItemMinWidth = 148.dp
private val menuItemMinHeight = 40.dp
private val menuItemHorizontalPadding = 16.dp
private val menuItemVerticalPadding = 8.dp
private val menuItemIconSpacing = 20.dp
private val menuIconSize = 18.dp
private val menuDividerInset = 14.dp

private const val darkLuminanceThreshold = 0.5f
private const val lightBorderAlpha = 0.08f
private const val darkBorderAlpha = 0.10f
private const val lightItemDividerAlpha = 0.40f
private const val darkItemDividerAlpha = 0.48f
private const val lightGroupDividerAlpha = 0.72f
private const val darkGroupDividerAlpha = 0.85f
private const val menuIconAlpha = 0.70f
private const val darkPrimaryLift = 0.28f

@Composable
fun GemDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(space0, space0),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(menuCornerRadius),
        containerColor = gemMenuContainerColor(),
        tonalElevation = space0,
        shadowElevation = menuShadowElevation,
        border = BorderStroke(hairlineThickness, gemMenuBorderColor()),
        content = content,
    )
}

@Composable
fun GemDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    isSelected: Boolean = false,
    isDestructive: Boolean = false,
) {
    val iconVector = trailingIcon ?: if (isSelected) AppIcons.Check else null
    val iconTint = gemMenuIconColor(isSelected = isSelected, isDestructive = isDestructive)
    GemDropdownMenuRow(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isSelected = isSelected,
        isDestructive = isDestructive,
        trailing = iconVector?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(menuIconSize),
                )
            }
        },
    )
}

@Composable
fun GemDropdownMenuItem(
    text: String,
    trailingIcon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val iconTint = gemMenuIconColor(isSelected = false, isDestructive = isDestructive)
    GemDropdownMenuRow(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isSelected = false,
        isDestructive = isDestructive,
        trailing = {
            Icon(
                painter = trailingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(menuIconSize),
            )
        },
    )
}

@Composable
fun GemDropdownMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = menuDividerInset),
        thickness = hairlineThickness,
        color = gemMenuItemDividerColor(),
    )
}

@Composable
fun GemDropdownMenuGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = hairlineThickness,
        color = gemMenuGroupDividerColor(),
    )
}

@Composable
private fun GemDropdownMenuRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    isSelected: Boolean,
    isDestructive: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    val textColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        isSelected -> gemMenuSelectedColor()
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = menuItemMinWidth)
            .defaultMinSize(minHeight = menuItemMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = menuItemHorizontalPadding, vertical = menuItemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(menuItemIconSpacing),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected || isDestructive) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
private fun isDarkColorScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < darkLuminanceThreshold

@Composable
private fun gemMenuContainerColor(): Color = if (isDarkColorScheme()) {
    MaterialTheme.colorScheme.surfaceContainerHighest
} else {
    MaterialTheme.colorScheme.background
}

@Composable
private fun gemMenuBorderColor(): Color {
    val alpha = if (isDarkColorScheme()) darkBorderAlpha else lightBorderAlpha
    return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
}

@Composable
private fun gemMenuItemDividerColor(): Color {
    val alpha = if (isDarkColorScheme()) darkItemDividerAlpha else lightItemDividerAlpha
    return MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
}

@Composable
private fun gemMenuGroupDividerColor(): Color {
    val alpha = if (isDarkColorScheme()) darkGroupDividerAlpha else lightGroupDividerAlpha
    return MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
}

@Composable
private fun gemMenuSelectedColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    return if (isDarkColorScheme()) lerp(primary, Color.White, darkPrimaryLift) else primary
}

@Composable
private fun gemMenuIconColor(isSelected: Boolean, isDestructive: Boolean): Color = when {
    isDestructive -> MaterialTheme.colorScheme.error
    isSelected -> gemMenuSelectedColor()
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = menuIconAlpha)
}
