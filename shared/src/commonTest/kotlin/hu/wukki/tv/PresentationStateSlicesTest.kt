package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PresentationStateSlicesTest {
    @Test
    fun `settings update does not rebuild channel directory or EPG snapshot`() {
        val fixture = fixture()
        val directory = fixture.model.filteredChannels()
        val epgVersion = fixture.model.epgContentVersion

        fixture.model.updatePlayback { it.copy(volume = 42) }

        assertSame(directory, fixture.model.filteredChannels())
        assertEquals(epgVersion, fixture.model.epgContentVersion)
        assertEquals(42, fixture.model.settings.playback.volume)
    }

    @Test
    fun `channel and EPG replacements invalidate only their matching presentation slices`() {
        val fixture = fixture()
        val directory = fixture.model.filteredChannels()
        val settings = fixture.model.settings

        fixture.store.update { state ->
            state.copy(channels = state.channels.map { it.copy(name = "RTL Kettő") })
        }

        assertNotSame(directory, fixture.model.filteredChannels())
        assertSame(settings, fixture.model.settings)
        assertEquals(0, fixture.model.epgContentVersion)

        fixture.store.update { state ->
            state.copy(
                epgProgrammesBySource =
                    mapOf(
                        OfficialWukkiSource.EPG_SOURCE_ID to
                            listOf(Programme("rtl.hu", "Híradó", 0L, 3_600_000L)),
                    ),
            )
        }

        assertEquals(1, fixture.model.epgContentVersion)
        assertEquals("Híradó", fixture.model.currentProgram(fixture.model.guideChannels().single(), 1_000L)?.title)
    }

    private fun fixture(): Fixture {
        val source =
            EpgSource(
                id = OfficialWukkiSource.EPG_SOURCE_ID,
                name = "Wukki TV EPG",
                url = "https://example.test/epg.xml",
                managedByPlaylist = true,
            )
        val initial =
            AppState(
                playlists =
                    listOf(
                        PlaylistDefinition(
                            OfficialWukkiSource.PLAYLIST_ID,
                            OfficialWukkiSource.PLAYLIST_NAME,
                            OfficialWukkiSource.PLAYLIST_URL,
                            PlaylistSource.URL,
                            1L,
                        ),
                    ),
                channels =
                    listOf(
                        Channel(
                            id = "rtl",
                            playlistId = OfficialWukkiSource.PLAYLIST_ID,
                            name = "RTL",
                            streamUrl = "https://example.test/rtl.m3u8",
                            tvgId = "rtl.hu",
                            tvgName = "RTL",
                            group = "General",
                            logo = null,
                            epgChannelId = "rtl.hu",
                            epgSourceId = OfficialWukkiSource.EPG_SOURCE_ID,
                        ),
                    ),
                epgSources = listOf(source),
                epgProgrammesBySource = mapOf(OfficialWukkiSource.EPG_SOURCE_ID to emptyList()),
            )
        val store = ApplicationStore(initial, {})
        val application =
            WukkiApplication(
                store,
                RemoteTextLoader { error("No network expected") },
                XmlTvParser { emptyList() },
            )
        return Fixture(store, WukkiModel(application))
    }

    private data class Fixture(
        val store: ApplicationStore,
        val model: WukkiModel,
    )
}
