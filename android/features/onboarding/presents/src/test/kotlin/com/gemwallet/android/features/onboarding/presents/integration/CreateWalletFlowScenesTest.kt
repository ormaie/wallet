package com.gemwallet.android.features.onboarding.presents.integration

import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.features.onboarding.presents.create_wallet.CreateWalletAction
import com.gemwallet.android.features.onboarding.presents.create_wallet.CreateWalletScene
import com.gemwallet.android.features.onboarding.presents.create_wallet.VerifyPhraseScene
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.theme.WalletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uniffi.gemstone.GemButtonState
import uniffi.gemstone.GemVerifyPhraseChoice
import uniffi.gemstone.GemVerifyPhraseViewState
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "w411dp-h891dp")
class CreateWalletFlowScenesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val words = listOf(
        "alpha", "bravo", "charlie", "alpha",
        "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima",
    )

    @Test
    fun copyButtonShowsCopiedFeedbackResetsDurationOnRetapAndTriggersContinue() {
        val actions = mutableListOf<CreateWalletAction>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                CreateWalletScene(
                    words = words,
                    dataError = null,
                    onAction = { actions.add(it) },
                )
            }
        }

        val copyLabel = composeRule.activity.getString(R.string.common_copy)
        val copiedLabel = composeRule.activity.getString(R.string.wallet_copied)

        composeRule.onNodeWithTag("copy_phrase").assertIsDisplayed().assertIsEnabled().assertTextEquals(copyLabel).performClick()
        passTime(0)
        assertEquals(listOf(CreateWalletAction.Copy), actions)
        composeRule.onNodeWithTag("copy_phrase").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag("copy_phrase").performClick()
        passTime(0)
        assertEquals(listOf(CreateWalletAction.Copy, CreateWalletAction.Copy), actions)
        composeRule.onNodeWithTag("copy_phrase").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag("copy_phrase").assertTextEquals(copiedLabel)

        passTime(700)
        composeRule.onNodeWithTag("copy_phrase").assertTextEquals(copyLabel)

        composeRule.onNodeWithTag("main_action").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(CreateWalletAction.Continue, actions.last())
    }

    @Test
    fun copyButtonIsDisabledWhenWordsAreEmpty() {
        composeRule.setContent {
            WalletTheme {
                CreateWalletScene(
                    words = emptyList(),
                    dataError = null,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("copy_phrase").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun verifyPhraseSceneHandlesWrongTapDuplicatesAndEnablesContinueWhenComplete() {
        val choices = listOf(
            "charlie", "alpha", "bravo", "alpha",
            "golf", "echo", "foxtrot", "hotel",
            "juliet", "lima", "india", "kilo",
        )
        var picked by mutableStateOf(emptyList<Int>())
        var doneClicks = 0

        fun buildState(pickedIndices: List<Int>): GemVerifyPhraseViewState {
            val verifiedCount = pickedIndices.size
            val complete = verifiedCount == words.size
            val nextIndex = if (complete) null else verifiedCount
            val groupItems = choices.indices.chunked(4).map { chunk ->
                chunk.map { index -> GemVerifyPhraseChoice(index = index, isPicked = index in pickedIndices) }
            }
            return GemVerifyPhraseViewState(
                verifiedCount = verifiedCount,
                groups = groupItems,
                currentGroup = nextIndex?.div(4),
                nextIndex = nextIndex,
                isComplete = complete,
                button = if (complete) GemButtonState.ENABLED else GemButtonState.DISABLED,
            )
        }

        composeRule.setContent {
            val state = buildState(picked)
            val filledWords = words.mapIndexed { index, word -> if (index < state.verifiedCount) word else "" }
            WalletTheme {
                VerifyPhraseScene(
                    state = state,
                    choices = choices,
                    words = filledWords,
                    onPick = { choiceIndex ->
                        val nextWord = words.getOrNull(picked.size)
                        val isMatch = choiceIndex !in picked && choices.getOrNull(choiceIndex) == nextWord
                        if (isMatch) {
                            picked = picked + choiceIndex
                        }
                        isMatch
                    },
                    onDone = { doneClicks++ },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag("main_action").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("word_option").assertCountEquals(12)

        composeRule.onNode(hasTestTag("word_option") and hasText("bravo") and isEnabled()).performClick()
        composeRule.waitForIdle()
        assertTrue(picked.isEmpty())
        composeRule.onNodeWithTag("word_0").assertTextEquals("")

        words.forEachIndexed { index, expectedWord ->
            composeRule.onAllNodes(hasTestTag("word_option") and hasText(expectedWord) and isEnabled())[0].performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("word_$index").assertTextEquals(expectedWord)
        }

        composeRule.onAllNodesWithTag("word_option").assertCountEquals(0)
        composeRule.onNodeWithTag("main_action").assertIsEnabled().performClick()
        assertEquals(1, doneClicks)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "w600dp-h900dp")
    fun phraseLayoutMatchesSizeAndTopPositionAcrossCreateAndVerifyScenes() {
        var showVerify by mutableStateOf(false)
        var verifiedCount by mutableIntStateOf(0)

        composeRule.setContent {
            WalletTheme {
                if (showVerify) {
                    val complete = verifiedCount == words.size
                    val nextIndex = if (complete) null else verifiedCount
                    val groups = words.indices.chunked(4).map { chunk ->
                        chunk.map { index -> GemVerifyPhraseChoice(index = index, isPicked = index < verifiedCount) }
                    }
                    VerifyPhraseScene(
                        state = GemVerifyPhraseViewState(
                            verifiedCount = verifiedCount,
                            groups = groups,
                            currentGroup = nextIndex?.div(4),
                            nextIndex = nextIndex,
                            isComplete = complete,
                            button = if (complete) GemButtonState.ENABLED else GemButtonState.DISABLED,
                        ),
                        choices = words,
                        words = words.mapIndexed { index, word -> if (index < verifiedCount) word else "" },
                        onPick = { true },
                        onDone = {},
                        onCancel = {},
                    )
                } else {
                    CreateWalletScene(
                        words = words,
                        dataError = null,
                        onAction = {},
                    )
                }
            }
        }

        val createBounds = composeRule.onNodeWithTag("phrase_layout").getUnclippedBoundsInRoot()

        showVerify = true
        for (count in listOf(0, 6, words.size)) {
            verifiedCount = count
            composeRule.waitForIdle()
            val verifyBounds = composeRule.onNodeWithTag("phrase_layout").getUnclippedBoundsInRoot()
            assertEquals(createBounds.width, verifyBounds.width)
            assertEquals(createBounds.height, verifyBounds.height)
            assertEquals(createBounds.top, verifyBounds.top)
        }
    }

    private fun passTime(millis: Long) {
        if (millis > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
            composeRule.mainClock.advanceTimeBy(millis)
        }
        composeRule.mainClock.advanceTimeByFrame()
    }
}
