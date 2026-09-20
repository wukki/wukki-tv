package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M3uPlaylistParserTest {
    @Test
    fun `parses IPTV metadata and removes duplicate entries`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="m2.hu" tvg-name="M2" tvg-chno="2" group-title="Gyerek" tvg-logo="https://example.test/m2.png",M2 / Petőfi TV
            http://example.test/m2/index.m3u8
            #EXTINF:-1 tvg-id="m2.hu",M2 / Petőfi TV
            http://example.test/m2/index.m3u8
            """.trimIndent()

        val channel = M3uPlaylistParser.parse(playlist, "test").single()

        assertEquals("M2 / Petőfi TV", channel.name)
        assertEquals("m2.hu", channel.tvgId)
        assertEquals(2, channel.tvgChno)
        assertEquals("Gyerek", channel.group)
        assertEquals("https://example.test/m2.png", channel.logo)
        assertTrue(channel.id.startsWith("m3u-"))
    }

    @Test
    fun `does not mistake an HLS media manifest for an IPTV channel list`() {
        val manifest =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment-1.ts
            """.trimIndent()

        assertTrue(M3uPlaylistParser.parse(manifest, "test").isEmpty())
    }
}
