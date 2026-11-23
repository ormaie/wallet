package com.gemwallet.android.features.transfer.presents.integration

import android.content.ClipboardManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.features.transfer.presents.receive.ReceiveScene
import com.gemwallet.android.testkit.mockAccount
import com.gemwallet.android.testkit.mockAsset
import com.gemwallet.android.testkit.mockAssetData
import com.gemwallet.android.testkit.mockAssetId
import com.gemwallet.android.testkit.mockGemChainRow
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.theme.WalletTheme
import com.wallet.core.primitives.Chain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uniffi.gemstone.addressCopy
import uniffi.gemstone.assetText
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ReceiveSceneCopyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val address = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA"
    private val asset = mockAsset(id = mockAssetId(chain = Chain.Base), name = "USDC", symbol = "USDC")

    @Test
    fun tapShowsCopiedFeedbackCopiesAddressAndResetsDurationOnRetap() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                ReceiveScene(
                    closeIcon = false,
                    assetInfo = mockAssetData(
                        asset = asset,
                        account = mockAccount(chain = Chain.Base, address = address),
                    ),
                    header = assetText(asset.toGem()),
                    warnings = emptyList(),
                    shareText = address,
                    copyText = addressCopy(Chain.Base.string, address),
                    chainRow = mockGemChainRow(title = "Base"),
                    onSelectNetwork = null,
                    onCancel = {},
                )
            }
        }

        val copyLabel = composeRule.activity.getString(R.string.wallet_copy_address)
        val copiedLabel = composeRule.activity.getString(R.string.wallet_copied)
        val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)

        composeRule.onNodeWithTag("receive_qr_code").assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithTag("main_action").assertIsDisplayed().assertTextEquals(copyLabel).performClick()
        passTime(0)
        assertEquals(address, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        composeRule.onNodeWithTag("main_action").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag(address).performClick()
        passTime(0)
        composeRule.onNodeWithTag("main_action").assertTextEquals(copiedLabel)

        passTime(900)
        composeRule.onNodeWithTag("main_action").assertTextEquals(copiedLabel)

        passTime(700)
        composeRule.onNodeWithTag("main_action").assertTextEquals(copyLabel)
    }

    @Test
    fun changingCopyTargetResetsCopiedStateImmediately() {
        var copyTarget by mutableStateOf(addressCopy(Chain.Base.string, address))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                ReceiveScene(
                    closeIcon = false,
                    assetInfo = mockAssetData(
                        asset = asset,
                        account = mockAccount(chain = Chain.Base, address = address),
                    ),
                    header = assetText(asset.toGem()),
                    warnings = emptyList(),
                    shareText = address,
                    copyText = copyTarget,
                    chainRow = mockGemChainRow(title = "Base"),
                    onSelectNetwork = null,
                    onCancel = {},
                )
            }
        }

        val copyLabel = composeRule.activity.getString(R.string.wallet_copy_address)
        val copiedLabel = composeRule.activity.getString(R.string.wallet_copied)

        composeRule.onNodeWithTag("main_action").performClick()
        passTime(0)
        composeRule.onNodeWithTag("main_action").assertTextEquals(copiedLabel)

        copyTarget = addressCopy(Chain.Arbitrum.string, address)
        passTime(0)
        composeRule.onNodeWithTag("main_action").assertTextEquals(copyLabel)
    }

    @Test
    fun networkPillOpensSelectorOnlyWhenSelectable() {
        var selectClicks = 0
        var selectable by mutableStateOf(true)
        composeRule.setContent {
            WalletTheme {
                ReceiveScene(
                    closeIcon = false,
                    assetInfo = mockAssetData(
                        asset = asset,
                        account = mockAccount(chain = Chain.Base, address = address),
                    ),
                    header = assetText(asset.toGem()),
                    warnings = emptyList(),
                    shareText = address,
                    copyText = addressCopy(Chain.Base.string, address),
                    chainRow = mockGemChainRow(title = "Base"),
                    onSelectNetwork = if (selectable) {
                        { selectClicks++ }
                    } else {
                        null
                    },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("Base").assertIsDisplayed().performClick()
        assertEquals(1, selectClicks)

        selectable = false
        composeRule.onNodeWithText("Base").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun cardHeaderShowsSymbolAndWarningsRenderOnSeparateLines() {
        val tether = mockAsset(id = mockAssetId(chain = Chain.Ethereum), name = "Tether", symbol = "USDT")
        composeRule.setContent {
            WalletTheme {
                ReceiveScene(
                    closeIcon = false,
                    assetInfo = mockAssetData(
                        asset = tether,
                        account = mockAccount(chain = Chain.Ethereum, address = address),
                    ),
                    header = assetText(tether.toGem()),
                    warnings = listOf(
                        "Send only **USDT** on the **Ethereum (ERC20)** network.",
                        "No memo required",
                    ),
                    shareText = address,
                    copyText = addressCopy(Chain.Ethereum.string, address),
                    chainRow = mockGemChainRow(title = "Ethereum"),
                    onSelectNetwork = null,
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("USDT").assertIsDisplayed()
        composeRule.onNodeWithText("Tether").assertDoesNotExist()
        composeRule.onNodeWithText("Send only USDT on the Ethereum (ERC20) network.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No memo required").assertIsDisplayed()
    }

    private fun passTime(millis: Long) {
        if (millis > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
            composeRule.mainClock.advanceTimeBy(millis)
        }
        composeRule.mainClock.advanceTimeByFrame()
    }
}
