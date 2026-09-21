package hu.wukki.tv.webos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppShellTest {
    @Test
    fun `shell routes follow the shared dashboard order`() {
        assertEquals(listOf("live", "guide", "channels", "settings"), webOsSectionOrder.map { it.route })
        assertEquals(WebOsSection.CHANNELS, webOsSection("channels"))
        assertNull(webOsSection("diagnostics"))
    }

    @Test
    fun `view switching cannot replace or resume the current media source`() {
        val source = "https://example.test/live.m3u8"

        assertEquals(PlaybackSourceAction.KEEP_PLAYING, playbackSourceAction(source, source, paused = false))
        assertEquals(PlaybackSourceAction.RESUME, playbackSourceAction(source, source, paused = true))
        assertEquals(PlaybackSourceAction.REPLACE, playbackSourceAction(source, "https://example.test/other.m3u8", paused = false))
    }
}
