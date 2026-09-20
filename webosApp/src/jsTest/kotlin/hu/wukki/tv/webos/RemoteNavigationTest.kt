package hu.wukki.tv.webos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteNavigationTest {
    @Test
    fun `channel navigation wraps in both directions`() {
        assertEquals(2, nextChannelIndex(current = 0, channelCount = 3, step = -1))
        assertEquals(0, nextChannelIndex(current = 2, channelCount = 3, step = 1))
    }

    @Test
    fun `held remote key is rate limited without suppressing a fresh press`() {
        assertTrue(shouldHandleChannelSwitch(repeated = false, nowMs = 10.0, lastSwitchMs = 0.0))
        assertFalse(shouldHandleChannelSwitch(repeated = true, nowMs = 349.0, lastSwitchMs = 0.0))
        assertTrue(shouldHandleChannelSwitch(repeated = true, nowMs = 350.0, lastSwitchMs = 0.0))
    }
}
