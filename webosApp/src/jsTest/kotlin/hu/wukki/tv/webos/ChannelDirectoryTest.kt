package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelDirectoryTest {
    private val channels =
        listOf(
            channel("hir", "Hír TV", "Hírek", 12),
            channel("m2", "M2 Gyerek", "Gyerek", 2),
            channel("elo", "Élő műsor", "Hírek", 4),
            channel("alfa", "Álfa TV", "Gyerek", null),
            channel("beta", "Béta TV", "Gyerek", null),
        )

    @Test
    fun `search ignores case and Hungarian accents and combines with category`() {
        assertEquals(
            listOf("elo"),
            filterAndSortChannels(channels, "ELO", WebOsChannelFilter.CATEGORY, "Hírek").map { it.id },
        )
        assertTrue(filterAndSortChannels(channels, "elo", WebOsChannelFilter.CATEGORY, "Gyerek").isEmpty())
    }

    @Test
    fun `channels sort by number then normalized name`() {
        assertEquals(listOf("m2", "elo", "hir", "alfa", "beta"), filterAndSortChannels(channels, "").map { it.id })
        assertEquals(listOf("Gyerek", "Hírek"), sortedChannelCategories(channels))
    }

    @Test
    fun `virtual window renders buffered rows only and clamps at the end`() {
        val first = calculateChannelRenderWindow(itemCount = 100, scrollTop = 0.0, viewportHeight = 430)
        val last = calculateChannelRenderWindow(itemCount = 100, scrollTop = 99.0 * 88, viewportHeight = 430)

        assertEquals(ChannelRenderWindow(0, 8), first)
        assertEquals(96, last.start)
        assertEquals(100, last.endExclusive)
    }

    @Test
    fun `favorites and recents use their common ordering rules`() {
        val favoriteChannels = channels.map { channel -> channel.copy(favorite = channel.id in setOf("hir", "alfa")) }

        assertEquals(
            listOf("hir", "alfa"),
            filterAndSortChannels(favoriteChannels, "", WebOsChannelFilter.FAVORITES).map(Channel::id),
        )
        assertEquals(
            listOf("alfa", "m2", "hir"),
            filterAndSortChannels(channels, "", WebOsChannelFilter.RECENT, recentChannelIds = listOf("alfa", "m2", "alfa", "hir")).map(Channel::id),
        )
    }

    @Test
    fun `playlist refresh preserves favorites but drops vanished channels`() {
        val cached = listOf(channel("old", "Old", "Hírek", 1).copy(favorite = true), channel("m2", "M2", "Gyerek", 2).copy(favorite = true))
        val refreshed = listOf(channel("m2-new", "M2", "Gyerek", 2).copy(tvgId = "m2"), channel("fresh", "Fresh", "Hírek", 3))
        val cachedWithStableTvg = cached.map { if (it.id == "m2") it.copy(tvgId = "m2") else it }

        val merged = mergeFavoriteState(refreshed, cachedWithStableTvg)

        assertEquals(listOf("m2-new"), merged.filter(Channel::favorite).map(Channel::id))
        assertTrue(merged.none { it.id == "old" })
    }

    @Test
    fun `all empty states expose the expected recovery`() {
        assertEquals(WebOsChannelEmptyState.NO_DATA, channelEmptyState(false, 0, "", WebOsChannelFilter.ALL, false))
        assertEquals(WebOsChannelEmptyState.LOAD_FAILED, channelEmptyState(false, 0, "", WebOsChannelFilter.ALL, true))
        assertEquals(WebOsChannelEmptyState.NO_SEARCH_RESULTS, channelEmptyState(true, 0, "x", WebOsChannelFilter.FAVORITES, false))
        assertEquals(WebOsChannelEmptyState.NO_FAVORITES, channelEmptyState(true, 0, "", WebOsChannelFilter.FAVORITES, false))
        assertEquals(WebOsChannelEmptyState.NO_RECENT, channelEmptyState(true, 0, "", WebOsChannelFilter.RECENT, false))
        assertEquals(WebOsChannelEmptyState.NO_CATEGORY_RESULTS, channelEmptyState(true, 0, "", WebOsChannelFilter.CATEGORY, false))
        assertEquals(WebOsChannelEmptyAction.CLEAR_SEARCH, WebOsChannelEmptyState.NO_SEARCH_RESULTS.action())
    }

    @Test
    fun `row modes drive virtualization height`() {
        assertEquals(64, channelRowHeight("COMPACT"))
        assertEquals(88, channelRowHeight("NORMAL"))
        assertEquals(120, channelRowHeight("DETAILED"))
        assertEquals(ChannelRenderWindow(7, 17), calculateChannelRenderWindow(30, 10.0 * 120, 480, 120))
    }

    private fun channel(
        id: String,
        name: String,
        group: String,
        number: Int?,
    ) = Channel(id, "webos", name, "https://example.test/$id.m3u8", id, null, number, group, null)
}
