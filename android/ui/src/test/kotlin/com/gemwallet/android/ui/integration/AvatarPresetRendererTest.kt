package com.gemwallet.android.ui.integration

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.ui.components.image.AvatarPreset
import com.gemwallet.android.ui.components.image.AvatarPresetRenderer
import com.gemwallet.android.ui.components.image.StoredAvatarSelection
import com.gemwallet.android.ui.components.image.inspectStoredAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvatarPresetRendererTest {

    @Test
    fun render_producesDistinctCircularPngsForAllPresets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(24, AvatarPreset.entries.size)
        val hashes = HashSet<Int>(AvatarPreset.entries.size)

        for (preset in AvatarPreset.entries) {
            val pngBytes = AvatarPresetRenderer.render(context, preset)
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            val tempFile = File.createTempFile("preset_${preset.name}", ".png", context.cacheDir)
            val storedInfo = try {
                tempFile.writeBytes(pngBytes)
                inspectStoredAvatar(tempFile)
            } finally {
                tempFile.delete()
            }

            assertEquals(StoredAvatarSelection.Preset(preset), storedInfo.selection)
            assertEquals(preset.accentColor, storedInfo.dominantColor)
            assertNotNull(bitmap)
            assertTrue(bitmap.width >= 256)
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
            hashes.add(pngBytes.contentHashCode())
        }

        assertEquals(AvatarPreset.entries.size, hashes.size)
    }
}
