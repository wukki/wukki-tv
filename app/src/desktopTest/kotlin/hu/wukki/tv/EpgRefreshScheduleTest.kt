package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpgRefreshScheduleTest {
    private val now = 1_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun `never refreshed enabled source is immediately due`() {
        val source = EpgSource("source", "EPG", "https://example.test/epg.xml")

        assertTrue(source.isEpgRefreshDue(RefreshInterval.TWELVE_HOURS, now))
    }

    @Test
    fun `fresh and disabled sources are not due`() {
        val fresh = EpgSource("fresh", "EPG", "https://example.test/fresh.xml", lastUpdatedAt = now - 5 * hour)
        val disabled = EpgSource("disabled", "EPG", "https://example.test/disabled.xml", enabled = false)

        assertFalse(fresh.isEpgRefreshDue(RefreshInterval.SIX_HOURS, now))
        assertFalse(disabled.isEpgRefreshDue(RefreshInterval.SIX_HOURS, now))
    }

    @Test
    fun `next check follows the earliest enabled source due time`() {
        val sources = listOf(
            EpgSource("first", "EPG 1", "https://example.test/1.xml", lastUpdatedAt = now - 2 * hour),
            EpgSource("second", "EPG 2", "https://example.test/2.xml", lastUpdatedAt = now - hour)
        )

        assertEquals(4 * hour, nextEpgRefreshDelayMillis(sources, RefreshInterval.SIX_HOURS, now))
    }

    @Test
    fun `manual mode never schedules a refresh`() {
        assertEquals(Long.MAX_VALUE, nextEpgRefreshDelayMillis(emptyList(), RefreshInterval.MANUAL, now))
    }

    @Test
    fun `missing channel list mode normalizes to normal`() {
        val state = AppState(settings = AppSettings(display = DisplaySettings(channelListMode = null))).normalized()

        assertEquals(ChannelListDisplayMode.NORMAL, state.settings?.display?.channelListMode)
    }
}
