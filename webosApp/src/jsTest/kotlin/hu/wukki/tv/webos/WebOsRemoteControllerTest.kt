package hu.wukki.tv.webos

import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebOsRemoteControllerTest {
    @Test
    fun `menu changes route only after confirm`() {
        val host = FakeNavigationHost()
        val controller = WebOsRemoteController(host)
        controller.onNavigationFocused(WebOsSection.CHANNELS)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.LEFT)))
        assertEquals(WebOsSection.CHANNELS, host.section)
        assertEquals(WebOsSection.GUIDE, host.focusedNavigation)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.CONFIRM)))
        assertEquals(WebOsSection.GUIDE, host.section)
    }

    @Test
    fun `search keeps caret keys and back clears before changing route`() {
        val host = FakeNavigationHost(searchFocused = true, searchHasText = true)
        val controller = WebOsRemoteController(host)
        controller.onSearchFocused()

        assertFalse(controller.dispatch(AppRemoteKey(remote = RemoteKey.LEFT)))
        assertTrue(controller.dispatch(AppRemoteKey(back = true)))
        assertTrue(host.searchCleared)
        assertEquals(WebOsSection.CHANNELS, host.section)
    }

    @Test
    fun `list favorite action and root double back apply reducer effects`() {
        val host = FakeNavigationHost()
        val controller = WebOsRemoteController(host)
        controller.onChannelFocused(index = 1, favorite = false)

        controller.dispatch(AppRemoteKey(remote = RemoteKey.RIGHT))
        controller.dispatch(AppRemoteKey(remote = RemoteKey.CONFIRM))
        assertEquals(1, host.favoriteIndex)

        host.section = WebOsSection.LIVE
        controller.onSectionActivated(WebOsSection.LIVE)
        controller.onNavigationFocused(WebOsSection.LIVE)
        assertTrue(controller.dispatch(AppRemoteKey(back = true), nowMillis = 1_000))
        assertTrue(host.status.contains("újra"))
        assertTrue(controller.dispatch(AppRemoteKey(back = true), nowMillis = 1_500))
        assertTrue(host.exited)
    }

    @Test
    fun `live preview browses without switching until confirm`() {
        val host = FakeNavigationHost(section = WebOsSection.LIVE)
        val controller = WebOsRemoteController(host)
        controller.onSectionActivated(WebOsSection.LIVE)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.UP, preview = LiveChannelPreviewEvent.NEXT)))
        assertEquals("one", host.previewedChannelId)
        assertEquals(null, host.openedChannelIndex)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.UP, preview = LiveChannelPreviewEvent.NEXT)))
        assertEquals("two", host.previewedChannelId)
        assertEquals(null, host.openedChannelIndex)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.CONFIRM)))
        assertEquals(1, host.openedChannelIndex)
    }

    @Test
    fun `hidden live navigation is revealed before back can leave`() {
        val host = FakeNavigationHost(section = WebOsSection.LIVE, navigationVisible = false)
        val controller = WebOsRemoteController(host)
        controller.onSectionActivated(WebOsSection.LIVE)

        assertTrue(controller.dispatch(AppRemoteKey(back = true)))

        assertTrue(host.navigationVisible)
        assertEquals(WebOsSection.LIVE, host.focusedNavigation)
        assertFalse(host.exited)
    }

    @Test
    fun `channel keys and previous channel bypass preview`() {
        val host = FakeNavigationHost(section = WebOsSection.LIVE)
        val controller = WebOsRemoteController(host)
        controller.onSectionActivated(WebOsSection.LIVE)

        controller.dispatch(AppRemoteKey(channelDelta = 1))
        controller.dispatch(AppRemoteKey(previousChannel = true))

        assertEquals(1, host.channelDelta)
        assertTrue(host.previousOpened)
        assertEquals(null, host.previewedChannelId)
    }

    @Test
    fun `ok and back only show and dismiss the live panel`() {
        val host = FakeNavigationHost(section = WebOsSection.LIVE)
        val controller = WebOsRemoteController(host)
        controller.onSectionActivated(WebOsSection.LIVE)

        assertTrue(controller.dispatch(AppRemoteKey(remote = RemoteKey.CONFIRM)))
        assertTrue(host.overlayVisible)
        assertEquals(null, host.openedChannelIndex)

        assertTrue(controller.dispatch(AppRemoteKey(back = true)))
        assertFalse(host.overlayVisible)
        assertFalse(host.exited)
        assertEquals(null, host.openedChannelIndex)
    }
}

private class FakeNavigationHost(
    var section: WebOsSection = WebOsSection.CHANNELS,
    var searchFocused: Boolean = false,
    var searchHasText: Boolean = false,
    var navigationVisible: Boolean = true,
) : WebOsNavigationHost {
    var focusedNavigation: WebOsSection? = null
    var searchCleared = false
    var favoriteIndex: Int? = null
    var status = ""
    var exited = false
    var overlayVisible = false
    var previewedChannelId: String? = null
    var openedChannelIndex: Int? = null
    var channelDelta: Int? = null
    var previousOpened = false

    override val activeSection: WebOsSection get() = section
    override val visibleChannelIds = listOf("one", "two", "three")
    override val selectedChannelIdForNavigation = "one"
    override val channelSearchHasText: Boolean get() = searchHasText
    override val channelSearchFocused: Boolean get() = searchFocused
    override val channelSearchOpen: Boolean get() = searchFocused || searchHasText
    override val channelFilterCount = 6
    override val liveOverlayVisible: Boolean get() = overlayVisible
    override val liveNavigationVisible: Boolean get() = navigationVisible
    override val dialogVisible = false
    override val activateSection: (WebOsSection) -> Unit = { section = it }
    override val focusNavigation: (WebOsSection) -> Unit = { focusedNavigation = it }
    override val focusSectionContent: (WebOsSection) -> Unit = {}
    override val focusChannelFilter: (Int) -> Unit = {}
    override val focusChannelSearch: () -> Unit = {}
    override val focusChannel: (Int, Boolean) -> Unit = { _, _ -> }
    override val focusSettings: (Int) -> Unit = {}
    override val activateChannelFilter: (Int) -> Unit = {}
    override val activateChannelEmpty: () -> Unit = {}
    override val clearChannelSearch: () -> Unit = {
        searchFocused = false
        searchHasText = false
        searchCleared = true
    }
    override val openChannel: (Int) -> Unit = { openedChannelIndex = it }
    override val toggleFavorite: (Int) -> Unit = { favoriteIndex = it }
    override val previewChannel: (String?) -> Unit = {
        previewedChannelId = it
        overlayVisible = it != null
    }
    override val switchChannel: (Int) -> Unit = { channelDelta = it }
    override val openPreviousChannel: () -> Unit = { previousOpened = true }
    override val selectChannelNumber: (String) -> Unit = {}
    override val showLiveOverlay: () -> Unit = { overlayVisible = true }
    override val hideLiveOverlay: () -> Unit = { overlayVisible = false }
    override val showLiveNavigation: () -> Unit = { navigationVisible = true }
    override val showChannelNumberInput: (String?) -> Unit = {}
    override val showQuickSettings: () -> Unit = {}
    override val closeDialog: () -> Unit = {}
    override val showStatus: (String) -> Unit = { status = it }
    override val exitApplication: () -> Unit = { exited = true }
}
