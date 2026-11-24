package com.gemwallet.android.ui.integration

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.ui.components.AvatarPickerScene
import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.BoringAvatar
import com.gemwallet.android.ui.components.image.BoringAvatarPalette
import com.gemwallet.android.ui.components.image.BoringAvatarVariant
import com.gemwallet.android.ui.components.image.BoringPaletteMode
import com.gemwallet.android.ui.components.image.StoredAvatarInfo
import com.gemwallet.android.ui.components.image.StoredAvatarSelection
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.theme.WalletTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "w411dp-h891dp")
class AvatarPickerSceneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun walletModeSwitchesTabsPalettesAndSelectsAvatars() {
        val seed = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA"
        var selectedBoring: BoringAvatar? = null
        var selectedPreset: AvatarPreset? = null
        var removed = false

        composeRule.setContent {
            WalletTheme {
                AvatarPickerScene(
                    heroImage = ListItemImage.Initials("GW"),
                    addressSeed = seed,
                    onClose = {},
                    onRemove = { removed = true },
                    onSelectBoring = { selectedBoring = it },
                    onSelectPreset = { selectedPreset = it },
                    nftContent = { Text(text = "NFT", modifier = Modifier.testTag("nft_body")) },
                )
            }
        }

        composeRule.onNodeWithTag("avatar_reset_action").assertIsDisplayed().performClick()
        assertEquals(true, removed)

        composeRule.onNodeWithTag("avatar_tab_unique").assertIsDisplayed()
        composeRule.onNodeWithTag("avatar_tab_presets").assertIsDisplayed()
        composeRule.onNodeWithTag("avatar_tab_nft").assertIsDisplayed()

        composeRule.onNodeWithTag("avatar_palette_${BoringPaletteMode.Obsidian.name}").performClick()
        composeRule.onNodeWithTag("avatar_boring_${BoringAvatarVariant.Bauhaus.name}").assertIsDisplayed().performClick()
        assertEquals(BoringAvatar(seed, BoringAvatarVariant.Bauhaus, BoringAvatarPalette.Obsidian), selectedBoring)

        composeRule.onNodeWithTag("avatar_tab_presets").performClick()
        composeRule.onNodeWithTag("avatar_preset_${AvatarPreset.GeoCobaltSplit.name}").assertIsDisplayed().performClick()
        assertEquals(AvatarPreset.GeoCobaltSplit, selectedPreset)

        composeRule.onNodeWithTag("avatar_tab_nft").performClick()
        composeRule.onNodeWithTag("nft_body").assertIsDisplayed()
    }

    @Test
    fun contactWithoutAddressShowsPresetsDirectlyWithoutTabs() {
        var selectedPreset: AvatarPreset? = null

        composeRule.setContent {
            WalletTheme {
                AvatarPickerScene(
                    heroImage = ListItemImage.Initials("AL"),
                    addressSeed = null,
                    onClose = {},
                    onSelectBoring = {},
                    onSelectPreset = { selectedPreset = it },
                )
            }
        }

        composeRule.onNodeWithTag("avatar_tab_unique").assertDoesNotExist()
        composeRule.onNodeWithTag("avatar_preset_${AvatarPreset.GeoMonochrome.name}").assertIsDisplayed().performClick()
        assertEquals(AvatarPreset.GeoMonochrome, selectedPreset)
    }

    @Test
    fun preloadedStoredPresetStartsDirectlyOnPresetsTab() {
        composeRule.setContent {
            WalletTheme {
                AvatarPickerScene(
                    heroImage = ListItemImage.Stored("saved_preset.png"),
                    addressSeed = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA",
                    storedAvatarInfo = StoredAvatarInfo(
                        selection = StoredAvatarSelection.Preset(AvatarPreset.GeoVioletPrism),
                        dominantColor = AvatarPreset.GeoVioletPrism.accentColor,
                    ),
                    onClose = {},
                    onSelectBoring = {},
                    onSelectPreset = {},
                )
            }
        }

        composeRule.onNodeWithTag("avatar_preset_${AvatarPreset.GeoVioletPrism.name}").assertIsDisplayed()
    }
}
