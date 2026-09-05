package hu.wukki.tv

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefreshOwnershipTest {
    @Test
    fun `overlapping worker and manual refresh preserve edits made during download`() = runBlocking {
        val playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://example.test/one\n" +
            "#EXTINF:-1 tvg-id=\"two\",Two\nhttps://example.test/two"
        val channels = PlaylistParser.parse(playlist, OfficialWukkiSource.PLAYLIST_ID)
        val initial = AppState(
            playlists = listOf(PlaylistDefinition(OfficialWukkiSource.PLAYLIST_ID, "Wukki",
                OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, 1L)),
            channels = channels,
            settings = AppSettings(playlistRefresh = RefreshInterval.SIX_HOURS)
        )
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var downloads = 0
        val saved = mutableListOf<AppState>()
        val model = WukkiModel(initial, RemoteTextLoader {
            downloads++
            started.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
            playlist
        }, XmlTvParser { emptyList() }, { saved += it }, RefreshService(this))
        val worker = async { model.refreshDuePlaylist() }
        started.await()
        try {
            val manual = async(start = CoroutineStart.UNDISPATCHED) { model.refreshOfficialPlaylist() }
            model.toggleFavorite(channels[1].id)
            model.selectChannel(channels[1].id)
            model.markChannelPlaybackSuccessful(channels[1].id)
            release.countDown()
            assertTrue(worker.await())
            assertTrue(manual.await())
            assertEquals(1, downloads)
            assertEquals(1, saved.count { it.playlists.single().updatedAt != 1L })
            assertTrue(saved.last().channels.single { it.id == channels[1].id }.favorite)
            assertEquals(channels[1].id, saved.last().lastChannelId)
        } finally {
            release.countDown()
        }
    }
}
