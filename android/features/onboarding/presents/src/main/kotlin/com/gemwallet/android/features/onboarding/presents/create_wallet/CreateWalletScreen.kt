package com.gemwallet.android.features.onboarding.presents.create_wallet

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.features.onboarding.presents.components.LoadingDialog
import com.gemwallet.android.features.onboarding.viewmodels.create_wallet.CreateWalletViewModel
import com.gemwallet.android.model.AuthRequest
import com.gemwallet.android.ui.DetectScreenshot
import com.gemwallet.android.ui.DisableScreenShooting
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.CenteredDescriptionText
import com.gemwallet.android.ui.components.animation.navigationSlideTransition
import com.gemwallet.android.ui.components.buttons.CopyButton
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.components.screen.PhraseLayout
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.requestAuth
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.plainContainer
import com.gemwallet.android.ui.theme.sceneContentPaddingValues
import com.gemwallet.android.ui.theme.space10
import uniffi.gemstone.GemButtonState
import uniffi.gemstone.GemWalletSecretKind

private const val DescriptionMinLines = 2
private val sectionSpacing = 20.dp

@Composable
fun CreateWalletScreen(onCancel: () -> Unit, onCreated: () -> Unit) {
    DisableScreenShooting()
    DetectScreenshot(GemWalletSecretKind.PHRASE)

    val context = LocalContext.current
    val clipboard = rememberClipboard()
    val viewModel: CreateWalletViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorText by viewModel.errorText.collectAsStateWithLifecycle()
    val verificationState by viewModel.verificationState.collectAsStateWithLifecycle()
    val verifiedWords by viewModel.verifiedWords.collectAsStateWithLifecycle()
    val verificationChoices by viewModel.verificationChoices.collectAsStateWithLifecycle()
    val isCreated by viewModel.isCreated.collectAsStateWithLifecycle()

    LaunchedEffect(isCreated) {
        if (isCreated) {
            viewModel.onCreatedHandled()
            onCreated()
        }
    }

    BackHandler(uiState.isShowSafeMessage) {
        viewModel.dismissSafeMessage()
    }

    AnimatedContent(
        targetState = uiState.isShowSafeMessage,
        transitionSpec = {
            navigationSlideTransition(forward = targetState)
        },
        label = "phrase",
    ) { state ->
        when (state) {
            true -> verificationState?.let { verification ->
                VerifyPhraseScene(
                    state = verification,
                    choices = verificationChoices,
                    words = verifiedWords,
                    onPick = viewModel::onPickWord,
                    onDone = { context.requestAuth(AuthRequest.Default) { viewModel.createWallet() } },
                    onCancel = viewModel::dismissSafeMessage,
                )
            }

            false -> CreateWalletScene(words = uiState.data, dataError = errorText) { action ->
                when (action) {
                    CreateWalletAction.Copy -> clipboard.copy(viewModel.phraseCopy())
                    CreateWalletAction.Continue -> viewModel.confirmPhrase()
                    CreateWalletAction.Cancel -> onCancel()
                }
            }
        }
    }
    if (verificationState?.button == GemButtonState.LOADING) {
        LoadingDialog()
    }
}

@Composable
internal fun CreateWalletScene(words: List<String>, dataError: String?, onAction: (CreateWalletAction) -> Unit) {
    Scene(
        title = stringResource(id = R.string.wallet_new_title),
        containerColor = MaterialTheme.colorScheme.plainContainer,
        onClose = { onAction(CreateWalletAction.Cancel) },
        padding = sceneContentPaddingValues(),
        mainAction = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CopyButton(
                    enabled = words.isNotEmpty() && dataError == null,
                    modifier = Modifier.testTag("copy_phrase"),
                    onClick = { onAction(CreateWalletAction.Copy) },
                )
                MainActionButton(
                    title = stringResource(id = R.string.common_continue),
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(CreateWalletAction.Continue) },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dataError != null) {
                Text(text = dataError)
            } else {
                CenteredDescriptionText(
                    text = stringResource(id = R.string.secret_phrase_save_phrase_safely),
                    minLines = DescriptionMinLines,
                )
                Spacer(modifier = Modifier.height(sectionSpacing))
                PhraseLayout(
                    words = words,
                    modifier = Modifier.widthIn(max = SceneSizing.contentMaxWidth),
                )
            }
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Pixel 2", device = Devices.PIXEL_2)
@Preview(name = "Nexus 5", device = Devices.NEXUS_5)
fun PreviewCreateWalletScene() {
    val words = listOf(
        "cinnamon", "two", "three", "cinnamon", "five", "six",
        "seven", "eight", "cinnamon", "ten", "eleven", "twelve",
    )
    WalletTheme {
        CreateWalletScene(
            words = words,
            dataError = null,
            onAction = {},
        )
    }
}
