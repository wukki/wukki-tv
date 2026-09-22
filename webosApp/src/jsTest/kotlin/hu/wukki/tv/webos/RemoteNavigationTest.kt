package hu.wukki.tv.webos

import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteNavigationTest {
    @Test
    fun `webOS keys map to the shared remote contract`() {
        assertEquals(AppRemoteKey(remote = RemoteKey.LEFT), webOsRemoteKey(37, liveContent = false))
        assertEquals(
            AppRemoteKey(remote = RemoteKey.UP, preview = LiveChannelPreviewEvent.NEXT),
            webOsRemoteKey(38, liveContent = true),
        )
        assertEquals(AppRemoteKey(back = true), webOsRemoteKey(WEBOS_BACK_KEY, liveContent = false))
        assertEquals(AppRemoteKey(previousChannel = true), webOsRemoteKey(WEBOS_RED_KEY, liveContent = true))
        assertEquals(AppRemoteKey(quickSettings = true), webOsRemoteKey(WEBOS_GREEN_KEY, liveContent = true))
        assertEquals(AppRemoteKey(digit = "7"), webOsRemoteKey(55, liveContent = true))
    }

    @Test
    fun `held confirm cannot activate a control twice`() {
        assertEquals(null, webOsRemoteKey(13, liveContent = false, repeated = true))
        assertEquals(AppRemoteKey(remote = RemoteKey.DOWN), webOsRemoteKey(40, liveContent = false, repeated = true))
    }

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
