package com.gemwallet.android.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VectorResourceCacheTest {

    @Test
    fun getOrPut_reusesCachedInstanceForMatchingKey() {
        val cache = VectorResourceCache<Any>(maxEntries = 4)
        val key = VectorCacheKey(resId = 1, themeId = 10, nightMode = 16, layoutDirection = 0, densityDpi = 440)
        var loads = 0

        val first = cache.getOrPut(key) {
            loads++
            Any()
        }
        val second = cache.getOrPut(key) {
            loads++
            Any()
        }

        assertSame(first, second)
        assertEquals(1, loads)
        assertEquals(1, cache.size())
    }

    @Test
    fun getOrPut_separatesEntriesByThemeNightModeDirectionAndDensity() {
        val cache = VectorResourceCache<String>(maxEntries = 8)
        val base = VectorCacheKey(resId = 1, themeId = 10, nightMode = 16, layoutDirection = 0, densityDpi = 440)
        val dark = base.copy(nightMode = 32)
        val newTheme = base.copy(themeId = 20)
        val rtl = base.copy(layoutDirection = 1)
        val xxhdpi = base.copy(densityDpi = 480)

        assertEquals("light", cache.getOrPut(base) { "light" })
        assertEquals("dark", cache.getOrPut(dark) { "dark" })
        assertEquals("theme", cache.getOrPut(newTheme) { "theme" })
        assertEquals("rtl", cache.getOrPut(rtl) { "rtl" })
        assertEquals("dpi", cache.getOrPut(xxhdpi) { "dpi" })
        assertEquals(5, cache.size())
    }

    @Test
    fun getOrPut_evictsLeastRecentlyUsedEntryBeyondCapacity() {
        val cache = VectorResourceCache<String>(maxEntries = 2)
        val k1 = VectorCacheKey(resId = 1, themeId = 1, nightMode = 16, layoutDirection = 0, densityDpi = 440)
        val k2 = k1.copy(resId = 2)
        val k3 = k1.copy(resId = 3)
        var loads = 0

        cache.getOrPut(k1) {
            loads++
            "v1"
        }
        cache.getOrPut(k2) {
            loads++
            "v2"
        }
        cache.getOrPut(k1) {
            loads++
            "unexpected"
        }
        cache.getOrPut(k3) {
            loads++
            "v3"
        }

        assertEquals(2, cache.size())
        assertEquals(3, loads)

        cache.getOrPut(k1) {
            loads++
            "unexpected"
        }
        assertEquals(3, loads)

        cache.getOrPut(k2) {
            loads++
            "v2-reloaded"
        }
        assertEquals(4, loads)
    }
}
