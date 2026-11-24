package com.gemwallet.android.features.wallet.presents

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.AppUrl
import com.gemwallet.android.features.wallet.viewmodels.SecretDataViewModel
import com.gemwallet.android.ui.DetectScreenshot
import com.gemwallet.android.ui.DisableScreenShooting
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.CenteredDescriptionText
import com.gemwallet.android.ui.components.DocsInfoButton
import com.gemwallet.android.ui.components.buttons.CopyButton
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.components.screen.LoadingScene
import com.gemwallet.android.ui.components.screen.PhraseLayout
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.components.sensitiveData
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.hairlineThickness
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.plainContainer
import com.gemwallet.android.ui.theme.sceneContentPaddingValues
import uniffi.gemstone.GemPrivateKeyScope
import uniffi.gemstone.GemSecretWarning
import uniffi.gemstone.GemWalletSecret
import uniffi.gemstone.GemWalletSecretKind
import uniffi.gemstone.privateKeyCopy
import uniffi.gemstone.secretPhraseCopy
import uniffi.gemstone.secretScreen

private const val DescriptionMinLines = 2
private val sectionSpacing = 20.dp
private val privateKeyCardVerticalPadding = 20.dp

@Composable
fun SecretDataScreen(onCancel: () -> Unit, viewModel: SecretDataViewModel = hiltViewModel()) {
    DisableScreenShooting()
    DetectScreenshot(viewModel.secretKind)

    val result by viewModel.secret.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = rememberClipboard()

    val secret = result?.getOrNull()
    val screen = secretScreen(viewModel.secretKind, false)
    val title = screen.title.string(context)
    val containerColor = MaterialTheme.colorScheme.plainContainer
    if (secret == null) {
        if (result == null) {
            LoadingScene(title = title, onCancel = onCancel, containerColor = containerColor)
        } else {
            SecretDataErrorScene(title = title, onCancel = onCancel, containerColor = containerColor)
        }
        return
    }

    SecretDataScene(
        title = title,
        warning = screen.warning,
        secretKind = viewModel.secretKind,
        secret = secret,
        onCopy = {
            val copy = when (secret) {
                is GemWalletSecret.PrivateKey -> privateKeyCopy(secret.key)
                is GemWalletSecret.Words -> secretPhraseCopy(secret.words)
            }
            clipboard.copy(copy)
        },
        onCancel = onCancel,
    )
}

@Composable
internal fun SecretDataScene(title: String, warning: GemSecretWarning, secretKind: GemWalletSecretKind, secret: GemWalletSecret, onCopy: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current

    Scene(
        title = title,
        containerColor = MaterialTheme.colorScheme.plainContainer,
        padding = sceneContentPaddingValues(),
        actions = {
            DocsInfoButton(AppUrl.secretSecurity(secretKind))
        },
        onClose = onCancel,
        mainAction = {
            CopyButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_secret"),
                onClick = onCopy,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CenteredDescriptionText(
                text = stringResource(id = warning.descriptionRes()),
                minLines = DescriptionMinLines,
            )
            Spacer(modifier = Modifier.height(sectionSpacing))
            when (secret) {
                is GemWalletSecret.PrivateKey -> {
                    PrivateKeyCard(key = secret.key)
                    secret.scope.string(context)?.let { scope ->
                        Spacer(modifier = Modifier.height(paddingDefault))
                        Text(
                            text = scope,
                            modifier = Modifier
                                .widthIn(max = SceneSizing.contentMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = paddingSmall)
                                .testTag("private_key_scope"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                is GemWalletSecret.Words -> PhraseLayout(
                    words = secret.words,
                    modifier = Modifier.widthIn(max = SceneSizing.contentMaxWidth),
                )
            }
        }
    }
}

@Composable
private fun PrivateKeyCard(key: String) {
    Surface(
        modifier = Modifier
            .widthIn(max = SceneSizing.contentMaxWidth)
            .fillMaxWidth()
            .testTag("private_key_card"),
        shape = RoundedCornerShape(paddingDefault),
        color = MaterialTheme.colorScheme.plainContainer,
        border = BorderStroke(hairlineThickness, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = key,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = paddingDefault, vertical = privateKeyCardVerticalPadding)
                .testTag("private_key_value")
                .sensitiveData(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@StringRes
private fun GemSecretWarning.descriptionRes(): Int = when (this) {
    GemSecretWarning.DO_NOT_SHARE -> R.string.secret_phrase_do_not_share_description
    GemSecretWarning.DO_NOT_SHARE_PRIVATE_KEY -> R.string.private_key_do_not_share_description
    GemSecretWarning.SAVE_SAFELY -> R.string.secret_phrase_save_phrase_safely
}

@Composable
internal fun SecretDataErrorScene(title: String, onCancel: () -> Unit, containerColor: Color = MaterialTheme.colorScheme.surface) {
    Scene(
        title = title,
        containerColor = containerColor,
        padding = sceneContentPaddingValues(),
        onClose = onCancel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingDefault),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.errors_keystore_access),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSecretDataPhraseScene() {
    WalletTheme {
        SecretDataScene(
            title = stringResource(R.string.common_secret_phrase),
            warning = GemSecretWarning.DO_NOT_SHARE,
            secretKind = GemWalletSecretKind.PHRASE,
            secret = GemWalletSecret.Words(
                listOf(
                    "alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
                    "golf", "hotel", "india", "juliet", "kilo", "lima",
                ),
            ),
            onCopy = {},
            onCancel = {},
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSecretDataPrivateKeyScene() {
    WalletTheme {
        SecretDataScene(
            title = stringResource(R.string.common_private_key),
            warning = GemSecretWarning.DO_NOT_SHARE_PRIVATE_KEY,
            secretKind = GemWalletSecretKind.PRIVATE_KEY,
            secret = GemWalletSecret.PrivateKey(
                key = "0x" + "0".repeat(64),
                scope = GemPrivateKeyScope.SharedChains("EVM"),
            ),
            onCopy = {},
            onCancel = {},
        )
    }
}
