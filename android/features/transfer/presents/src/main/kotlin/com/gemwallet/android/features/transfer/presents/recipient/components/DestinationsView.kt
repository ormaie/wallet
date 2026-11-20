package com.gemwallet.android.features.transfer.presents.recipient.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemwallet.android.features.transfer.viewmodels.recipient.models.QrScanField
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.fields.AddressChainField
import com.gemwallet.android.ui.components.fields.MemoTextField
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.theme.alpha10
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space4
import uniffi.gemstone.GemNameIndicator
import uniffi.gemstone.GemRecipient

private val matchedContactIconSize = 14.dp

fun LazyListScope.destinationView(
    hasMemo: Boolean,
    address: String,
    addressError: String,
    nameResolveIndicator: GemNameIndicator?,
    matchedContact: GemRecipient?,
    memo: String,
    onAddress: (String) -> Unit,
    onMemo: (String) -> Unit,
    onUseMatchedMemo: () -> Unit,
    onQrScan: (QrScanField) -> Unit,
    onSubmitAddress: () -> Unit,
) {
    item {
        val matchedName = if (addressError.isEmpty()) matchedContact?.name?.takeIf { it.isNotBlank() } else null
        val showUseMemo = matchedName != null && !matchedContact?.memo.isNullOrBlank() && memo.isBlank()
        Column {
            AddressChainField(
                value = address,
                label = stringResource(id = R.string.transfer_recipient_address_field),
                floatingLabel = {
                    AddressFloatingLabel(
                        defaultLabel = stringResource(id = R.string.transfer_recipient_address_field),
                        matchedName = matchedName,
                    )
                },
                indicator = nameResolveIndicator,
                error = addressError,
                onValueChange = onAddress,
                modifier = Modifier.testTag("addressInputField"),
                onQrScanner = { onQrScan(QrScanField.Address) },
                onSubmit = onSubmitAddress,
            )
            if (hasMemo) {
                MemoTextField(
                    value = memo,
                    label = stringResource(id = R.string.transfer_memo),
                    onValueChange = onMemo,
                    modifier = Modifier.testTag("memoInputField"),
                    trailingAction = if (showUseMemo) {
                        { UseMatchedMemoChip(onUseMemo = onUseMatchedMemo) }
                    } else {
                        null
                    },
                    onQrScanner = { onQrScan(QrScanField.Memo) },
                )
            }
        }
    }
}

@Composable
private fun AddressFloatingLabel(
    defaultLabel: String,
    matchedName: String?,
) {
    AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        targetState = matchedName,
        transitionSpec = { fadeIn() togetherWith fadeOut() using null },
        label = "AddressFloatingLabel",
    ) { name ->
        if (name != null) {
            val accentColor = MaterialTheme.colorScheme.tertiary
            Row(
                modifier = Modifier.testTag("matchedContactHint"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space4),
            ) {
                Icon(
                    modifier = Modifier.size(matchedContactIconSize),
                    imageVector = AppIcons.CheckCircleOutlined,
                    contentDescription = null,
                    tint = accentColor,
                )
                Text(
                    text = stringResource(id = R.string.transfer_recipient_matched_contact, name),
                    color = accentColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = defaultLabel,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UseMatchedMemoChip(onUseMemo: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val chipShape = RoundedCornerShape(paddingSmall)
    Text(
        modifier = Modifier
            .clip(chipShape)
            .background(accentColor.copy(alpha = alpha10))
            .clickable(onClick = onUseMemo)
            .padding(horizontal = paddingSmall, vertical = paddingHalfSmall)
            .testTag("useMatchedContactMemo"),
        text = stringResource(id = R.string.transfer_recipient_use_memo),
        color = accentColor,
        style = MaterialTheme.typography.labelSmall,
    )
}
