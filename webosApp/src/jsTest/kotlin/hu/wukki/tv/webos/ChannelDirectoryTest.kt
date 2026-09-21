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
        assertEquals(listOf("elo"), filterAndSortChannels(channels, "ELO", "Hírek").map { it.id })
        assertTrue(filterAndSortChannels(channels, "elo", "Gyerek").isEmpty())
    }

    @Test
    fun `channels sort by number then normalized name`() {
        assertEquals(listOf("m2", "elo", "hir", "alfa", "beta"), filterAndSortChannels(channels, "", null).map { it.id })
        assertEquals(listOf("Gyerek", "Hírek"), sortedChannelCategories(channels))
    }

    @Test
    fun `virtual window renders buffered rows only and clamps at the end`() {
        val first = calculateChannelRenderWindow(itemCount = 100, scrollTop = 0.0, viewportHeight = 430)
        val last = calculateChannelRenderWindow(itemCount = 100, scrollTop = 99.0 * VIRTUAL_CHANNEL_ROW_HEIGHT, viewportHeight = 430)

        assertEquals(ChannelRenderWindow(0, 8), first)
        assertEquals(96, last.start)
        assertEquals(100, last.endExclusive)
    }

    private fun channel(
        id: String,
        name: String,
        group: String,
        number: Int?,
    ) = Channel(id, "webos", name, "https://example.test/$id.m3u8", id, null, number, group, null)
}
