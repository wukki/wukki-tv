package hu.wukki.tv

import hu.wukki.tv.ui.components.displayCategoryName
import hu.wukki.tv.ui.components.displayName
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelFallbackLocalizationTest {
    @Test
    fun `parser stores stable sentinels for missing channel metadata`() {
        val channel = PlaylistParser.parse(
            """
                #EXTM3U
                #EXTINF:-1,
                https://example.test/live.m3u8
            """.trimIndent(),
            playlistId = "playlist"
        ).single()

        assertEquals(UNKNOWN_CHANNEL_NAME_ID, channel.name)
        assertEquals(OTHER_CATEGORY_ID, channel.group)
    }

    @Test
    fun `fallback names render in the selected UI language`() {
        val channel = channel(name = UNKNOWN_CHANNEL_NAME_ID, group = OTHER_CATEGORY_ID)

        assertEquals("Ismeretlen csatorna", channel.displayName(AppLanguage.HUNGARIAN))
        assertEquals("Unknown channel", channel.displayName(AppLanguage.ENGLISH))
        assertEquals("Egyéb", channel.group.displayCategoryName(AppLanguage.HUNGARIAN))
        assertEquals("Other", channel.group.displayCategoryName(AppLanguage.ENGLISH))
    }

    @Test
    fun `legacy Hungarian cache values migrate once to sentinels`() {
        val original = channel(name = "Ismeretlen csatorna", group = "Egyéb")

        val migrated = AppState(channels = listOf(original)).normalized()
        val migratedChannel = migrated.channels.single()

        assertEquals(original.id, migratedChannel.id)
        assertEquals(UNKNOWN_CHANNEL_NAME_ID, migratedChannel.name)
        assertEquals(OTHER_CATEGORY_ID, migratedChannel.group)
        assertEquals(migrated, migrated.normalized())
    }

    @Test
    fun `model persists the legacy cache migration only on first load`() {
        val loaded = AppState(
            playlists = listOf(
                PlaylistDefinition(
                    id = OfficialWukkiSource.PLAYLIST_ID,
                    name = OfficialWukkiSource.PLAYLIST_NAME,
                    location = OfficialWukkiSource.PLAYLIST_URL,
                    source = PlaylistSource.URL,
                    updatedAt = 123L
                )
            ),
            channels = listOf(
                channel(name = "Ismeretlen csatorna", group = "Egyéb")
                    .copy(playlistId = OfficialWukkiSource.PLAYLIST_ID)
            ),
            settings = AppSettings(),
            epgSources = emptyList(),
            epgProgrammesBySource = emptyMap()
        )
        var saved: AppState? = null
        WukkiModel(
            loaded,
            RemoteTextLoader { error("No network expected") },
            XmlTvParser { emptyList() },
            stateSaver = { saved = it }
        )

        val migrated = checkNotNull(saved)
        assertEquals(UNKNOWN_CHANNEL_NAME_ID, migrated.channels.single().name)
        assertEquals(OTHER_CATEGORY_ID, migrated.channels.single().group)

        var repeatedSaves = 0
        WukkiModel(
            migrated,
            RemoteTextLoader { error("No network expected") },
            XmlTvParser { emptyList() },
            stateSaver = { repeatedSaves++ }
        )
        assertEquals(0, repeatedSaves)
    }

    private fun channel(name: String, group: String) = Channel(
        id = "channel",
        playlistId = "playlist",
        name = name,
        streamUrl = "https://example.test/live.m3u8",
        tvgId = null,
        tvgName = null,
        group = group,
        logo = null
    )
}
