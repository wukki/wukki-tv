package hu.wukki.tv.ui.navigation

import hu.wukki.tv.ui.settings.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppRemoteReducerTest {
    @Test
    fun `three digits then confirm selects exactly the entered channel number`() {
        var state = AppRemoteState(overlayVisible = true, preview = LiveChannelPreviewState("old"))
        for (digit in listOf("1", "2", "3")) {
            val next = state.reduce(AppRemoteKey(digit = digit))
            assertTrue(next.handled)
            state = next.state
        }
        assertEquals("123", state.number)
        assertFalse(state.overlayVisible)
        assertFalse(state.preview.isActive)
        val confirmed = state.reduce(AppRemoteKey(remote = RemoteKey.CONFIRM))
        assertEquals("", confirmed.state.number)
        assertEquals(listOf(AppRemoteEffect.ResetExit, AppRemoteEffect.SelectNumber("123")), confirmed.effects)
    }

    @Test
    fun `second back from live overlay requests exit`() {
        var state = AppRemoteState(overlayVisible = true, preview = LiveChannelPreviewState("one"))
        val overlay = state.reduce(AppRemoteKey(back = true))
        assertEquals(listOf(AppRemoteEffect.Back(AppBackNavigationEffect.DISMISS_LIVE_OVERLAY)), overlay.effects)
        assertFalse(overlay.state.overlayVisible)
        assertFalse(overlay.state.preview.isActive)
        assertEquals(TvFocusZone.MAIN_NAVIGATION, overlay.state.focus)
        val exit = overlay.state.reduce(AppRemoteKey(back = true))
        assertFalse(exit.handled)
        assertTrue(exit.effects.isEmpty())
    }

    @Test
    fun `second back from every non-live section activates live`() {
        DashboardSection.entries.filterNot { it == DashboardSection.LIVE }.forEach { section ->
            val first = AppRemoteState(section = section).reduce(AppRemoteKey(back = true))
            assertEquals(TvFocusZone.MAIN_NAVIGATION, first.state.focus)

            val second = first.state.reduce(AppRemoteKey(back = true))

            assertTrue(second.handled)
            assertEquals(DashboardSection.LIVE, second.state.section)
            assertEquals(listOf(AppRemoteEffect.ActivateSection(DashboardSection.LIVE)), second.effects)
        }
    }

    @Test
    fun `closing non-live transient UI counts as first back`() {
        val states =
            listOf(
                AppRemoteState(section = DashboardSection.GUIDE, dialogVisible = true),
                AppRemoteState(section = DashboardSection.CHANNELS, searchOpen = true),
                AppRemoteState(
                    section = DashboardSection.SETTINGS,
                    settings = SettingsNavigationState(section = SettingsSection.PLAYBACK),
                ),
            )
        states.forEach { state ->
            val first = state.reduce(AppRemoteKey(back = true))
            assertEquals(TvFocusZone.MAIN_NAVIGATION, first.state.focus)
            val second = first.state.reduce(AppRemoteKey(back = true))
            assertEquals(listOf(AppRemoteEffect.ActivateSection(DashboardSection.LIVE)), second.effects)
        }
    }

    @Test
    fun `back first reveals hidden live navigation preserving existing priority`() {
        val result = AppRemoteState(navigationVisible = false, overlayVisible = true).reduce(AppRemoteKey(back = true))
        assertEquals(listOf(AppRemoteEffect.RevealNavigation), result.effects)
        assertTrue(result.state.navigationVisible)
        assertTrue(result.state.overlayVisible)
        assertEquals(TvFocusZone.MAIN_NAVIGATION, result.state.focus)
    }

    @Test
    fun `exit confirmation uses supplied time and second back propagates to platform`() {
        val state = AppRemoteState(overlayVisible = true, requireDoubleBack = true, nowMillis = 100L)
        val first = state.reduce(AppRemoteKey(back = true))
        assertTrue(first.handled)
        assertEquals(
            listOf(
                AppRemoteEffect.Back(AppBackNavigationEffect.DISMISS_LIVE_OVERLAY),
                AppRemoteEffect.ShowExitHint,
            ),
            first.effects,
        )
        assertFalse(
            first.state
                .copy(nowMillis = 200L)
                .reduce(AppRemoteKey(back = true))
                .handled,
        )
    }

    @Test
    fun `settings left and right adjust volume without changing the selected option`() {
        val state =
            AppRemoteState(
                section = DashboardSection.SETTINGS,
                settings =
                    SettingsNavigationState(
                        section = SettingsSection.PLAYBACK,
                        option = PlaybackSettingsOption.VOLUME,
                    ),
            )
        for ((key, delta) in listOf(RemoteKey.LEFT to -1, RemoteKey.RIGHT to 1)) {
            val result = state.reduce(AppRemoteKey(remote = key))
            assertEquals(state.settings, result.state.settings)
            assertTrue(result.handled)
            assertTrue(AppRemoteEffect.Settings(SettingsNavigationEffect.Adjust(PlaybackSettingsOption.VOLUME, delta)) in result.effects)
        }
    }

    @Test
    fun `dpad previews while page up immediately switches even from menu focus`() {
        val state = AppRemoteState(channelIds = listOf("one", "two"), selectedChannelId = "one")
        val first = state.reduce(AppRemoteKey(remote = RemoteKey.UP, preview = LiveChannelPreviewEvent.NEXT))
        assertEquals("one", first.state.preview.channelId)
        val next = first.state.copy(overlayVisible = true).reduce(AppRemoteKey(remote = RemoteKey.UP, preview = LiveChannelPreviewEvent.NEXT))
        assertEquals("two", next.state.preview.channelId)
        val page = state.copy(focus = TvFocusZone.MAIN_NAVIGATION).reduce(AppRemoteKey(remote = RemoteKey.UP, channelDelta = 1))
        assertTrue(AppRemoteEffect.SwitchChannel(1) in page.effects)
    }

    @Test
    fun `search backspace passes through and escape closes search`() {
        val state =
            AppRemoteState(
                section = DashboardSection.CHANNELS,
                searchOpen = true,
                channels = ChannelNavigationState(ChannelRemoteFocus.SEARCH, 0, 0),
            )
        assertFalse(state.reduce(AppRemoteKey(back = true, backspace = true)).handled)
        val escape = state.reduce(AppRemoteKey(back = true, escape = true))
        assertFalse(escape.state.searchOpen)
        assertEquals(ChannelRemoteFocus.LIST, escape.state.channels.focus)
    }
}
