package com.gemwallet.android.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.ui.models.navigation.RouteArgument
import com.wallet.core.primitives.AssetId

internal fun assetIdArgument(assetId: AssetId): Pair<RouteArgument, String> = RouteArgument.AssetId to assetId.toIdentifier()

internal fun fiatAmountArgument(amount: Int?): Pair<RouteArgument, Int?> = RouteArgument.FiatAmount to amount

internal fun contactIdArgument(contactId: String): Pair<RouteArgument, String> = RouteArgument.ContactId to contactId

internal fun fromAssetIdArgument(assetId: AssetId?): Pair<RouteArgument, String?> = RouteArgument.FromAssetId to assetId?.toIdentifier()

internal fun toAssetIdArgument(assetId: AssetId?): Pair<RouteArgument, String?> = RouteArgument.ToAssetId to assetId?.toIdentifier()

internal fun paramsArgument(params: String): Pair<RouteArgument, String> = RouteArgument.Params to params

internal fun NavEntry<NavKey>.withOccurrenceContentKey(key: NavKey, occurrence: Int): NavEntry<NavKey> {
    if (occurrence == 0) {
        return this
    }
    val uniqueContentKey = "$contentKey#$occurrence"
    val entry = this
    return NavEntry(
        key = key,
        contentKey = uniqueContentKey,
        metadata = metadata,
    ) {
        entry.Content()
    }
}

internal fun resolveWalletNavEntries(
    keys: List<NavKey>,
    cache: MutableMap<Pair<NavKey, Int>, NavEntry<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val occurrences = mutableMapOf<Any, Int>()
    val activeIds = HashSet<Pair<NavKey, Int>>(keys.size)
    val resolved = keys.map { key ->
        val probe = cache[key to 0] ?: entryProvider(key)
        val occurrence = occurrences.getOrDefault(probe.contentKey, 0)
        occurrences[probe.contentKey] = occurrence + 1
        val cacheKey = key to occurrence
        activeIds.add(cacheKey)
        cache.getOrPut(cacheKey) {
            val base = if (occurrence == 0) probe else entryProvider(key)
            base.withOccurrenceContentKey(key, occurrence)
        }
    }
    cache.keys.retainAll(activeIds)
    return resolved
}
