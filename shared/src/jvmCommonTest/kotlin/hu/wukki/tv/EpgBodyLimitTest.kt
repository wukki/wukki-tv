package hu.wukki.tv

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class EpgBodyLimitTest {
    @Test
    fun `oversized EPG reports load error and leaves previous cache untouched`() = runBlocking {
        val cached = listOf(Programme("rtl", "Cached", 1L, 2L))
        val source = EpgSource(
            OfficialWukkiSource.EPG_SOURCE_ID,
            "Wukki TV EPG",
            "https://example.test/guide.xml",
            lastUpdatedAt = 123L,
            managedByPlaylist = true
        )
        val initial = AppState(
            playlists = listOf(PlaylistDefinition(
                OfficialWukkiSource.PLAYLIST_ID,
                OfficialWukkiSource.PLAYLIST_NAME,
                OfficialWukkiSource.PLAYLIST_URL,
                PlaylistSource.URL,
                456L
            )),
            epgSources = listOf(source),
            epgProgrammesBySource = mapOf(source.id to cached)
        )
        var saves = 0
        val model = WukkiModel(
            initial,
            RemoteTextLoader { decodeRemoteText(ByteArrayInputStream(ByteArray(9)), 8) },
            JvmXmlTvParser,
            { saves++ }
        )
        val previousCache = model.state.epgProgrammesBySource
        val savesBeforeRefresh = saves

        assertFalse(model.refreshOfficialEpg())
        assertSame(previousCache, model.state.epgProgrammesBySource)
        assertEquals(123L, model.officialEpgSource?.lastUpdatedAt)
        assertEquals("error.epg.load", (model.error as UserMessage.Key).key)
        assertEquals(savesBeforeRefresh, saves)
    }
}
