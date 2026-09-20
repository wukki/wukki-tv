package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistParserTest {
    @Test
    fun `parses full IPTV metadata and removes normalized duplicates`() {
        val playlist =
            """
            #EXTM3U url-tvg="../epg/guide.xml"
            #EXTINF:-1 tvg-id="m2.hu" tvg-name="M2" tvg-chno="2 HD" group-title="Gyerek, család" tvg-logo="https://example.test/m2.png" tvg-shift="+01:30",M2 / Petőfi TV
            ../live/m2/index.m3u8
            #EXTINF:-1 tvg-id="duplicate",M2 / Petofi TV
            https://media.example/lists/live/m2/index.m3u8
            """.trimIndent()

        val channel = PlaylistParser.parse(playlist, "test", "https://media.example/lists/main/channels.m3u").single()

        assertEquals("M2 / Petőfi TV", channel.name)
        assertEquals("m2.hu", channel.tvgId)
        assertEquals(2, channel.tvgChno)
        assertEquals("Gyerek, család", channel.group)
        assertEquals("https://example.test/m2.png", channel.logo)
        assertEquals(1.5, channel.tvgShiftHours)
        assertEquals("https://media.example/lists/live/m2/index.m3u8", channel.streamUrl)
        assertEquals("https://media.example/lists/epg/guide.xml", PlaylistParser.epgUrl(playlist, "https://media.example/lists/main/channels.m3u"))
    }

    @Test
    fun `recognizes every supported EPG header and ignores unsafe URLs`() {
        assertEquals("https://example.test/one.xml", PlaylistParser.epgUrl("#EXTM3U url-tvg=\"https://example.test/one.xml\""))
        assertEquals("https://example.test/two.xml", PlaylistParser.epgUrl("#EXTM3U x-tvg-url='https://example.test/two.xml'"))
        assertEquals("https://example.test/three.xml", PlaylistParser.epgUrl("#EXTM3U tvg-url=https://example.test/three.xml"))
        assertNull(PlaylistParser.epgUrl("#EXTM3U url-tvg=\"file:///private/guide.xml\""))
    }

    @Test
    fun `does not mistake an HLS manifest for an IPTV channel list`() {
        val manifest =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment-1.ts
            """.trimIndent()

        assertTrue(PlaylistParser.parse(manifest, "test").isEmpty())
    }

    @Test
    fun `normalization and channel IDs are identical on every target`() {
        assertEquals("arvizturo tukorfurogep", platformNormalize("Árvíztűrő TÜKÖRFÚRÓGÉP"))
        assertEquals(
            "c8d386c9-700e-3927-81c7-6ed55d3c27fb",
            stableChannelId("RTL|https://example.test/live.m3u8"),
        )
        assertEquals(
            "10634999-cabc-3da4-82d8-6234492bf1a8",
            stableChannelId("M2 Petőfi|https://example.test/élő.m3u8"),
        )
    }
}
