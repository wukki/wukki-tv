package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebOsStateStoreTest {
    private val channel = Channel("m2", "webos", "M2", "https://example.test/m2.m3u8", "m2", null, group = "Közszolgálati", logo = null)

    @Test
    fun `versioned state round trip retains cache settings and history`() {
        var stored: String? = null
        val store = WebOsStateStore(read = { stored }, write = { stored = it })
        val expected =
            WebOsStoredState(
                playlistUrl = "https://example.test/list.m3u",
                playlistUpdatedAt = 123L,
                channels = listOf(channel),
                lastChannelId = channel.id,
                recentChannelIds = listOf(channel.id),
                settings = WebOsSettings(autoPlayOnLaunch = false),
            )

        assertNull(store.save(expected))
        assertTrue(stored.orEmpty().contains("\"schemaVersion\":$WEBOS_STATE_SCHEMA_VERSION"))
        val loaded = store.load()
        assertNull(loaded.error)
        assertEquals(expected, loaded.state)
    }

    @Test
    fun `corrupt state remains untouched and reports an error`() {
        var stored = "{broken"
        var writes = 0
        val store =
            WebOsStateStore(
                read = { stored },
                write = {
                    stored = it
                    writes++
                },
            )

        val loaded = store.load()

        assertNull(loaded.state)
        assertNotNull(loaded.error)
        assertEquals("{broken", stored)
        assertEquals(0, writes)
    }

    @Test
    fun `empty or failed save cannot replace the previous snapshot`() {
        var stored = "previous"
        val emptyStore = WebOsStateStore(read = { stored }, write = { stored = it })
        assertNotNull(emptyStore.save(storedState()))
        assertEquals("previous", stored)

        val fullStore = WebOsStateStore(read = { stored }, write = { throw IllegalStateException("quota") })
        assertTrue(fullStore.save(storedState(listOf(channel))).orEmpty().contains("quota"))
        assertEquals("previous", stored)
    }

    private fun storedState(channels: List<Channel> = emptyList()) =
        WebOsStoredState(
            playlistUrl = "https://example.test/list.m3u",
            playlistUpdatedAt = 0L,
            channels = channels,
        )
}
