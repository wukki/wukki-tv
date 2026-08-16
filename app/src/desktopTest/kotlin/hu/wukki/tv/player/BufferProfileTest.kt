package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class BufferProfileTest {
    @Test
    fun `profiles map to progressively larger VLC network caches`() {
        assertEquals(":network-caching=300", BufferProfile.LOW_LATENCY.vlcOption())
        assertEquals(":network-caching=1000", BufferProfile.BALANCED.vlcOption())
        assertEquals(":network-caching=3000", BufferProfile.STABLE.vlcOption())
    }
}
