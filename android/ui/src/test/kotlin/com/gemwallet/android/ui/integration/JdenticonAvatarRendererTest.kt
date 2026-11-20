package com.gemwallet.android.ui.integration

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.testkit.mockGemWalletRow
import com.gemwallet.android.ui.components.image.JdenticonAvatarRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JdenticonAvatarRendererTest {

    @Test
    fun render_producesCircularPngsForJdenticonAndBlockiesMatchingWalletAddress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
        val multicoinIcons = mockGemWalletRow(id = "multicoin_$address").identicons()
        val watchOnlyIcons = mockGemWalletRow(id = "view_ethereum_$address").identicons()

        assertEquals(2, multicoinIcons.size)
        assertEquals(multicoinIcons, watchOnlyIcons)

        for (icon in multicoinIcons) {
            val pngBytes = JdenticonAvatarRenderer.render(context, icon)
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)

            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0)
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        }
    }
}
