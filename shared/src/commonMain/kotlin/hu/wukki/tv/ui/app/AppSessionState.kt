package hu.wukki.tv.ui.app

import androidx.compose.runtime.*
import hu.wukki.tv.DeviceInfo
import hu.wukki.tv.ui.guide.GuideProgrammeDialogState
import hu.wukki.tv.ui.navigation.*

/** Ephemeral dashboard state, owned by one composition rather than the persisted content model. */
@Stable
class AppSessionState(autoPlayOnLaunch: Boolean) {
    var tick by mutableStateOf(System.currentTimeMillis())
    var settingsNavigation by mutableStateOf(SettingsNavigationState())
    var activeSection by mutableStateOf(if (autoPlayOnLaunch) DashboardSection.LIVE else DashboardSection.CHANNELS)
    var focusZone by mutableStateOf(TvFocusZone.CONTENT)
    var mainNavigationIndex by mutableIntStateOf(DashboardSection.entries.indexOf(activeSection).coerceAtLeast(0))
    var channelRemoteFocus by mutableStateOf(ChannelRemoteFocus.LIST)
    var channelFilterIndex by mutableIntStateOf(0)
    var channelListIndex by mutableIntStateOf(0)
    var channelFocusedId by mutableStateOf<String?>(null)
    var channelSearchOpen by mutableStateOf(false)
    var channelListOpenRequest by mutableIntStateOf(if (activeSection == DashboardSection.CHANNELS) 1 else 0)
    var settingsDropdownOpenRequest by mutableIntStateOf(0)
    var settingsDropdownOptionIndex by mutableIntStateOf(-1)
    var settingsAboutOpenRequest by mutableIntStateOf(0)
    var guideProgrammeDetailsVisible by mutableStateOf(false)
    var guideProgrammeDialogState by mutableStateOf(GuideProgrammeDialogState())
    var automaticLaunchPending by mutableStateOf(autoPlayOnLaunch)
    var observedAutoPlaySetting by mutableStateOf(autoPlayOnLaunch)
    var officialSourceReady by mutableStateOf(false)
    var overlayRequest by mutableIntStateOf(0)
    var programmeOverlayVisible by mutableStateOf(false)
    var liveChannelPreviewState by mutableStateOf(LiveChannelPreviewState())
    var liveNavigationState by mutableStateOf(LiveNavigationVisibilityState())
    var exitConfirmationState by mutableStateOf(ExitConfirmationState())
    var channelNumberInput by mutableStateOf("")
    var deviceInfo by mutableStateOf<DeviceInfo?>(null)
}
