package hu.wukki.tv.ui.navigation

import androidx.compose.ui.input.key.Key
import hu.wukki.tv.ui.settings.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RemoteNavigationTest {
    @Test
    fun `direction keys map to immediate live channel changes`() {
        assertEquals(1, Key.DirectionUp.liveImmediateChannelDelta())
        assertEquals(-1, Key.DirectionDown.liveImmediateChannelDelta())
        assertEquals(null, Key.PageUp.liveImmediateChannelDelta())
    }

    @Test
    fun `page and Android TV channel keys browse the live information panel`() {
        assertEquals(LiveChannelPreviewEvent.NEXT, Key.PageUp.livePreviewEvent())
        assertEquals(LiveChannelPreviewEvent.NEXT, Key.ChannelUp.livePreviewEvent())
        assertEquals(LiveChannelPreviewEvent.PREVIOUS, Key.PageDown.livePreviewEvent())
        assertEquals(LiveChannelPreviewEvent.PREVIOUS, Key.ChannelDown.livePreviewEvent())
        assertEquals(null, Key.DirectionUp.livePreviewEvent())
    }

    @Test
    fun `main menu moves horizontally stays in bounds and activates focused item`() {
        val first = MainMenuNavigationState(0).reduce(RemoteKey.LEFT, 4)
        val last = MainMenuNavigationState(3).reduce(RemoteKey.RIGHT, 4)
        val selected = last.state.reduce(RemoteKey.CONFIRM, 4)

        assertEquals(0, first.state.index)
        assertEquals(3, last.state.index)
        assertEquals(3, assertIs<MainMenuNavigationEffect.Activate>(selected.effect).index)
    }

    @Test
    fun `main menu enters content below it`() {
        val result = MainMenuNavigationState(2).reduce(RemoteKey.DOWN, 4)

        assertEquals(2, result.state.index)
        assertIs<MainMenuNavigationEffect.EnterContent>(result.effect)
    }

    @Test
    fun `channel list moves to filters above first row`() {
        val result = ChannelNavigationState(ChannelRemoteFocus.LIST, 2, 0)
            .reduce(RemoteKey.UP, filterCount = 5, channelCount = 20, searchHasText = false)

        assertEquals(ChannelRemoteFocus.FILTERS, result.state.focus)
        assertEquals(2, result.state.filterIndex)
    }

    @Test
    fun `search keeps directional keys while text exists`() {
        val result = ChannelNavigationState(ChannelRemoteFocus.SEARCH, 0, 0)
            .reduce(RemoteKey.DOWN, filterCount = 3, channelCount = 4, searchHasText = true)

        assertFalse(result.handled)
        assertEquals(ChannelRemoteFocus.SEARCH, result.state.focus)
    }

    @Test
    fun `settings uses typed options and clamps navigation`() {
        val opened = SettingsNavigationState(categoryIndex = SettingsSection.entries.indexOf(SettingsSection.PLAYBACK))
            .reduce(RemoteKey.CONFIRM).state
        val top = opened.reduce(RemoteKey.UP).state
        val adjusted = top.reduce(RemoteKey.RIGHT)

        assertEquals(PlaybackSettingsOption.AUTOPLAY, top.option)
        assertEquals(
            PlaybackSettingsOption.AUTOPLAY,
            assertIs<SettingsNavigationEffect.Adjust>(adjusted.effect).option
        )
    }

    @Test
    fun `settings source refresh is a typed action`() {
        val state = SettingsNavigationState(
            section = SettingsSection.EPG,
            categoryIndex = SettingsSection.entries.indexOf(SettingsSection.EPG),
            option = EpgSettingsOption.REFRESH
        )

        assertEquals(
            EpgSettingsOption.REFRESH,
            assertIs<SettingsNavigationEffect.Activate>(state.reduce(RemoteKey.CONFIRM).effect).option
        )
    }

    @Test
    fun `back closes transient UI before returning to navigation and exiting`() {
        assertEquals(
            AppBackNavigationEffect.DISMISS_GUIDE_DIALOG,
            AppBackNavigationState(true, true, true, true, TvFocusZone.CONTENT).reduce()
        )
        assertEquals(
            AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH,
            AppBackNavigationState(false, true, true, true, TvFocusZone.CONTENT).reduce()
        )
        assertEquals(
            AppBackNavigationEffect.FOCUS_MAIN_NAVIGATION,
            AppBackNavigationState(false, false, false, false, TvFocusZone.CONTENT).reduce()
        )
        assertEquals(
            AppBackNavigationEffect.EXIT_APPLICATION,
            AppBackNavigationState(false, false, false, false, TvFocusZone.MAIN_NAVIGATION).reduce()
        )
    }

    @Test
    fun `about settings exposes every row to remote navigation`() {
        val opened = SettingsNavigationState(
            section = SettingsSection.ABOUT,
            categoryIndex = SettingsSection.entries.indexOf(SettingsSection.ABOUT),
            option = AboutSettingsOption.APPLICATION
        )
        var state = opened
        repeat(9) { state = state.reduce(RemoteKey.DOWN).state }

        assertEquals(AboutSettingsOption.LICENSES, state.option)
        assertEquals(
            AboutSettingsOption.LICENSES,
            assertIs<SettingsNavigationEffect.Activate>(state.reduce(RemoteKey.CONFIRM).effect).option
        )
    }
}
