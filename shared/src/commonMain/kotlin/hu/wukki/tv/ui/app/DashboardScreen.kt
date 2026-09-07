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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.wukki.tv.Channel
import hu.wukki.tv.LiveVideoGestures
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.adjustSetting
import hu.wukki.tv.setVolume
import hu.wukki.tv.setBufferProfile
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
import hu.wukki.tv.ui.guide.GuideDataSource
import hu.wukki.tv.ui.guide.guideTimeline
import hu.wukki.tv.ui.live.LiveTvScreen
import hu.wukki.tv.ui.live.LiveTvUiState
import hu.wukki.tv.ui.navigation.TvFocusZone
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.NavigationEntryUiState
import hu.wukki.tv.ui.navigation.TopNavigation
import hu.wukki.tv.ui.navigation.SideNavigationUiState
import hu.wukki.tv.ui.settings.SettingsScreen
import hu.wukki.tv.ui.settings.SettingsCallbacks
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

private fun channelBrowserUiState(model: WukkiModel, tick: Long): ChannelBrowserUiState {
    val rows = model.filteredChannels().mapIndexed { index, channel ->
        val current = model.currentProgram(channel, tick)
        ChannelBrowserRowUiState(channel, index + 1, current, current?.let { model.nextProgram(channel, it) })
    }
    return ChannelBrowserUiState(
        language = model.settings.language,
        categories = model.categories(),
        query = model.query,
        selectedCategory = model.category,
        onlyFavorites = model.onlyFavorites,
        channels = rows,
        displayMode = model.settings.display.channelListMode,
        showChannelProgramme = model.settings.display.showChannelProgramme,
        showMiniGuide = model.settings.display.showMiniGuide,
        showLogos = model.settings.display.showLogos,
        showProgrammeImages = model.settings.display.showProgrammeImages,
        preview = null
    )
}

private fun ChannelBrowserUiState.withPreview(channelId: String?, tick: Long): ChannelBrowserUiState {
    val selected = channels.firstOrNull { it.channel.id == channelId } ?: channels.firstOrNull()
    return copy(preview = selected?.let { row -> ChannelPreviewUiState(row.channel, row.currentProgramme, tick) })
}

@Composable
fun DashboardScreen(
    session: AppSessionState,
    callbacks: DashboardCallbacks,
    model: WukkiModel,
    scope: CoroutineScope,
    guideDataSource: GuideDataSource,
    guideState: EpgGuideState,
    androidSettingsNavigation: Boolean,
    videoHost: @Composable (Modifier, LiveVideoGestures?) -> Unit,
    liveVideoGestures: LiveVideoGestures,
    playbackEngineLabel: String,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / 1470f, maxHeight.value / 920f).coerceIn(.70f, 1.45f)
        val padding = (14.dp * scale).coerceIn(8.dp, 20.dp)
        val navigationState = navigationState(model, session.activeSection, DashboardSection.entries[session.mainNavigationIndex].takeIf { (session.focusZone == TvFocusZone.MAIN_NAVIGATION) })
        Column(Modifier.fillMaxSize()) {
            if (session.activeSection != DashboardSection.LIVE) {
                TopNavigation(
                    state = navigationState,
                    onSelect = callbacks.onSectionChange,
                    scale = scale,
                    showLabels = !androidSettingsNavigation,
                    overlay = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                when (session.activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(
                    LiveTvUiState(model.selectedChannel() != null, tr(model.settings.language, "live.empty")),
                    scale,
                    video = { videoHost(Modifier.fillMaxSize(), liveVideoGestures) },
                    modifier = Modifier.fillMaxSize()
                )
                DashboardSection.GUIDE -> EpgGuideScreen(
                    guideDataSource, session.tick, guideState, onProgrammeClick = { _, _ -> callbacks.onShowGuideProgrammeDetails() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                DashboardSection.CHANNELS -> {
                    val browserState = remember(
                        model.state,
                        model.query,
                        model.category,
                        model.onlyFavorites,
                        session.tick
                    ) { channelBrowserUiState(model, session.tick) }
                    val browserStateWithPreview = remember(browserState, (session.channelFocusedId ?: model.selectedChannelId)) {
                        browserState.withPreview((session.channelFocusedId ?: model.selectedChannelId), session.tick)
                    }
                    ChannelBrowserScreen(
                        state = browserStateWithPreview,
                        callbacks = ChannelBrowserCallbacks(
                            onQueryChange = model::setChannelQuery,
                            onSelectAll = model::showAllChannels,
                            onSelectFavorites = model::showFavoriteChannels,
                            onSelectCategory = model::showChannelCategory,
                            onSelectChannel = callbacks.onChannelPreviewSelect,
                            onOpenChannel = callbacks.onOpenChannel,
                            onToggleFavorite = model::toggleFavorite
                        ),
                        modifier = Modifier.fillMaxSize().padding(padding),
                        scale = scale.coerceAtMost(1f),
                        remoteFocus = session.channelRemoteFocus,
                        remoteFilterIndex = session.channelFilterIndex,
                        remoteListIndex = session.channelListIndex,
                        listOpenRequest = session.channelListOpenRequest,
                        searchOpen = session.channelSearchOpen,
                        onSearchOpenChange = callbacks.onChannelSearchOpenChange
                    )
                }
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
                        deviceInfo = session.deviceInfo,
                        playbackEngineLabel = playbackEngineLabel
                    ),
                    callbacks = SettingsCallbacks(
                        adjustSetting = model::adjustSetting,
                        setVolume = model::setVolume,
                        setBufferProfile = model::setBufferProfile,
                        updatePlayback = model::updatePlayback,
                        updateDisplay = model::updateDisplay,
                        setPlaylistRefresh = model::setPlaylistRefresh,
                        setEpgRefresh = model::setEpgRefresh,
                        setLanguage = model::setLanguage,
                        refreshPlaylist = { scope.launch { model.refreshOfficialPlaylist() } },
                        refreshEpg = { scope.launch { model.refreshOfficialEpg() } }
                    ),
                    selectedSection = session.settingsNavigation.section,
                    onSectionChange = callbacks.onSettingsSectionChange, remoteCategoryIndex = session.settingsNavigation.categoryIndex,
                    remoteNavigationActive = !(session.focusZone == TvFocusZone.MAIN_NAVIGATION), remoteOptionIndex = session.settingsNavigation.optionIndex,
                    settingsDropdownOpenRequest = session.settingsDropdownOpenRequest,
                    settingsDropdownOptionIndex = session.settingsDropdownOptionIndex,
                    settingsAboutOpenRequest = session.settingsAboutOpenRequest,
                    androidFullScreenSubmenus = androidSettingsNavigation,
                    onCategoryFocus = callbacks.onSettingsCategoryFocus,
                    onOptionFocus = callbacks.onSettingsOptionFocus,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                }
            }
        }
        if (session.activeSection == DashboardSection.LIVE) {
            AnimatedVisibility(
                visible = session.liveNavigationState.visible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) {
                TopNavigation(
                    state = navigationState,
                    onSelect = callbacks.onSectionChange,
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
        if (session.guideProgrammeDetailsVisible) {
            val focused = guideState.focusedProgramme(guideDataSource, guideTimeline(session.tick, model.guideLatestProgrammeEnd()))
            focused?.let { (channel: Channel, programme) ->
                val next = model.programmesFor(channel, programme.end, programme.end + 86_400_000L).firstOrNull()
                GuideProgrammeDetails(
                    GuideProgrammeDetailsUiState(
                        model.settings.language,
                        channel,
                        programme,
                        next,
                        session.guideProgrammeDialogState.focusedAction
                    ),
                    callbacks.onDismissGuideProgrammeDetails,
                    callbacks.onOpenGuideProgrammeChannel,
                    callbacks.onGuideProgrammeDialogEvent,
                    handleSystemBackKey = !androidSettingsNavigation
                )
            }
        }
    }
}
