package hu.wukki.tv.webos

import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.navigation.AppBackNavigationEffect
import hu.wukki.tv.ui.navigation.AppRemoteEffect
import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.AppRemoteState
import hu.wukki.tv.ui.navigation.ChannelNavigationEffect
import hu.wukki.tv.ui.navigation.ChannelNavigationState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEffect
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.RemoteKey
import hu.wukki.tv.ui.navigation.SettingsNavigationEffect
import hu.wukki.tv.ui.navigation.SettingsOptionId
import hu.wukki.tv.ui.navigation.TvFocusZone
import hu.wukki.tv.ui.navigation.options
import hu.wukki.tv.ui.navigation.reduce
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Date

internal interface WebOsNavigationHost {
    val activeSection: WebOsSection
    val visibleChannelIds: List<String>
    val selectedChannelIdForNavigation: String?
    val channelSearchHasText: Boolean
    val channelSearchFocused: Boolean
    val channelSearchOpen: Boolean
    val channelFilterCount: Int
    val liveOverlayVisible: Boolean
    val liveNavigationVisible: Boolean
    val dialogVisible: Boolean
    val settingsDetailOpen: Boolean
    val activateSection: (WebOsSection) -> Unit
    val focusNavigation: (WebOsSection) -> Unit
    val focusSectionContent: (WebOsSection) -> Unit
    val focusChannelFilter: (Int) -> Unit
    val focusChannelSearch: () -> Unit
    val focusChannel: (Int, Boolean) -> Unit
    val focusSettings: (Int) -> Unit
    val openSettingsSection: (SettingsSection) -> Unit
    val closeSettingsSection: () -> Unit
    val focusSettingsOption: (Int) -> Unit
    val adjustSetting: (SettingsOptionId, Int) -> Unit
    val activateSetting: (SettingsOptionId) -> Unit
    val activateChannelFilter: (Int) -> Unit
    val activateChannelEmpty: () -> Unit
    val clearChannelSearch: () -> Unit
    val openChannel: (Int) -> Unit
    val toggleFavorite: (Int) -> Unit
    val previewChannel: (String?) -> Unit
    val switchChannel: (Int) -> Unit
    val openPreviousChannel: () -> Unit
    val selectChannelNumber: (String) -> Unit
    val showLiveOverlay: () -> Unit
    val hideLiveOverlay: () -> Unit
    val showLiveNavigation: () -> Unit
    val showChannelNumberInput: (String?) -> Unit
    val showQuickSettings: () -> Unit
    val closeDialog: () -> Unit
    val handleDialogEvent: (GuideProgrammeDialogEvent) -> Unit
    val handleGuideKey: (RemoteKey) -> Unit
    val confirmGuide: () -> Unit
    val showStatus: (String) -> Unit
    val exitApplication: () -> Unit
}

internal class WebOsRemoteController(
    private val host: WebOsNavigationHost,
) {
    private var state =
        AppRemoteState(
            section = DashboardSection.CHANNELS,
            focus = TvFocusZone.CONTENT,
            menuIndex = DashboardSection.CHANNELS.ordinal,
            channels = ChannelNavigationState(ChannelRemoteFocus.LIST, 0, 0),
            filterCount = 1,
            requireDoubleBack = true,
        )
    private var numberTimer: Int? = null
    private var previewTimer: Int? = null

    fun onSectionActivated(section: WebOsSection) {
        if (section == WebOsSection.LIVE) {
            state = state.copy(section = section.dashboardSection(), navigationVisible = true)
        } else {
            cancelNumberTimer()
            cancelPreviewTimer()
            host.showChannelNumberInput(null)
            host.previewChannel(null)
            state =
                state.copy(
                    section = section.dashboardSection(),
                    number = "",
                    overlayVisible = false,
                    preview = state.preview.copy(channelId = null),
                    navigationVisible = true,
                )
        }
    }

    fun onNavigationFocused(section: WebOsSection) {
        state =
            state.copy(
                focus = TvFocusZone.MAIN_NAVIGATION,
                menuIndex = section.dashboardSection().ordinal,
            )
    }

    fun onGuideFocused() {
        state = state.copy(focus = TvFocusZone.CONTENT, section = DashboardSection.GUIDE)
    }

    fun onChannelFocused(
        index: Int,
        favorite: Boolean,
    ) {
        state =
            state.copy(
                focus = TvFocusZone.CONTENT,
                channels = state.channels.copy(focus = if (favorite) ChannelRemoteFocus.FAVORITE else ChannelRemoteFocus.LIST, channelIndex = index),
            )
    }

    fun onSearchFocused() {
        state = state.copy(focus = TvFocusZone.CONTENT, channels = state.channels.copy(focus = ChannelRemoteFocus.SEARCH))
    }

    fun onChannelFilterFocused(index: Int) {
        state = state.copy(focus = TvFocusZone.CONTENT, channels = state.channels.copy(focus = ChannelRemoteFocus.FILTERS, filterIndex = index))
    }

    fun onSettingsFocused(index: Int) {
        state = state.copy(focus = TvFocusZone.CONTENT, settings = state.settings.copy(section = null, option = null, categoryIndex = index))
    }

    fun onSettingsCategoryActivated(index: Int) {
        val section = SettingsSection.entries[index.coerceIn(0, SettingsSection.entries.lastIndex)]
        state = state.copy(focus = TvFocusZone.CONTENT, settings = state.settings.copy(section = section, categoryIndex = section.ordinal, option = section.options().firstOrNull()))
        host.openSettingsSection(section)
        host.focusSettingsOption(0)
    }

    fun onSettingsOptionFocused(index: Int) {
        val options =
            state.settings.section
                ?.options()
                .orEmpty()
        options.getOrNull(index)?.let { option -> state = state.copy(focus = TvFocusZone.CONTENT, settings = state.settings.copy(option = option)) }
    }

    fun pauseForBackground() {
        cancelNumberTimer()
        cancelPreviewTimer()
        host.showChannelNumberInput(null)
        host.previewChannel(null)
        state =
            state.copy(
                number = "",
                overlayVisible = false,
                preview = state.preview.copy(channelId = null),
            )
    }

    fun handle(event: KeyboardEvent): Boolean {
        val key =
            webOsRemoteKey(
                keyCode = event.keyCode,
                liveContent = host.activeSection == WebOsSection.LIVE && state.focus == TvFocusZone.CONTENT,
                repeated = event.repeat,
            ) ?: return false
        val handled = dispatch(key)
        if (handled) event.preventDefault()
        return handled
    }

    internal fun dispatch(
        key: AppRemoteKey,
        nowMillis: Long = Date.now().toLong(),
    ): Boolean {
        state = synchronizedState(nowMillis)
        val result = state.reduce(key)
        state = result.state
        if (!result.handled) {
            if (key.back && !key.backspace) {
                host.exitApplication()
                return true
            }
            return false
        }
        result.effects.forEach(::applyEffect)
        if (key.digit != null) scheduleNumberSelection()
        if (host.dialogVisible) return true
        restoreFocus()
        return true
    }

    private fun synchronizedState(nowMillis: Long): AppRemoteState =
        state.copy(
            section = host.activeSection.dashboardSection(),
            channelIds = host.visibleChannelIds,
            selectedChannelId = host.selectedChannelIdForNavigation,
            searchHasText = host.channelSearchHasText,
            searchOpen = host.channelSearchOpen,
            filterCount = host.channelFilterCount.coerceAtLeast(1),
            overlayVisible = host.liveOverlayVisible,
            dialogVisible = host.dialogVisible,
            navigationVisible = host.liveNavigationVisible,
            settings = if (!host.settingsDetailOpen) state.settings.copy(section = null, option = null) else state.settings,
            nowMillis = nowMillis,
        )

    private fun applyEffect(effect: AppRemoteEffect) {
        when (effect) {
            AppRemoteEffect.ResetExit,
            -> {
                return
            }

            AppRemoteEffect.ConfirmGuide -> {
                host.confirmGuide()
            }

            is AppRemoteEffect.GuideKey -> {
                host.handleGuideKey(effect.key)
            }

            is AppRemoteEffect.Dialog -> {
                host.handleDialogEvent(effect.event)
                state = state.copy(dialogVisible = host.dialogVisible)
            }

            AppRemoteEffect.RevealNavigation -> {
                host.showLiveNavigation()
                host.focusNavigation(host.activeSection)
            }

            AppRemoteEffect.InteractNavigation -> {
                host.showLiveNavigation()
            }

            AppRemoteEffect.ShowOverlay -> {
                host.showLiveOverlay()
            }

            AppRemoteEffect.ShowQuickSettings -> {
                host.showQuickSettings()
            }

            AppRemoteEffect.PreviousChannel -> {
                host.openPreviousChannel()
            }

            AppRemoteEffect.ShowExitHint -> {
                host.showStatus("A kilépéshez nyomd meg újra a Vissza gombot.")
            }

            is AppRemoteEffect.SwitchChannel -> {
                host.switchChannel(effect.delta)
            }

            is AppRemoteEffect.SelectNumber -> {
                cancelNumberTimer()
                host.showChannelNumberInput(null)
                host.selectChannelNumber(effect.number)
            }

            is AppRemoteEffect.ActivateSection -> {
                activateSection(effect.section)
            }

            is AppRemoteEffect.Preview -> {
                applyPreview(effect)
            }

            is AppRemoteEffect.Back -> {
                applyBack(effect.effect)
            }

            is AppRemoteEffect.Channels -> {
                applyChannels(effect.effect)
            }

            is AppRemoteEffect.Settings -> {
                applySettings(effect.effect)
            }
        }
    }

    private fun activateSection(section: DashboardSection) {
        val target = section.webOsSection()
        state = state.copy(section = section)
        host.activateSection(target)
    }

    private fun applyPreview(effect: AppRemoteEffect.Preview) {
        val preview = effect.result
        when (preview.effect) {
            LiveChannelPreviewEffect.OPEN_CHANNEL -> {
                cancelPreviewTimer()
                preview.channelIdToOpen?.let { id ->
                    val index = host.visibleChannelIds.indexOf(id)
                    if (index >= 0) host.openChannel(index)
                }
            }

            LiveChannelPreviewEffect.DISMISS -> {
                cancelPreviewTimer()
                host.previewChannel(null)
            }

            LiveChannelPreviewEffect.NONE -> {
                host.previewChannel(preview.state.channelId)
                schedulePreviewTimeout()
            }
        }
    }

    private fun applyBack(effect: AppBackNavigationEffect) {
        when (effect) {
            AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH -> {
                host.clearChannelSearch()
            }

            AppBackNavigationEffect.DISMISS_LIVE_OVERLAY -> {
                host.hideLiveOverlay()
            }

            AppBackNavigationEffect.FOCUS_MAIN_NAVIGATION,
            -> {
                host.focusNavigation(host.activeSection)
            }

            AppBackNavigationEffect.CLOSE_SETTINGS_DETAIL -> {
                host.closeSettingsSection()
                host.focusSettings(state.settings.categoryIndex)
            }

            AppBackNavigationEffect.DISMISS_GUIDE_DIALOG,
            AppBackNavigationEffect.EXIT_APPLICATION,
            -> {
            }
        }
    }

    private fun applyChannels(effect: ChannelNavigationEffect) {
        when (effect) {
            ChannelNavigationEffect.None -> {
                return
            }

            ChannelNavigationEffect.ExitToMainMenu -> {
                state = state.copy(focus = TvFocusZone.MAIN_NAVIGATION, menuIndex = DashboardSection.CHANNELS.ordinal)
            }

            ChannelNavigationEffect.ActivateEmptyState -> {
                host.activateChannelEmpty()
            }

            is ChannelNavigationEffect.ActivateFilter -> {
                host.activateChannelFilter(effect.index)
            }

            is ChannelNavigationEffect.OpenChannel -> {
                host.openChannel(effect.index)
            }

            is ChannelNavigationEffect.ToggleFavorite -> {
                host.toggleFavorite(effect.index)
            }
        }
    }

    private fun applySettings(effect: SettingsNavigationEffect) {
        when (effect) {
            SettingsNavigationEffect.None -> state.settings.section?.let(host.openSettingsSection)
            SettingsNavigationEffect.ExitToMainMenu -> host.focusNavigation(WebOsSection.SETTINGS)
            is SettingsNavigationEffect.Adjust -> host.adjustSetting(effect.option, effect.delta)
            is SettingsNavigationEffect.Activate -> host.activateSetting(effect.option)
        }
    }

    private fun restoreFocus() {
        if (state.focus == TvFocusZone.MAIN_NAVIGATION) {
            host.focusNavigation(webOsSectionOrder[state.menuIndex.coerceIn(webOsSectionOrder.indices)])
            return
        }
        when (host.activeSection) {
            WebOsSection.CHANNELS -> {
                restoreChannelFocus()
            }

            WebOsSection.SETTINGS -> {
                if (state.settings.section == null) host.focusSettings(state.settings.categoryIndex) else host.focusSettingsOption(state.settings.optionIndex)
            }

            else -> {
                host.focusSectionContent(host.activeSection)
            }
        }
    }

    private fun restoreChannelFocus() {
        when (state.channels.focus) {
            ChannelRemoteFocus.FILTERS -> host.focusChannelFilter(state.channels.filterIndex)
            ChannelRemoteFocus.SEARCH -> host.focusChannelSearch()
            ChannelRemoteFocus.LIST -> host.focusChannel(state.channels.channelIndex, false)
            ChannelRemoteFocus.FAVORITE -> host.focusChannel(state.channels.channelIndex, true)
        }
    }

    private fun scheduleNumberSelection() {
        cancelNumberTimer()
        host.showChannelNumberInput(state.number)
        numberTimer =
            window.setTimeout(
                {
                    val number = state.number
                    state = state.copy(number = "")
                    host.showChannelNumberInput(null)
                    if (number.isNotEmpty()) host.selectChannelNumber(number)
                    numberTimer = null
                },
                3_000,
            )
    }

    private fun cancelNumberTimer() {
        numberTimer?.let(window::clearTimeout)
        numberTimer = null
    }

    private fun schedulePreviewTimeout() {
        cancelPreviewTimer()
        previewTimer =
            window.setTimeout(
                {
                    val result = state.reduce(AppRemoteKey(preview = LiveChannelPreviewEvent.TIMEOUT))
                    state = result.state
                    result.effects.forEach(::applyEffect)
                    previewTimer = null
                },
                liveLayerTimeoutMillis(LiveLayer.INFORMATION_PANEL) ?: 5_000,
            )
    }

    private fun cancelPreviewTimer() {
        previewTimer?.let(window::clearTimeout)
        previewTimer = null
    }
}

private fun WebOsSection.dashboardSection(): DashboardSection = DashboardSection.valueOf(name)

private fun DashboardSection.webOsSection(): WebOsSection = WebOsSection.valueOf(name)
