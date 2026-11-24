package com.gemwallet.android.features.wallet.presents.integration

import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.features.wallet.presents.SecretDataScene
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WalletTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uniffi.gemstone.GemPrivateKeyScope
import uniffi.gemstone.GemSecretWarning
import uniffi.gemstone.GemWalletSecret
import uniffi.gemstone.GemWalletSecretKind
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "w411dp-h891dp")
class SecretDataSceneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val words = listOf(
        "alpha", "bravo", "charlie", "delta",
        "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima",
    )

    @Test
    fun phraseSceneShowsCenteredDescriptionWordsAndCopyFeedback() {
        var copyCount = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                SecretDataScene(
                    title = composeRule.activity.getString(R.string.common_secret_phrase),
                    warning = GemSecretWarning.DO_NOT_SHARE,
                    secretKind = GemWalletSecretKind.PHRASE,
                    secret = GemWalletSecret.Words(words),
                    onCopy = { copyCount++ },
                    onCancel = {},
                )
            }
        }

        val description = composeRule.activity.getString(R.string.secret_phrase_do_not_share_description)
        val removedBannerTitle = composeRule.activity.getString(R.string.secret_phrase_do_not_share_title)
        val copyLabel = composeRule.activity.getString(R.string.common_copy)
        val copiedLabel = composeRule.activity.getString(R.string.wallet_copied)

        composeRule.onNodeWithText(description).assertIsDisplayed()
        composeRule.onNodeWithText(removedBannerTitle).assertDoesNotExist()
        composeRule.onNodeWithTag("phrase_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("word_0").assertTextEquals("alpha")
        composeRule.onNodeWithTag("word_11").assertTextEquals("lima")

        composeRule.onNodeWithTag("copy_secret").assertIsDisplayed().assertIsEnabled().assertTextEquals(copyLabel).performClick()
        passTime(0)
        assertEquals(1, copyCount)
        composeRule.onNodeWithTag("copy_secret").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag("copy_secret").performClick()
        passTime(0)
        assertEquals(2, copyCount)
        composeRule.onNodeWithTag("copy_secret").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag("copy_secret").assertTextEquals(copiedLabel)

        passTime(700)
        composeRule.onNodeWithTag("copy_secret").assertTextEquals(copyLabel)
    }

    @Test
    fun privateKeySceneShowsKeyDescriptionCardScopeAndCopyFeedback() {
        var copyCount = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                SecretDataScene(
                    title = composeRule.activity.getString(R.string.common_private_key),
                    warning = GemSecretWarning.DO_NOT_SHARE_PRIVATE_KEY,
                    secretKind = GemWalletSecretKind.PRIVATE_KEY,
                    secret = GemWalletSecret.PrivateKey("0xkey_example", GemPrivateKeyScope.SharedChains("EVM")),
                    onCopy = { copyCount++ },
                    onCancel = {},
                )
            }
        }

        val description = composeRule.activity.getString(R.string.private_key_do_not_share_description)
        val scope = composeRule.activity.getString(R.string.private_key_scope_shared, "EVM")
        val copyLabel = composeRule.activity.getString(R.string.common_copy)
        val copiedLabel = composeRule.activity.getString(R.string.wallet_copied)

        composeRule.onNodeWithText(description).assertIsDisplayed()
        composeRule.onNodeWithTag("private_key_card").assertIsDisplayed()
        composeRule.onNodeWithTag("private_key_value").assertIsDisplayed().assertTextEquals("0xkey_example")
        composeRule.onNodeWithTag("private_key_scope").assertIsDisplayed().assertTextEquals(scope)

        composeRule.onNodeWithTag("copy_secret").assertIsDisplayed().assertIsEnabled().assertTextEquals(copyLabel).performClick()
        passTime(0)
        assertEquals(1, copyCount)
        composeRule.onNodeWithTag("copy_secret").assertTextEquals(copiedLabel)
    }

    @Test
    fun privateKeySceneHidesScopeForSingleChainKey() {
        composeRule.setContent {
            WalletTheme {
                SecretDataScene(
                    title = composeRule.activity.getString(R.string.common_private_key),
                    warning = GemSecretWarning.DO_NOT_SHARE_PRIVATE_KEY,
                    secretKind = GemWalletSecretKind.PRIVATE_KEY,
                    secret = GemWalletSecret.PrivateKey("0xkey_single", GemPrivateKeyScope.OneChain),
                    onCopy = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag("private_key_card").assertIsDisplayed()
        composeRule.onNodeWithTag("private_key_scope").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "w600dp-h900dp")
    fun phraseAndPrivateKeyCardsMatchMaxWidthAndTopAlignment() {
        var showPrivateKey by mutableStateOf(false)

        composeRule.setContent {
            WalletTheme {
                if (showPrivateKey) {
                    SecretDataScene(
                        title = composeRule.activity.getString(R.string.common_private_key),
                        warning = GemSecretWarning.DO_NOT_SHARE_PRIVATE_KEY,
                        secretKind = GemWalletSecretKind.PRIVATE_KEY,
                        secret = GemWalletSecret.PrivateKey("0xkey_example", GemPrivateKeyScope.OneChain),
                        onCopy = {},
                        onCancel = {},
                    )
                } else {
                    SecretDataScene(
                        title = composeRule.activity.getString(R.string.common_secret_phrase),
                        warning = GemSecretWarning.DO_NOT_SHARE,
                        secretKind = GemWalletSecretKind.PHRASE,
                        secret = GemWalletSecret.Words(words),
                        onCopy = {},
                        onCancel = {},
                    )
                }
            }
        }

        val phraseBounds = composeRule.onNodeWithTag("phrase_layout").getUnclippedBoundsInRoot()
        assertEquals(SceneSizing.contentMaxWidth, phraseBounds.width)

        showPrivateKey = true
        composeRule.waitForIdle()
        val keyCardBounds = composeRule.onNodeWithTag("private_key_card").getUnclippedBoundsInRoot()
        assertEquals(SceneSizing.contentMaxWidth, keyCardBounds.width)
        assertEquals(phraseBounds.top, keyCardBounds.top)
    }

    private fun passTime(millis: Long) {
        if (millis > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
            composeRule.mainClock.advanceTimeBy(millis)
        }
        composeRule.mainClock.advanceTimeByFrame()
    }
}
