package hu.wukki.tv.webos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveLayerTimingTest {
    @Test
    fun `navigation and information panel follow independent five second timeouts`() {
        assertEquals(5_000, liveLayerTimeoutMillis(LiveLayer.NAVIGATION))
        assertEquals(5_000, liveLayerTimeoutMillis(LiveLayer.INFORMATION_PANEL))
    }

    @Test
    fun `channel number commits after three seconds while dialogs remain explicit`() {
        assertEquals(3_000, liveLayerTimeoutMillis(LiveLayer.CHANNEL_NUMBER))
        assertNull(liveLayerTimeoutMillis(LiveLayer.DIALOG))
    }
}
