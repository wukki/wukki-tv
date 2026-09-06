package hu.wukki.tv.ui.navigation

import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent

/** Platform-neutral input; page/channel keys retain their immediate live-channel semantics. */
data class AppRemoteKey(
    val remote: RemoteKey? = null,
    val digit: String? = null,
    val back: Boolean = false,
    val escape: Boolean = false,
    val backspace: Boolean = false,
    val channelDelta: Int? = null,
    val preview: LiveChannelPreviewEvent? = null
)

data class AppRemoteState(
    val section: DashboardSection = DashboardSection.LIVE,
    val focus: TvFocusZone = TvFocusZone.CONTENT,
    val menuIndex: Int = 0,
    val settings: SettingsNavigationState = SettingsNavigationState(),
    val channels: ChannelNavigationState = ChannelNavigationState(ChannelRemoteFocus.LIST, 0, 0),
    val filterCount: Int = 2,
    val channelIds: List<String> = emptyList(),
    val selectedChannelId: String? = null,
    val searchHasText: Boolean = false,
    val searchOpen: Boolean = false,
    val dialogVisible: Boolean = false,
    val overlayVisible: Boolean = false,
    val preview: LiveChannelPreviewState = LiveChannelPreviewState(),
    val number: String = "",
    val exitConfirmation: ExitConfirmationState = ExitConfirmationState(),
    val requireDoubleBack: Boolean = false,
    val nowMillis: Long = 0L,
    val navigationVisible: Boolean = true
)

sealed interface AppRemoteEffect {
    data object ShowExitHint : AppRemoteEffect
    data object ResetExit : AppRemoteEffect
    data class Dialog(val event: GuideProgrammeDialogEvent) : AppRemoteEffect
    data class Back(val effect: AppBackNavigationEffect) : AppRemoteEffect
    data object RevealNavigation : AppRemoteEffect
    data class SwitchChannel(val delta: Int) : AppRemoteEffect
    data class ActivateSection(val section: DashboardSection) : AppRemoteEffect
    data object InteractNavigation : AppRemoteEffect
    data object ShowGuideDetails : AppRemoteEffect
    data class GuideKey(val key: RemoteKey) : AppRemoteEffect
    data class SelectNumber(val number: String) : AppRemoteEffect
    data class Preview(val result: LiveChannelPreviewResult) : AppRemoteEffect
    data object ShowOverlay : AppRemoteEffect
    data class Settings(val effect: SettingsNavigationEffect) : AppRemoteEffect
    data class Channels(val effect: ChannelNavigationEffect) : AppRemoteEffect
}

data class AppRemoteResult(val state: AppRemoteState, val effects: List<AppRemoteEffect> = emptyList(), val handled: Boolean = true)

fun AppRemoteState.reduce(key: AppRemoteKey): AppRemoteResult {
    val prefix = if (key.back) emptyList() else listOf(AppRemoteEffect.ResetExit)
    fun result(state: AppRemoteState = this, effect: AppRemoteEffect? = null, handled: Boolean = true) =
        AppRemoteResult(
            if (!key.back || effect is AppRemoteEffect.Back || effect == AppRemoteEffect.RevealNavigation)
                state.copy(exitConfirmation = ExitConfirmationState()) else state,
            prefix + listOfNotNull(effect), handled
        )
    if (dialogVisible) {
        val event = when {
            key.back -> GuideProgrammeDialogEvent.BACK
            key.remote == RemoteKey.LEFT -> GuideProgrammeDialogEvent.LEFT
            key.remote == RemoteKey.RIGHT -> GuideProgrammeDialogEvent.RIGHT
            key.remote == RemoteKey.CONFIRM -> GuideProgrammeDialogEvent.CONFIRM
            else -> null
        }
        return result(effect = event?.let(AppRemoteEffect::Dialog), handled = event != null)
    }
    if (section == DashboardSection.CHANNELS && channels.focus == ChannelRemoteFocus.SEARCH) {
        if (key.escape) return result(copy(searchOpen = false, channels = channels.copy(focus = ChannelRemoteFocus.LIST)),
            AppRemoteEffect.Back(AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH))
        if (key.backspace) return result(handled = false)
    }
    if (key.back) {
        if (section == DashboardSection.LIVE && !navigationVisible) {
            return result(copy(navigationVisible = true, focus = TvFocusZone.MAIN_NAVIGATION), AppRemoteEffect.RevealNavigation)
        }
        val effect = AppBackNavigationState(dialogVisible, section == DashboardSection.CHANNELS && searchOpen,
            section == DashboardSection.LIVE && (preview.isActive || overlayVisible),
            section == DashboardSection.SETTINGS && settings.section != null, focus).reduce()
        if (effect == AppBackNavigationEffect.EXIT_APPLICATION) {
            if (!requireDoubleBack) return result(handled = false)
            val exit = exitConfirmation.requestExit(nowMillis)
            return result(copy(exitConfirmation = exit.state),
                AppRemoteEffect.ShowExitHint.takeIf { exit.effect == ExitConfirmationEffect.SHOW_HINT },
                handled = exit.effect == ExitConfirmationEffect.SHOW_HINT)
        }
        val next = when (effect) {
            AppBackNavigationEffect.DISMISS_GUIDE_DIALOG -> copy(dialogVisible = false)
            AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH -> copy(searchOpen = false, channels = channels.copy(focus = ChannelRemoteFocus.LIST))
            AppBackNavigationEffect.DISMISS_LIVE_OVERLAY -> copy(overlayVisible = false, preview = preview.copy(channelId = null, interactionSequence = preview.interactionSequence + if (preview.isActive) 1 else 0))
            AppBackNavigationEffect.CLOSE_SETTINGS_DETAIL -> copy(settings = settings.copy(section = null, option = null))
            AppBackNavigationEffect.FOCUS_MAIN_NAVIGATION -> copy(focus = TvFocusZone.MAIN_NAVIGATION, menuIndex = DashboardSection.entries.indexOf(section))
            AppBackNavigationEffect.EXIT_APPLICATION -> this
        }
        return result(next, AppRemoteEffect.Back(effect))
    }
    if (section == DashboardSection.LIVE && key.channelDelta != null) return result(effect = AppRemoteEffect.SwitchChannel(key.channelDelta))
    if (focus == TvFocusZone.MAIN_NAVIGATION) {
        val remote = key.remote ?: return result(handled = false)
        val menu = MainMenuNavigationState(menuIndex).reduce(remote, DashboardSection.entries.size)
        val target = (menu.effect as? MainMenuNavigationEffect.Activate)?.let { DashboardSection.entries[it.index] }
        val next = copy(menuIndex = menu.state.index, focus = if (menu.effect == MainMenuNavigationEffect.EnterContent) TvFocusZone.CONTENT else focus)
        val effects = listOfNotNull(target?.let(AppRemoteEffect::ActivateSection)) +
            if (menu.handled && section == DashboardSection.LIVE && (target == null || target == DashboardSection.LIVE)) listOf(AppRemoteEffect.InteractNavigation) else emptyList()
        return AppRemoteResult(next, prefix + effects, menu.handled)
    }
    if (section == DashboardSection.GUIDE && key.remote != null) return result(effect =
        if (key.remote == RemoteKey.CONFIRM) AppRemoteEffect.ShowGuideDetails else AppRemoteEffect.GuideKey(key.remote))
    if (section == DashboardSection.LIVE && key.remote == RemoteKey.CONFIRM) {
        if (number.isNotEmpty()) return result(copy(number = "", preview = preview.copy(channelId = null, interactionSequence = preview.interactionSequence + if (preview.isActive) 1 else 0)), AppRemoteEffect.SelectNumber(number))
        if (preview.isActive) {
            val selection = preview.reduce(LiveChannelPreviewEvent.CONFIRM, channelIds, selectedChannelId, overlayVisible)
            return result(copy(preview = selection.state), AppRemoteEffect.Preview(selection))
        }
        return result(effect = AppRemoteEffect.ShowOverlay)
    }
    if (section == DashboardSection.SETTINGS) {
        val remote = key.remote ?: return result(handled = false)
        val transition = settings.reduce(remote)
        return result(copy(settings = transition.state, focus = if (transition.effect == SettingsNavigationEffect.ExitToMainMenu) TvFocusZone.MAIN_NAVIGATION else focus), AppRemoteEffect.Settings(transition.effect), transition.handled)
    }
    if (section == DashboardSection.CHANNELS) {
        val remote = key.remote ?: return result(handled = false)
        val transition = channels.reduce(remote, filterCount, channelIds.size, searchHasText)
        return result(copy(channels = transition.state), AppRemoteEffect.Channels(transition.effect), transition.handled)
    }
    if (key.digit != null) return if (section == DashboardSection.LIVE) result(copy(number = (number + key.digit).take(4),
        overlayVisible = false, preview = preview.copy(channelId = null, interactionSequence = preview.interactionSequence + if (preview.isActive) 1 else 0))) else result(handled = false)
    if (key.preview != null) {
        val transition = preview.reduce(key.preview, channelIds, selectedChannelId, overlayVisible)
        return result(copy(preview = transition.state), AppRemoteEffect.Preview(transition))
    }
    return result(handled = false)
}
