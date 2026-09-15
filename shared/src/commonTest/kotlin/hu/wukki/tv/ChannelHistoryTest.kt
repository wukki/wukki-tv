package hu.wukki.tv

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelHistoryTest {
    private val channels =
        (1..12).map { id ->
            Channel("$id", OfficialWukkiSource.PLAYLIST_ID, "Channel $id", "https://example.test/$id", "$id", null, group = "News", logo = null)
        }

    private fun initial() =
        AppState(
            playlists = listOf(PlaylistDefinition(OfficialWukkiSource.PLAYLIST_ID, "Wukki", OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, 0L)),
            channels = channels,
        )

    private fun model(
        state: AppState = initial(),
        save: (AppState) -> Unit = {},
    ) = WukkiModel(
        state,
        RemoteTextLoader { error("No network") },
        XmlTvParser { emptyList() },
        save,
    )

    @Test
    fun `history is newest first unique bounded and persisted`() {
        var saved = initial()
        val model = model(save = { saved = it })
        channels.forEach { model.markChannelPlaybackSuccessful(it.id) }
        model.markChannelPlaybackSuccessful("5")
        model.markChannelPlaybackSuccessful("5")
        val expected = listOf("5", "12", "11", "10", "9", "8", "7", "6", "4", "3")
        assertEquals(expected, saved.recentChannelIds)
        val reloaded = model(Json.decodeFromString<AppState>(Json.encodeToString(saved)))
        assertEquals(expected, reloaded.state.recentChannelIds)
        assertEquals("5", reloaded.selectedChannelId)
    }

    @Test
    fun `failed opens preserve history and previous toggles successful channels`() {
        val model = model()
        assertFalse(model.selectPreviousChannel())
        model.selectChannel("1")
        model.markChannelPlaybackSuccessful("1")
        model.selectChannel("2")
        model.markChannelPlaybackSuccessful("2")
        model.selectChannel("3") // No success event: opening failed.
        assertEquals(listOf("2", "1"), model.state.recentChannelIds)
        assertEquals("2", model.state.lastChannelId)
        assertTrue(model.selectPreviousChannel())
        assertEquals("2", model.selectedChannelId)
        model.markChannelPlaybackSuccessful("2")
        assertTrue(model.selectPreviousChannel())
        assertEquals("1", model.selectedChannelId)
        model.markChannelPlaybackSuccessful("1")
        assertTrue(model.selectPreviousChannel())
        assertEquals("2", model.selectedChannelId)
    }

    @Test
    fun `legacy json without history migrates last channel once`() {
        val legacy = Json.encodeToString(initial().copy(lastChannelId = "3"))
        assertFalse(legacy.contains("recentChannelIds"))
        val migrated = model(Json.decodeFromString<AppState>(legacy)).state
        assertEquals(listOf("3"), migrated.recentChannelIds)
        assertEquals(migrated, model(migrated).state)
    }

    @Test
    fun `refresh removes missing ids and remaps retained channel identity`() {
        val store = ApplicationStore(initial().copy(lastChannelId = "2", recentChannelIds = listOf("2", "1", "3")), {})
        val repository = StoreChannelRepository(store)
        repository.replace(listOf(channels[0].copy(id = "new-one"), channels[1]))
        assertEquals(listOf("2", "new-one"), store.current.recentChannelIds)
        repository.markPlaybackSuccessful("missing")
        assertEquals(listOf("2", "new-one"), store.current.recentChannelIds)
        repository.replace(emptyList())
        assertEquals(emptyList(), store.current.recentChannelIds)
    }

    @Test
    fun `recent filter is ordered searchable reactive and separate from favorites`() {
        val model = model()
        model.markChannelPlaybackSuccessful("2")
        model.markChannelPlaybackSuccessful("1")
        model.showRecentChannels()
        assertEquals(listOf("1", "2"), model.filteredChannels().map { it.id })
        model.markChannelPlaybackSuccessful("3")
        assertEquals(listOf("3", "1", "2"), model.filteredChannels().map { it.id })
        model.setChannelQuery("Channel 2")
        assertEquals(listOf("2"), model.filteredChannels().map { it.id })
        model.setChannelQuery("")
        model.showFavoriteChannels()
        assertFalse(model.onlyRecent)
        assertTrue(model.filteredChannels().isEmpty())
        model.showRecentChannels()
        model.showAllChannels()
        assertFalse(model.onlyRecent)
        assertEquals(12, model.filteredChannels().size)
    }
}
