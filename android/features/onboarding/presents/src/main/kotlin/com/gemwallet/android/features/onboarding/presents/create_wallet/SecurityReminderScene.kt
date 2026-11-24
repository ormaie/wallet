package com.gemwallet.android.features.onboarding.presents.create_wallet

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gemwallet.android.AppUrl
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.introRes
import com.gemwallet.android.ui.localization.subtitleRes
import com.gemwallet.android.ui.localization.titleRes
import com.gemwallet.android.ui.models.actions.CancelAction
import com.gemwallet.android.ui.open
import com.gemwallet.android.ui.style.accentColor
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.hairlineThickness
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingLarge
import com.gemwallet.android.ui.theme.plainContainer
import com.gemwallet.android.ui.theme.space24
import com.gemwallet.android.ui.theme.space6
import uniffi.gemstone.DocsUrl
import uniffi.gemstone.GemSecurityReminderItem
import uniffi.gemstone.GemSecurityReminderKind
import uniffi.gemstone.securityReminderItems

private const val TabularNumbersFeature = "tnum"
private const val OrdinalDigits = 2
private val ruleVerticalPadding = 20.dp

@Composable
fun SecurityReminderScene(title: String = stringResource(R.string.wallet_new_title), kind: GemSecurityReminderKind = GemSecurityReminderKind.PHRASE, onAccept: () -> Unit, onCancel: CancelAction) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val items = remember(kind) { securityReminderItems(kind) }

    Scene(
        title = title,
        containerColor = MaterialTheme.colorScheme.plainContainer,
        mainAction = {
            MainActionButton(
                stringResource(R.string.common_continue),
                onClick = onAccept,
            )
        },
        actions = {
            IconButton(
                { uriHandler.open(context, AppUrl.docs(kind.docsUrl())) },
            ) {
                Icon(AppIcons.InfoOutlined, stringResource(R.string.common_learn_more))
            }
        },
        onClose = { onCancel() },
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SceneSizing.buttonMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space24),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = space24, bottom = paddingLarge),
                text = stringResource(kind.introRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(
                thickness = hairlineThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            items.forEachIndexed { index, item ->
                SecurityReminderItem(
                    ordinal = (index + 1).toString().padStart(OrdinalDigits, '0'),
                    item = item,
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        thickness = hairlineThickness,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.size(it.calculateBottomPadding()))
        }
    }
}

@Composable
private fun SecurityReminderItem(ordinal: String, item: GemSecurityReminderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ruleVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(paddingDefault),
    ) {
        Text(
            text = ordinal,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                fontFeatureSettings = TabularNumbersFeature,
            ),
            color = item.accentColor(),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(space6),
        ) {
            Text(
                text = stringResource(item.titleRes()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(item.subtitleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private fun GemSecurityReminderKind.docsUrl(): DocsUrl = when (this) {
    GemSecurityReminderKind.PHRASE -> DocsUrl.WhatIsSecretPhrase
    GemSecurityReminderKind.PRIVATE_KEY, GemSecurityReminderKind.ACCOUNT_KEY -> DocsUrl.WhatIsPrivateKey
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSecurityReminderScene() {
    WalletTheme {
        SecurityReminderScene(onAccept = {}, onCancel = {})
    }
}
