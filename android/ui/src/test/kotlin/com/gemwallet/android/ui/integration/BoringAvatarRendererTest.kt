package com.gemwallet.android.ui.integration

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.ui.components.image.BoringAvatar
import com.gemwallet.android.ui.components.image.BoringAvatarPalette
import com.gemwallet.android.ui.components.image.BoringAvatarRenderer
import com.gemwallet.android.ui.components.image.BoringAvatarVariant
import com.gemwallet.android.ui.components.image.BoringPaletteMode
import com.gemwallet.android.ui.components.image.StoredAvatarSelection
import com.gemwallet.android.ui.components.image.inspectStoredAvatar
import com.gemwallet.android.ui.components.image.walletAvatarSeed
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoringAvatarRendererTest {

    @Test
    fun walletAvatarSeed_extractsAddressFromEveryWalletIdFormat() {
        val address = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA"
        assertEquals(address, walletAvatarSeed("multicoin_$address"))
        assertEquals(address, walletAvatarSeed("single_ethereum_$address"))
        assertEquals(address, walletAvatarSeed("privateKey_ethereum_$address"))
        assertEquals(address, walletAvatarSeed("view_ethereum_$address"))
        assertNull(walletAvatarSeed("invalid"))
    }

    @Test
    fun mixPaletteMode_assignsDistinctDeterministicPalettesAcrossVariants() {
        val seed = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA"
        val firstRun = BoringAvatarVariant.entries.indices.map { BoringPaletteMode.Mix.paletteFor(seed, it) }
        val secondRun = BoringAvatarVariant.entries.indices.map { BoringPaletteMode.Mix.paletteFor(seed, it) }

        assertEquals(firstRun, secondRun)
        assertEquals(BoringAvatarVariant.entries.size, firstRun.toSet().size)
        assertEquals(BoringAvatarPalette.Obsidian, BoringPaletteMode.Obsidian.paletteFor(seed, 0))
    }

    @Test
    fun render_producesDeterministicCircularPngsForAllFiveVariants() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seed = "0x39B70628bf57Ee6b8CCA6b67C47da7934C1489DA"
        val hashes = HashSet<Int>(BoringAvatarVariant.entries.size)

        for ((index, variant) in BoringAvatarVariant.entries.withIndex()) {
            val avatar = BoringAvatar(
                seed = seed,
                variant = variant,
                palette = BoringPaletteMode.Mix.paletteFor(seed, index),
                paletteMode = BoringPaletteMode.Mix,
            )
            val pngBytes = BoringAvatarRenderer.render(context, avatar)
            val repeatBytes = BoringAvatarRenderer.render(context, avatar)
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            val tempFile = File.createTempFile("boring_${variant.name}", ".png", context.cacheDir)
            val storedInfo = try {
                tempFile.writeBytes(pngBytes)
                inspectStoredAvatar(tempFile)
            } finally {
                tempFile.delete()
            }

            assertArrayEquals(pngBytes, repeatBytes)
            assertEquals(
                StoredAvatarSelection.Boring(variant, avatar.palette, BoringPaletteMode.Mix),
                storedInfo.selection,
            )
            assertEquals(avatar.palette.accentColor, storedInfo.dominantColor)
            assertNotNull(bitmap)
            assertTrue(bitmap.width >= 256)
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
            hashes.add(pngBytes.contentHashCode())
        }

        assertEquals(BoringAvatarVariant.entries.size, hashes.size)
    }
}
