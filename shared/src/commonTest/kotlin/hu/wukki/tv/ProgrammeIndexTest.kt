package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammeIndexTest {
    private val channel = Channel(
        id = "channel-1",
        playlistId = "playlist",
        name = "RTL",
        streamUrl = "https://example.com/live.m3u8",
        tvgId = "rtl.hu",
        tvgName = "RTL",
        group = "General",
        logo = null,
        epgChannelId = "RTL.HU",
        epgSourceId = "epg",
        tvgShiftHours = 1.5
    )

    @Test
    fun `programmes are indexed case-insensitively sorted once and shifted`() {
        val index = ProgrammeIndex(
            mapOf(
                "epg" to listOf(
                    Programme("rtl.hu", "Later", 3_600_000L, 5_400_000L),
                    Programme("RTL.HU", "Earlier", 0L, 3_600_000L),
                    Programme("other", "Other", 0L, 1_000L)
                )
            )
        )

        val programmes = index.programmes(channel)

        assertEquals(listOf("Earlier", "Later"), programmes.map(Programme::title))
        assertEquals(5_400_000L, programmes.first().start)
        assertEquals(10_800_000L, programmes.last().end)
        assertTrue(programmes === index.programmes(channel))
    }

    @Test
    fun `source and channel without a matching EPG entry stay empty`() {
        val index = ProgrammeIndex(mapOf("another-source" to listOf(Programme("rtl.hu", "News", 0L, 1_000L))))

        assertTrue(index.programmes(channel).isEmpty())
    }

    @Test
    fun `latest end uses the shifted programmes visible to channels`() {
        val index = ProgrammeIndex(mapOf("epg" to listOf(Programme("rtl.hu", "News", 0L, 3_600_000L))))

        assertEquals(9_000_000L, index.latestEnd(listOf(channel)))
    }
}
