package com.gemwallet.android.features.onboarding.presents.create_wallet

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gemwallet.android.features.onboarding.presents.create_wallet.components.WordChip
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.CenteredDescriptionText
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.screen.PhraseLayout
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.models.buttonState
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.sceneContentPaddingValues
import com.gemwallet.android.ui.theme.space8
import uniffi.gemstone.GemButtonState
import uniffi.gemstone.GemVerifyPhraseChoice
import uniffi.gemstone.GemVerifyPhraseViewState

private val sectionSpacing = 20.dp
private val choicesVerticalPadding = 20.dp

@Composable
internal fun VerifyPhraseScene(state: GemVerifyPhraseViewState, choices: List<String>, words: List<String>, onPick: (Int) -> Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val isSmallScreen = isCompactDimension(WindowDimension.Height)
    val groups = if (isSmallScreen) {
        listOfNotNull(state.currentGroup?.let { state.groups.getOrNull(it) })
    } else {
        state.groups
    }
    val verticalChoicesPadding = if (isSmallScreen) paddingDefault else choicesVerticalPadding

    Scene(
        title = stringResource(id = R.string.transfer_confirm),
        containerColor = onboardingContainerColor(),
        onClose = onCancel,
        padding = sceneContentPaddingValues(),
        mainAction = {
            MainActionButton(
                title = stringResource(id = R.string.common_continue),
                state = state.button.buttonState(),
            ) {
                onDone()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CenteredDescriptionText(stringResource(R.string.secret_phrase_confirm_quick_test_title))
            Spacer(modifier = Modifier.height(sectionSpacing))
            PhraseLayout(
                words = words,
                modifier = Modifier.widthIn(max = SceneSizing.contentMaxWidth),
                highlightIndex = state.nextIndex,
            )
            AnimatedVisibility(visible = !state.isComplete || !isSmallScreen) {
                Column(
                    modifier = Modifier
                        .padding(vertical = verticalChoicesPadding)
                        .widthIn(max = SceneSizing.contentMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(space8),
                ) {
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(space8),
                        ) {
                            group.forEach { choice ->
                                WordChip(
                                    word = choices.getOrElse(choice.index) { "" },
                                    isEnable = !choice.isPicked,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPick(choice.index) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewVerifyPhraseScene() {
    val choices = listOf(
        "delta", "alpha", "bravo", "charlie",
        "golf", "echo", "foxtrot", "hotel",
        "juliet", "lima", "india", "kilo",
    )
    val groups = choices.indices.chunked(4).map { chunk ->
        chunk.map { index -> GemVerifyPhraseChoice(index = index, isPicked = index == 1) }
    }
    WalletTheme {
        VerifyPhraseScene(
            state = GemVerifyPhraseViewState(
                verifiedCount = 1,
                groups = groups,
                currentGroup = 0,
                nextIndex = 1,
                isComplete = false,
                button = GemButtonState.DISABLED,
            ),
            choices = choices,
            words = listOf("alpha") + List(11) { "" },
            onPick = { true },
            onDone = {},
            onCancel = {},
        )
    }
}
