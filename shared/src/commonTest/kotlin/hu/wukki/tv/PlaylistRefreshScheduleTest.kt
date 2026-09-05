package hu.wukki.tv

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PlaylistRefreshScheduleTest {
    private val hour = 3_600_000L
    private val now = 100 * hour

    private fun model(ageHours: Long, interval: RefreshInterval = RefreshInterval.SIX_HOURS,
                      save: (AppState) -> Unit = {}, loader: RemoteTextLoader): WukkiModel = WukkiModel(
        AppState(
            playlists = listOf(PlaylistDefinition(OfficialWukkiSource.PLAYLIST_ID, "Wukki",
                OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, now - ageHours * hour)),
            settings = AppSettings(playlistRefresh = interval)
        ), loader, XmlTvParser { emptyList() }, save
    )

    @Test
    fun `cold start five hours past the six hour deadline refreshes immediately`() = runBlocking {
        var calls = 0
        val model = model(11, loader = RemoteTextLoader { calls++; error("offline") })
        assertEquals(0L, model.nextPlaylistRefreshDelayMillis(now))
        assertFalse(model.refreshDuePlaylist(now))
        assertEquals(1, calls)
        assertEquals(now - 11 * hour, model.officialPlaylist.updatedAt)
    }

    @Test
    fun `cold start one hour after success waits five hours`() = runBlocking {
        val model = model(1, loader = RemoteTextLoader { error("Must not load") })
        assertEquals(5 * hour, model.nextPlaylistRefreshDelayMillis(now))
        assertTrue(model.refreshDuePlaylist(now))
    }

    @Test
    fun `manual mode never invokes loader even when overdue`() = runBlocking {
        val model = model(99, RefreshInterval.MANUAL, loader = RemoteTextLoader { error("Must not load") })
        assertEquals(Long.MAX_VALUE, model.nextPlaylistRefreshDelayMillis(now))
        assertTrue(model.refreshDuePlaylist(now))
    }

    @Test
    fun `manual success persists a new deadline that survives restart`() = runBlocking {
        var saved = AppState()
        val loader = RemoteTextLoader { "#EXTM3U\n#EXTINF:-1 tvg-id=\"rtl\",RTL\nhttps://example.test/live.m3u8" }
        val model = model(11, save = { saved = it }, loader = loader)
        assertTrue(model.refreshOfficialPlaylist())
        val success = saved.playlists.single().updatedAt
        val restarted = WukkiModel(saved, loader, XmlTvParser { emptyList() }, {})
        assertEquals(6 * hour, restarted.nextPlaylistRefreshDelayMillis(success))
        assertEquals(5 * hour, restarted.nextPlaylistRefreshDelayMillis(success + hour))
    }

    @Test
    fun `all automatic intervals use success time and exact deadline is due`() {
        for (interval in listOf(RefreshInterval.SIX_HOURS, RefreshInterval.TWELVE_HOURS, RefreshInterval.DAILY)) {
            assertEquals((interval.hours - 1) * hour, playlistRefreshDelayMillis(now - hour, interval, now))
            assertEquals(0L, playlistRefreshDelayMillis(now - interval.hours * hour, interval, now))
            assertEquals(0L, playlistRefreshDelayMillis(0L, interval, now))
        }
    }
}
