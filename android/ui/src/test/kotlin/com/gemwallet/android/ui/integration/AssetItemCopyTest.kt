package com.gemwallet.android.ui.integration

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.testkit.mockGemAssetItemRow
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.list_item.AssetItemAction
import com.gemwallet.android.ui.components.list_item.AssetListItem
import com.gemwallet.android.ui.models.ListPosition
import com.gemwallet.android.ui.theme.WalletTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import uniffi.gemstone.GemAssetItemTrailing
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class AssetItemCopyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapShowsCopiedFeedbackAndResetsDurationOnRetap() {
        val actions = mutableListOf<AssetItemAction>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WalletTheme {
                AssetListItem(
                    row = mockGemAssetItemRow(title = "USDC", trailing = GemAssetItemTrailing.Copy),
                    listPosition = ListPosition.Single,
                    onAction = { actions += it },
                )
            }
        }

        val copyLabel = composeRule.activity.getString(R.string.common_copy)
        val copiedLabel = composeRule.activity.getString(R.string.common_copied_to_clipboard)

        composeRule.onNodeWithContentDescription(copyLabel).assertIsDisplayed().performClick()
        passTime(0)
        assertEquals(listOf(AssetItemAction.Copy), actions)
        composeRule.onNodeWithContentDescription(copiedLabel).assertIsDisplayed()

        passTime(900)
        composeRule.onNodeWithContentDescription(copiedLabel).assertIsDisplayed().performClick()
        passTime(0)
        assertEquals(listOf(AssetItemAction.Copy, AssetItemAction.Copy), actions)

        passTime(900)
        composeRule.onNodeWithContentDescription(copiedLabel).assertIsDisplayed()

        passTime(700)
        composeRule.onNodeWithContentDescription(copyLabel).assertIsDisplayed()
    }

    private fun passTime(millis: Long) {
        if (millis > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
            composeRule.mainClock.advanceTimeBy(millis)
        }
        composeRule.mainClock.advanceTimeByFrame()
    }
}
