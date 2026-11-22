package com.gemwallet.android.ui.icons

import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import java.util.concurrent.ConcurrentHashMap

internal data class VectorCacheKey(
    @param:DrawableRes val resId: Int,
    val themeId: Int,
    val nightMode: Int,
    val layoutDirection: Int,
    val densityDpi: Int,
)

internal class VectorResourceCache<V : Any>(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val cache = object : LinkedHashMap<VectorCacheKey, V>(maxEntries, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VectorCacheKey, V>): Boolean = size > maxEntries
    }

    fun getOrPut(key: VectorCacheKey, load: () -> V): V {
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val loaded = load()
        synchronized(cache) {
            return cache.getOrPut(key) { loaded }
        }
    }

    fun size(): Int = synchronized(cache) { cache.size }

    fun clear() = synchronized(cache) { cache.clear() }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 256
        const val LOAD_FACTOR = 0.75f
    }
}

private val sharedVectorCache = VectorResourceCache<ImageVector>()
private val xmlDrawableCache = ConcurrentHashMap<Int, Boolean>()

private fun isXmlDrawable(resources: Resources, @DrawableRes id: Int): Boolean = xmlDrawableCache.getOrPut(id) {
    val value = TypedValue()
    resources.getValue(id, value, true)
    value.string?.toString()?.endsWith(".xml", ignoreCase = true) == true
}

@Composable
fun cachedVectorResource(@DrawableRes id: Int): ImageVector {
    val theme = LocalContext.current.theme
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val key = VectorCacheKey(
        resId = id,
        themeId = System.identityHashCode(theme),
        nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        layoutDirection = configuration.layoutDirection,
        densityDpi = configuration.densityDpi,
    )
    return remember(key, resources, theme) {
        sharedVectorCache.getOrPut(key) {
            ImageVector.vectorResource(theme = theme, res = resources, resId = id)
        }
    }
}

@Composable
fun cachedDrawablePainter(@DrawableRes id: Int): Painter {
    val resources = LocalResources.current
    return if (isXmlDrawable(resources, id)) {
        rememberVectorPainter(cachedVectorResource(id))
    } else {
        painterResource(id)
    }
}
