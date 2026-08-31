package hu.wukki.tv.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.wukki.tv.Channel
import hu.wukki.tv.DeviceInfo
import hu.wukki.tv.LiveVideoGestures
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.channels.ChannelBrowserCallbacks
import hu.wukki.tv.ui.channels.ChannelBrowserRowUiState
import hu.wukki.tv.ui.channels.ChannelBrowserScreen
import hu.wukki.tv.ui.channels.ChannelBrowserUiState
import hu.wukki.tv.ui.channels.ChannelPreviewUiState
import hu.wukki.tv.ui.components.text
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.guide.EpgGuideScreen
import hu.wukki.tv.ui.guide.EpgGuideState
import hu.wukki.tv.ui.guide.GuideProgrammeDetails
import hu.wukki.tv.ui.guide.GuideProgrammeDetailsUiState
import hu.wukki.tv.ui.guide.GuideProgrammeDialogAction
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.guide.guideTimeline
import hu.wukki.tv.ui.live.LiveTvScreen
import hu.wukki.tv.ui.live.LiveTvUiState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.NavigationEntryUiState
import hu.wukki.tv.ui.navigation.TopNavigation
import hu.wukki.tv.ui.navigation.SideNavigationUiState
import hu.wukki.tv.ui.settings.SettingsScreen
import hu.wukki.tv.ui.settings.SettingsCallbacks
import hu.wukki.tv.ui.settings.SettingsSection
import hu.wukki.tv.ui.settings.SettingsSourceUiState
import hu.wukki.tv.ui.settings.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun navigationState(model: WukkiModel, activeSection: DashboardSection, focusedSection: DashboardSection?): SideNavigationUiState {
    val language = model.settings.language
    return SideNavigationUiState(
        entries = listOf(
            NavigationEntryUiState(DashboardSection.LIVE, tr(language, "nav.live")),
            NavigationEntryUiState(DashboardSection.GUIDE, tr(language, "nav.guide")),
            NavigationEntryUiState(DashboardSection.CHANNELS, tr(language, "nav.channels")),
            NavigationEntryUiState(DashboardSection.SETTINGS, tr(language, "nav.settings"))
        ),
        activeSection = activeSection,
        focusedSection = focusedSection
    )
}

private fun channelBrowserUiState(model: WukkiModel, tick: Long, previewChannelId: String?): ChannelBrowserUiState {
    val rows = model.filteredChannels().mapIndexed { index, channel ->
        val current = model.currentProgram(channel, tick)
        ChannelBrowserRowUiState(channel, index + 1, current, current?.let { model.nextProgram(channel, it) })
    }
    val selected = rows.firstOrNull { it.channel.id == previewChannelId }?.channel
        ?: rows.firstOrNull()?.channel
    return ChannelBrowserUiState(
        language = model.settings.language,
        categories = model.categories(),
        query = model.query,
        selectedCategory = model.category,
        onlyFavorites = model.onlyFavorites,
        channels = rows,
        displayMode = model.settings.display.channelListMode ?: hu.wukki.tv.ChannelListDisplayMode.NORMAL,
        showChannelProgramme = model.settings.display.showChannelProgramme,
        showMiniGuide = model.settings.display.showMiniGuide,
        showLogos = model.settings.display.showLogos,
        showProgrammeImages = model.settings.display.showProgrammeImages != false,
        preview = selected?.let { ChannelPreviewUiState(it, model.currentProgram(it, tick), tick) }
    )
}

@Composable
fun DashboardScreen(
    model: WukkiModel,
    scope: CoroutineScope,
    tick: Long,
    activeSection: DashboardSection,
    liveNavigationVisible: Boolean,
    guideState: EpgGuideState,
    onSectionChange: (DashboardSection) -> Unit,
    settingsSection: SettingsSection?,
    onSettingsSectionChange: (SettingsSection?) -> Unit,
    mainNavigationFocused: Boolean,
    mainNavigationSection: DashboardSection,
    channelRemoteFocus: ChannelRemoteFocus,
    channelFilterIndex: Int,
    channelListIndex: Int,
    channelListOpenRequest: Int,
    channelPreviewId: String?,
    onChannelPreviewSelect: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    channelSearchOpen: Boolean,
    onChannelSearchOpenChange: (Boolean) -> Unit,
    settingsCategoryIndex: Int,
    settingsOptionIndex: Int,
    settingsDropdownOpenRequest: Int,
    settingsDropdownOptionIndex: Int,
    settingsAboutOpenRequest: Int,
    androidSettingsNavigation: Boolean,
    onSettingsCategoryFocus: (Int) -> Unit,
    onSettingsOptionFocus: (Int) -> Unit,
    guideProgrammeDetailsVisible: Boolean,
    onShowGuideProgrammeDetails: () -> Unit,
    onDismissGuideProgrammeDetails: () -> Unit,
    onOpenGuideProgrammeChannel: (String) -> Unit,
    onGuideProgrammeDialogEvent: (GuideProgrammeDialogEvent) -> Unit,
    guideProgrammeDialogAction: GuideProgrammeDialogAction,
    videoHost: @Composable (Modifier, LiveVideoGestures?) -> Unit,
    liveVideoGestures: LiveVideoGestures,
    playbackEngineLabel: String,
    deviceInfo: DeviceInfo?
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / 1470f, maxHeight.value / 920f).coerceIn(.70f, 1.45f)
        val padding = (14.dp * scale).coerceIn(8.dp, 20.dp)
        val navigationState = navigationState(model, activeSection, mainNavigationSection.takeIf { mainNavigationFocused })
        Column(Modifier.fillMaxSize()) {
            if (activeSection != DashboardSection.LIVE) {
                TopNavigation(
                    state = navigationState,
                    onSelect = onSectionChange,
                    scale = scale,
                    showLabels = !androidSettingsNavigation,
                    overlay = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                when (activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(
                    LiveTvUiState(model.selectedChannel() != null, tr(model.settings.language, "live.empty")),
                    scale,
                    video = { videoHost(Modifier.fillMaxSize(), liveVideoGestures) },
                    modifier = Modifier.fillMaxSize()
                )
                DashboardSection.GUIDE -> EpgGuideScreen(
                    model.guideDataSource(), tick, guideState, onProgrammeClick = { _, _ -> onShowGuideProgrammeDetails() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                DashboardSection.CHANNELS -> ChannelBrowserScreen(
                    state = channelBrowserUiState(model, tick, channelPreviewId),
                    callbacks = ChannelBrowserCallbacks(
                        onQueryChange = model::setChannelQuery,
                        onSelectAll = model::showAllChannels,
                        onSelectFavorites = model::showFavoriteChannels,
                        onSelectCategory = model::showChannelCategory,
                        onSelectChannel = onChannelPreviewSelect,
                        onOpenChannel = onOpenChannel,
                        onToggleFavorite = model::toggleFavorite
                    ),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    scale = scale.coerceAtMost(1f),
                    remoteFocus = channelRemoteFocus,
                    remoteFilterIndex = channelFilterIndex,
                    remoteListIndex = channelListIndex,
                    listOpenRequest = channelListOpenRequest,
                    searchOpen = channelSearchOpen,
                    onSearchOpenChange = onChannelSearchOpenChange
                )
                DashboardSection.SETTINGS -> SettingsScreen(
                    state = SettingsUiState(
                        settings = model.settings,
                        playlistSource = SettingsSourceUiState(
                            model.officialPlaylist.name,
                            model.officialPlaylist.location,
                            model.officialPlaylist.updatedAt
                        ),
                        epgSource = model.officialEpgSource?.let { source ->
                            SettingsSourceUiState(source.name, source.url, source.lastUpdatedAt)
                        },
                        channelCount = model.state.channels.size,
                        deviceInfo = deviceInfo,
                        playbackEngineLabel = playbackEngineLabel
                    ),
                    callbacks = SettingsCallbacks(
                        updatePlayback = model::updatePlayback,
                        updateDisplay = model::updateDisplay,
                        setPlaylistRefresh = model::setPlaylistRefresh,
                        setEpgRefresh = model::setEpgRefresh,
                        setLanguage = model::setLanguage,
                        refreshPlaylist = { scope.launch { model.refreshOfficialPlaylist() } },
                        refreshEpg = { scope.launch { model.refreshOfficialEpg() } }
                    ),
                    selectedSection = settingsSection,
                    onSectionChange = onSettingsSectionChange, remoteCategoryIndex = settingsCategoryIndex,
                    remoteNavigationActive = !mainNavigationFocused, remoteOptionIndex = settingsOptionIndex,
                    settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                    settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                    settingsAboutOpenRequest = settingsAboutOpenRequest,
                    androidFullScreenSubmenus = androidSettingsNavigation,
                    onCategoryFocus = onSettingsCategoryFocus,
                    onOptionFocus = onSettingsOptionFocus,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                }
            }
        }
        if (activeSection == DashboardSection.LIVE) {
            AnimatedVisibility(
                visible = liveNavigationVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) {
                TopNavigation(
                    state = navigationState,
                    onSelect = onSectionChange,
                    scale = scale,
                    showLabels = !androidSettingsNavigation,
                    overlay = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.error?.let { AppFeedback(tr(model.settings.language, "app.error.prefix", it.text(model.settings.language))) }
            model.status?.let { message ->
                AppFeedback(message.text(model.settings.language))
            }
        }
        if (guideProgrammeDetailsVisible) {
            val focused = guideState.focusedProgramme(model.guideDataSource(), guideTimeline(tick, model.guideLatestProgrammeEnd()))
            focused?.let { (channel: Channel, programme) ->
                val next = model.programmesFor(channel, programme.end, programme.end + 86_400_000L).firstOrNull()
                GuideProgrammeDetails(
                    GuideProgrammeDetailsUiState(
                        model.settings.language,
                        channel,
                        programme,
                        next,
                        guideProgrammeDialogAction
                    ),
                    onDismissGuideProgrammeDetails,
                    onOpenGuideProgrammeChannel,
                    onGuideProgrammeDialogEvent,
                    handleSystemBackKey = !androidSettingsNavigation
                )
            }
        }
    }
}
