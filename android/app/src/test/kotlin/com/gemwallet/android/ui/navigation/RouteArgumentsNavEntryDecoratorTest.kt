package com.gemwallet.android.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.gemwallet.android.ui.models.navigation.RouteArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RouteArgumentsNavEntryDecoratorTest {

    @Test
    fun contentKey_keepsFirstEntryAndDisambiguatesDuplicateOccurrences() {
        val key = TestRoute("asset")
        val entry = NavEntry<NavKey>(key) {}

        val firstOccurrence = entry.withOccurrenceContentKey(key, occurrence = 0)
        assertSame(entry, firstOccurrence)
        assertEquals(entry.contentKey, firstOccurrence.contentKey)
        assertEquals(
            "${entry.contentKey}#1",
            entry.withOccurrenceContentKey(key, occurrence = 1).contentKey,
        )
    }

    @Test
    fun resolveWalletNavEntries_reusesEntriesAcrossPushesIncludingDuplicates() {
        val cache = mutableMapOf<Pair<NavKey, Int>, NavEntry<NavKey>>()
        var providerCalls = 0
        val provider: (NavKey) -> NavEntry<NavKey> = { key ->
            providerCalls++
            NavEntry(key) {}
        }
        val home = TestRoute("home")
        val asset = TestRoute("asset")
        val details = TestRoute("details")

        val initial = resolveWalletNavEntries(listOf(home, asset, asset), cache, provider)
        assertEquals(3, providerCalls)
        assertNotSame(initial[1], initial[2])
        assertEquals("${initial[1].contentKey}#1", initial[2].contentKey)

        val afterPush = resolveWalletNavEntries(listOf(home, asset, asset, details), cache, provider)
        assertEquals(4, providerCalls)
        assertSame(initial[0], afterPush[0])
        assertSame(initial[1], afterPush[1])
        assertSame(initial[2], afterPush[2])
    }

    @Test
    fun resolveWalletNavEntries_evictsPoppedEntriesBeforeRepush() {
        val cache = mutableMapOf<Pair<NavKey, Int>, NavEntry<NavKey>>()
        val provider: (NavKey) -> NavEntry<NavKey> = { key -> NavEntry(key) {} }
        val home = TestRoute("home")
        val asset = TestRoute("asset")

        val first = resolveWalletNavEntries(listOf(home, asset, asset), cache, provider)
        val poppedDuplicate = resolveWalletNavEntries(listOf(home, asset), cache, provider)
        val repushedDuplicate = resolveWalletNavEntries(listOf(home, asset, asset), cache, provider)
        val poppedAll = resolveWalletNavEntries(listOf(home), cache, provider)
        val repushed = resolveWalletNavEntries(listOf(home, asset), cache, provider)

        assertSame(first[0], poppedDuplicate[0])
        assertSame(first[1], poppedDuplicate[1])
        assertSame(first[1], repushedDuplicate[1])
        assertNotSame(first[2], repushedDuplicate[2])
        assertSame(first[0], poppedAll[0])
        assertSame(first[0], repushed[0])
        assertNotSame(first[1], repushed[1])
    }

    @Test
    fun routeArguments_dropsNullValues() {
        assertEquals(
            mapOf("routeArguments" to mapOf(RouteArgument.AssetId.key to "bitcoin")),
            routeArguments(
                RouteArgument.AssetId to "bitcoin",
                RouteArgument.NftAssetId to null,
            ),
        )
    }

    private data class TestRoute(val id: String) : NavKey
}
