package hu.wukki.tv

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same schema is persisted in Android DataStore as JSON. */
class AndroidStateSerializationTest {
    @Test
    fun `state survives android json round trip`() {
        val original = AppState(
            playlists = listOf(PlaylistDefinition("wukki", "Wukki TV", "https://example.test/list.m3u", PlaylistSource.URL, 42L)),
            channels = listOf(Channel("rtl", "wukki", "RTL", "https://example.test/rtl.m3u8", "rtl", "RTL", 1, "News", null, true, "rtl", "epg")),
            programmes = listOf(Programme("rtl", "Híradó", 100L, 200L, "Leírás", "https://example.test/news.jpg")),
            lastChannelId = "rtl",
            settings = AppSettings(playback = PlaybackSettings(autoPlayOnLaunch = false), display = DisplaySettings(showProgrammeImages = false))
        )

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        assertEquals(original, json.decodeFromString<AppState>(json.encodeToString(original)))
    }

    @Test
    fun `missing json fields use non-null domain defaults`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        val state = json.decodeFromString<AppState>(
            """{"settings":{"playback":{"autoPlayOnLaunch":null},"display":{"channelListMode":null,"showProgrammeImages":null}},"epgSources":null,"epgProgrammesBySource":null}"""
        )

        assertEquals(AppSettings(), state.settings)
        assertEquals(emptyList(), state.epgSources)
        assertEquals(emptyMap(), state.epgProgrammesBySource)
        assertEquals(true, state.settings.playback.autoPlayOnLaunch)
        assertEquals(ChannelListDisplayMode.NORMAL, state.settings.display.channelListMode)
        assertEquals(true, state.settings.display.showProgrammeImages)
    }
}
