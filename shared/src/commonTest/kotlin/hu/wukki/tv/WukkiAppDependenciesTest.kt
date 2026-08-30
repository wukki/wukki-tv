package hu.wukki.tv

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WukkiAppDependenciesTest {
    @Test
    fun `model created by app dependencies uses injected services`() = runBlocking {
        var savedState: AppState? = null
        val store = object : AppStateStore {
            override fun load(): AppState = AppState()
            override fun save(state: AppState) { savedState = state }
        }
        val dependencies = WukkiAppDependencies(
            stateStore = store,
            remoteTextLoader = RemoteTextLoader { url ->
                when (url) {
                    OfficialWukkiSource.PLAYLIST_URL -> """
                        #EXTM3U url-tvg="https://example.test/guide.xml"
                        #EXTINF:-1 tvg-id="rtl" tvg-name="RTL" tvg-chno="1",RTL
                        https://example.test/rtl.m3u8
                    """.trimIndent()
                    "https://example.test/guide.xml" -> "<tv/>"
                    else -> error("Unexpected URL: $url")
                }
            },
            xmlTvParser = XmlTvParser { listOf(Programme("rtl", "Híradó", 1_000, 2_000)) },
            deviceInfoProvider = DeviceInfoProvider { DeviceInfo("Test", "1", "id", 0, 0) }
        )

        val model = dependencies.createModel()

        assertTrue(model.refreshOfficialPlaylist(showFeedback = false))
        assertEquals("Híradó", model.state.epgProgrammesBySource.orEmpty().values.single().single().title)
        assertEquals(model.state, savedState)
    }
}
