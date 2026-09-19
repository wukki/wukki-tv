package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreDomainTest {
    @Test
    fun `EPG matching and history are platform independent`() {
        val channel =
            Channel(
                id = "m1",
                playlistId = "official",
                name = "Műsor Egy",
                streamUrl = "https://example.test/live.m3u8",
                tvgId = null,
                tvgName = null,
                group = "Teszt",
                logo = null,
            )
        val matched = EpgMatcher.match(listOf(channel), listOf(Programme("musor egy", "Hírek", 1, 2))).single()

        assertEquals("musor egy", matched.epgChannelId)
        assertEquals(listOf("m1"), normalizedChannelHistory(listOf("missing", "m1", "m1"), listOf(channel)))
    }
}
