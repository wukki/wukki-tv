package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammeIndexTest {
    private val channel =
        Channel(
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
            tvgShiftHours = 1.5,
        )

    @Test
    fun `programmes are indexed case-insensitively sorted once and shifted`() {
        val index =
            ProgrammeIndex(
                mapOf(
                    "epg" to
                        listOf(
                            Programme("rtl.hu", "Later", 3_600_000L, 5_400_000L),
                            Programme("RTL.HU", "Earlier", 0L, 3_600_000L),
                            Programme("other", "Other", 0L, 1_000L),
                        ),
                ),
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

    @Test
    fun `time lookups use sorted shifted boundaries across a multi-day guide`() {
        val index =
            ProgrammeIndex(
                mapOf(
                    "epg" to
                        listOf(
                            Programme("rtl.hu", "Third", 7_200_000L, 10_800_000L),
                            Programme("rtl.hu", "First", 0L, 3_600_000L),
                            Programme("rtl.hu", "Second", 3_600_000L, 7_200_000L),
                        ),
                ),
            )
        val shiftedFirst = index.programmes(channel).first()

        assertEquals("First", index.currentProgramme(channel, 5_400_000L)?.title)
        assertEquals("Second", index.nextProgramme(channel, shiftedFirst)?.title)
        assertEquals(
            listOf("First", "Second"),
            index.programmesFor(channel, 5_400_000L, 12_600_000L).map(Programme::title),
        )
        assertTrue(index.programmesFor(channel, 12_600_000L, 12_600_000L).isEmpty())
        assertEquals(null, index.currentProgramme(channel, 20_000_000L))
    }

    @Test
    fun `large index supports repeated current and range lookups`() {
        val programmes =
            List(14 * 48) { slot ->
                val start = slot * 30L * 60L * 1_000L
                Programme("rtl.hu", "Programme $slot", start, start + 30L * 60L * 1_000L)
            }
        val index = ProgrammeIndex(mapOf("epg" to programmes))

        repeat(10_000) { iteration ->
            val rawTime = (iteration % programmes.size) * 30L * 60L * 1_000L
            assertEquals("Programme ${iteration % programmes.size}", index.currentProgramme(channel.copy(tvgShiftHours = null), rawTime)?.title)
        }
        assertEquals(96, index.programmesFor(channel.copy(tvgShiftHours = null), 5 * 86_400_000L, 7 * 86_400_000L).size)
    }

    @Test
    fun `cache rebuilds only when the immutable source snapshot changes`() {
        val cache = EpgProgrammeCache()
        val firstSources = mapOf("epg" to listOf(Programme("rtl.hu", "First", 0L, 3_600_000L)))
        val secondSources = mapOf("epg" to listOf(Programme("rtl.hu", "Second", 0L, 7_200_000L)))
        val unshifted = channel.copy(tvgShiftHours = null)

        assertEquals("First", cache.currentProgramme(firstSources, unshifted, 1_000L)?.title)
        assertEquals("First", cache.currentProgramme(firstSources, unshifted, 1_000L)?.title)
        assertEquals("Second", cache.currentProgramme(secondSources, unshifted, 1_000L)?.title)
        assertEquals(7_200_000L, cache.latestEnd(secondSources, listOf(unshifted)))
    }
}
