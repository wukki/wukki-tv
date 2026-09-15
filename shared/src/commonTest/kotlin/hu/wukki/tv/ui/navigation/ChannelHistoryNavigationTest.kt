package hu.wukki.tv.ui.navigation

import androidx.compose.ui.input.key.Key
import hu.wukki.tv.ui.channels.ChannelEmptyAction
import hu.wukki.tv.ui.channels.ChannelEmptyState
import hu.wukki.tv.ui.channels.action
import hu.wukki.tv.ui.channels.channelEmptyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelHistoryNavigationTest {
    @Test
    fun `desktop and media previous keys map to the shared previous command`() {
        assertTrue(Key.F8.toAppRemoteKey().previousChannel)
        assertTrue(Key.MediaPrevious.toAppRemoteKey().previousChannel)
        assertFalse(Key.DirectionLeft.toAppRemoteKey().previousChannel)
    }

    @Test
    fun `previous command works with hidden navigation and does not override dialogs`() {
        val key = AppRemoteKey(previousChannel = true)
        val live = AppRemoteState(navigationVisible = false).reduce(key)
        assertTrue(live.handled)
        assertTrue(AppRemoteEffect.PreviousChannel in live.effects)
        assertFalse(AppRemoteEffect.PreviousChannel in AppRemoteState(dialogVisible = true).reduce(key).effects)
        assertFalse(AppRemoteEffect.PreviousChannel in AppRemoteState(section = DashboardSection.SETTINGS).reduce(key).effects)
    }

    @Test
    fun `empty history offers all channels instead of a blank list`() {
        val state = channelEmptyState(true, 0, "", false, null, false, onlyRecent = true)
        assertEquals(ChannelEmptyState.NO_RECENT, state)
        assertEquals(ChannelEmptyAction.SHOW_ALL, state?.action())
    }
}
