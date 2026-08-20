package hu.wukki.tv

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialWukkiSourceTest {
    @Test
    fun `provisioning removes custom sources and retains official cached state`() {
        val officialChannel = channel(id = "legacy-rtl", playlistId = "legacy-wukki", favorite = true, epgSourceId = "legacy-epg")
        val customChannel = channel(id = "custom", playlistId = "custom-list", name = "Custom")
        val programme = Programme("rtl", "Híradó", 1_000, 2_000)
        val state = AppState(
            playlists = listOf(
                PlaylistDefinition("legacy-wukki", "Old Wukki", OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, 12),
                PlaylistDefinition("custom-list", "Custom", "https://example.test/list.m3u", PlaylistSource.URL, 14)
            ),
            channels = listOf(officialChannel, customChannel),
            lastChannelId = officialChannel.id,
            epgSources = listOf(
                EpgSource("legacy-epg", "Old EPG", "https://example.test/guide.xml", managedByPlaylist = true),
                EpgSource("custom-epg", "Custom EPG", "https://example.test/custom.xml")
            ),
            epgProgrammesBySource = mapOf("legacy-epg" to listOf(programme), "custom-epg" to listOf(programme))
        )

        val migrated = OfficialWukkiSource.provision(state, now = 99)

        assertEquals(listOf(OfficialWukkiSource.PLAYLIST_ID), migrated.playlists.map { it.id })
        assertEquals(OfficialWukkiSource.PLAYLIST_URL, migrated.playlists.single().location)
        assertEquals(listOf(officialChannel.id), migrated.channels.map { it.id })
        assertEquals(OfficialWukkiSource.PLAYLIST_ID, migrated.channels.single().playlistId)
        assertTrue(migrated.channels.single().favorite)
        assertEquals(officialChannel.id, migrated.lastChannelId)
        assertEquals(listOf(OfficialWukkiSource.EPG_SOURCE_ID), migrated.epgSources.orEmpty().map { it.id })
        assertEquals(listOf(programme), migrated.epgProgrammesBySource.orEmpty()[OfficialWukkiSource.EPG_SOURCE_ID])
    }

    @Test
    fun `refresh preserves favourites and last channel while replacing the fixed source`() = runBlocking {
        val legacy = channel(id = "legacy-rtl", playlistId = "legacy-wukki", favorite = true)
        val initial = AppState(
            playlists = listOf(PlaylistDefinition("legacy-wukki", "Wukki", OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, 1)),
            channels = listOf(legacy),
            lastChannelId = legacy.id
        )
        val loader = RemoteTextLoader { url ->
            when (url) {
                OfficialWukkiSource.PLAYLIST_URL -> m3u("https://epg.example/guide.xml")
                "https://epg.example/guide.xml" -> xml("rtl", "Híradó")
                else -> error("Unexpected URL: $url")
            }
        }
        val model = WukkiModel(initial, loader, stateSaver = {})

        assertTrue(model.refreshOfficialPlaylist())

        val refreshed = model.state.channels.single()
        assertTrue(refreshed.favorite)
        assertEquals(refreshed.id, model.state.lastChannelId)
        assertEquals(OfficialWukkiSource.EPG_SOURCE_ID, refreshed.epgSourceId)
        assertEquals("rtl", refreshed.epgChannelId)
        assertEquals("https://epg.example/guide.xml", model.officialEpgSource?.url)
    }

    @Test
    fun `new EPG URL replaces the old cache and missing EPG clears it`() = runBlocking {
        var playlist = m3u("https://epg.example/first.xml")
        val loader = RemoteTextLoader { url ->
            when (url) {
                OfficialWukkiSource.PLAYLIST_URL -> playlist
                "https://epg.example/first.xml" -> xml("rtl", "Első")
                "https://epg.example/second.xml" -> xml("rtl", "Második")
                else -> error("Unexpected URL: $url")
            }
        }
        val model = WukkiModel(AppState(), loader, stateSaver = {})

        assertTrue(model.refreshOfficialPlaylist())
        playlist = m3u("https://epg.example/second.xml")
        assertTrue(model.refreshOfficialPlaylist())
        assertEquals("https://epg.example/second.xml", model.officialEpgSource?.url)
        assertEquals("Második", model.state.programmes.single().title)

        playlist = m3u(null)
        assertTrue(model.refreshOfficialPlaylist())
        assertNull(model.officialEpgSource)
        assertTrue(model.state.programmes.isEmpty())
        assertEquals("error.wukki.epg.missing", (model.error as? UserMessage.Key)?.key)
    }

    @Test
    fun `failed fixed playlist refresh retains the cache`() = runBlocking {
        val cached = channel(id = "cached-rtl", playlistId = OfficialWukkiSource.PLAYLIST_ID)
        val initial = AppState(
            playlists = listOf(PlaylistDefinition(OfficialWukkiSource.PLAYLIST_ID, OfficialWukkiSource.PLAYLIST_NAME, OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, 1)),
            channels = listOf(cached)
        )
        val model = WukkiModel(initial, RemoteTextLoader { error("offline") }, stateSaver = {})

        assertFalse(model.refreshOfficialPlaylist(showFeedback = false))
        assertEquals(listOf(cached.id), model.state.channels.map { it.id })
    }

    @Test
    fun `channel matching prefers tvg id then normalized name`() {
        assertTrue(OfficialWukkiSource.sameChannel(channel("one", "list", tvgId = "RTL.HU"), channel("two", "list", tvgId = "rtl.hu")))
        assertTrue(OfficialWukkiSource.sameChannel(channel("one", "list", name = "RTL Kettő"), channel("two", "list", name = "RTL Ketto")))
    }

    private fun channel(
        id: String,
        playlistId: String,
        name: String = "RTL",
        tvgId: String? = "rtl",
        favorite: Boolean = false,
        epgSourceId: String? = null
    ) = Channel(
        id = id,
        playlistId = playlistId,
        name = name,
        streamUrl = "https://stream.example/$id.m3u8",
        tvgId = tvgId,
        tvgName = name,
        tvgChno = 1,
        group = "News",
        logo = null,
        favorite = favorite,
        epgSourceId = epgSourceId,
        epgChannelId = epgSourceId?.let { "rtl" }
    )

    private fun m3u(epgUrl: String?): String = buildString {
        append("#EXTM3U")
        epgUrl?.let { append(" url-tvg=\"").append(it).append("\"") }
        appendLine()
        appendLine("#EXTINF:-1 tvg-id=\"rtl\" tvg-name=\"RTL\" tvg-chno=\"1\" group-title=\"News\",RTL")
        appendLine("https://stream.example/rtl.m3u8")
    }

    private fun xml(channelId: String, title: String): String = """
        <tv>
          <programme channel="$channelId" start="20260820180000 +0000" stop="20260820190000 +0000">
            <title>$title</title>
          </programme>
        </tv>
    """.trimIndent()
}
