package com.gemwallet.android.features.onboarding.presents.create_wallet

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.components.screen.PhraseLayout
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.requestAuth
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.alpha50
import com.gemwallet.android.ui.theme.mainActionHeight
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.sceneContentPaddingValues
import com.gemwallet.android.ui.theme.space10
import com.gemwallet.android.ui.theme.space6
import kotlinx.coroutines.delay
import uniffi.gemstone.GemButtonState
import uniffi.gemstone.GemWalletSecretKind

private const val LightSurfaceLuminanceThreshold = 0.5f
private const val CopyFeedbackDurationMs = 1_500L
private const val CopyFeedbackAnimationMs = 180
private const val CopyIconInitialScale = 0.6f
private const val CopySuccessBackgroundAlpha = 0.12f
private const val DescriptionMinLines = 2
private val sectionSpacing = 20.dp
private val copyButtonMinWidth = 112.dp
private val copyButtonIconSize = 18.dp

@Composable
internal fun isLightThemeSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > LightSurfaceLuminanceThreshold

@Composable
internal fun onboardingContainerColor(): Color = if (isLightThemeSurface()) {
    MaterialTheme.colorScheme.background
} else {
    MaterialTheme.colorScheme.surface
}

@Composable
internal fun onboardingNeutralFillColor(): Color = if (isLightThemeSurface()) {
    MaterialTheme.colorScheme.surface
} else {
    MaterialTheme.colorScheme.surfaceContainerHighest
}

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
    var copyTrigger by remember { mutableIntStateOf(0) }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) {
            delay(CopyFeedbackDurationMs)
            isCopied = false
        }
    }

    Scene(
        title = stringResource(id = R.string.wallet_new_title),
        containerColor = onboardingContainerColor(),
        onClose = { onAction(CreateWalletAction.Cancel) },
        padding = sceneContentPaddingValues(),
        mainAction = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhraseCopyActionButton(
                    isCopied = isCopied,
                    enabled = words.isNotEmpty() && dataError == null,
                    onClick = {
                        onAction(CreateWalletAction.Copy)
                        isCopied = true
                        copyTrigger++
                    },
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
private fun PhraseCopyActionButton(isCopied: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val neutralFill = onboardingNeutralFillColor()
    val containerColor by animateColorAsState(
        targetValue = if (isCopied) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = CopySuccessBackgroundAlpha)
        } else {
            neutralFill
        },
        animationSpec = tween(CopyFeedbackAnimationMs),
        label = "phrase_copy_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isCopied) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(CopyFeedbackAnimationMs),
        label = "phrase_copy_content",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        elevation = null,
        contentPadding = PaddingValues(horizontal = paddingDefault),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = neutralFill.copy(alpha = alpha50),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha50),
        ),
        modifier = Modifier
            .heightIn(min = mainActionHeight)
            .widthIn(min = copyButtonMinWidth)
            .testTag("copy_phrase"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space6),
        ) {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = {
                    (fadeIn(tween(CopyFeedbackAnimationMs)) + scaleIn(tween(CopyFeedbackAnimationMs), initialScale = CopyIconInitialScale))
                        .togetherWith(fadeOut(tween(CopyFeedbackAnimationMs)) + scaleOut(tween(CopyFeedbackAnimationMs), targetScale = CopyIconInitialScale))
                },
                label = "phrase_copy_icon",
            ) { copied ->
                Icon(
                    imageVector = if (copied) AppIcons.Check else AppIcons.ContentCopyOutlined,
                    contentDescription = null,
                    modifier = Modifier.size(copyButtonIconSize),
                )
            }
            Text(
                text = stringResource(if (isCopied) R.string.wallet_copied else R.string.common_copy),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
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
