package hu.wukki.tv.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.wukki.tv.Channel
import hu.wukki.tv.LiveVideoGestures
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.channels.ChannelBrowserCallbacks
import hu.wukki.tv.ui.channels.ChannelBrowserRowUiState
import hu.wukki.tv.ui.channels.ChannelBrowserScreen
import hu.wukki.tv.ui.channels.ChannelBrowserUiState
import hu.wukki.tv.ui.channels.ChannelPreviewUiState
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.text
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.guide.EpgGuideScreen
import hu.wukki.tv.ui.guide.EpgGuideState
import hu.wukki.tv.ui.guide.GuideProgrammeDetails
import hu.wukki.tv.ui.guide.GuideProgrammeDetailsUiState
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.guide.guideTimeline
import hu.wukki.tv.ui.live.LiveTvScreen
import hu.wukki.tv.ui.live.LiveTvUiState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.NavigationEntryUiState
import hu.wukki.tv.ui.navigation.SideNavigation
import hu.wukki.tv.ui.navigation.SideNavigationUiState
import hu.wukki.tv.ui.settings.SettingsScreen
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.coroutines.CoroutineScope

private fun navigationState(model: WukkiModel, activeSection: DashboardSection, focusedSection: DashboardSection?, tick: Long): SideNavigationUiState {
    val language = model.settings.language
    return SideNavigationUiState(
        entries = listOf(
            NavigationEntryUiState(DashboardSection.LIVE, tr(language, "nav.live")),
            NavigationEntryUiState(DashboardSection.GUIDE, tr(language, "nav.guide")),
            NavigationEntryUiState(DashboardSection.CHANNELS, tr(language, "nav.channels")),
            NavigationEntryUiState(DashboardSection.SETTINGS, tr(language, "nav.settings"))
        ),
        activeSection = activeSection,
        focusedSection = focusedSection,
        timeLabel = formatTime(tick),
        dateLabel = hu.wukki.tv.ui.components.Localizer.formatSidebarDate(language, tick, tr(language, "date.sidebar.pattern"))
    )
}

private fun channelBrowserUiState(model: WukkiModel, tick: Long): ChannelBrowserUiState {
    val rows = model.filteredChannels().mapIndexed { index, channel ->
        val current = model.currentProgram(channel, tick)
        ChannelBrowserRowUiState(channel, index + 1, current, current?.let { model.nextProgram(channel, it) })
    }
    val selected = model.selectedChannel()
    return ChannelBrowserUiState(
        language = model.settings.language,
        categories = model.categories(),
        query = model.query,
        selectedCategory = model.category,
        onlyFavorites = model.onlyFavorites,
        channels = rows,
        selectedChannelId = model.selectedChannelId,
        displayMode = model.settings.display.channelListMode ?: hu.wukki.tv.ChannelListDisplayMode.NORMAL,
        showChannelProgramme = model.settings.display.showChannelProgramme,
        showMiniGuide = model.settings.display.showMiniGuide,
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
    channelSearchOpen: Boolean,
    onChannelSearchOpenChange: (Boolean) -> Unit,
    settingsCategoryIndex: Int,
    settingsOptionIndex: Int,
    androidSettingsNavigation: Boolean,
    useExpandedDesktopNavigation: Boolean,
    onSettingsCategoryFocus: (Int) -> Unit,
    onSettingsOptionFocus: (Int) -> Unit,
    guideProgrammeDetailsVisible: Boolean,
    onShowGuideProgrammeDetails: () -> Unit,
    onDismissGuideProgrammeDetails: () -> Unit,
    onOpenGuideProgrammeChannel: (String) -> Unit,
    onGuideProgrammeDialogEvent: (GuideProgrammeDialogEvent) -> Unit,
    videoHost: @Composable (Modifier, LiveVideoGestures?) -> Unit,
    liveVideoGestures: LiveVideoGestures,
    playbackEngineLabel: String
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / 1470f, maxHeight.value / 920f).coerceIn(.70f, 1.45f)
        val expandedDesktopNavigation = useExpandedDesktopNavigation && maxWidth >= 980.dp
        val navigationWidth = if (expandedDesktopNavigation) (256.dp * scale).coerceIn(220.dp, 430.dp) else 80.dp
        val padding = (14.dp * scale).coerceIn(8.dp, 20.dp)
        Row(Modifier.fillMaxSize()) {
            SideNavigation(
                state = navigationState(model, activeSection, mainNavigationSection.takeIf { mainNavigationFocused }, tick),
                onSelect = onSectionChange,
                scale = scale,
                expandedDesktop = expandedDesktopNavigation,
                modifier = Modifier.width(navigationWidth).fillMaxHeight()
            )
            when (activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(
                    LiveTvUiState(model.selectedChannel() != null, tr(model.settings.language, "live.empty")),
                    scale,
                    video = { videoHost(Modifier.fillMaxSize(), liveVideoGestures) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                DashboardSection.GUIDE -> EpgGuideScreen(
                    model.guideDataSource(), tick, guideState, onProgrammeClick = { _, _ -> onShowGuideProgrammeDetails() },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(padding)
                )
                DashboardSection.CHANNELS -> ChannelBrowserScreen(
                    state = channelBrowserUiState(model, tick),
                    callbacks = ChannelBrowserCallbacks(
                        onQueryChange = { model.query = it },
                        onSelectAll = { model.category = null; model.onlyFavorites = false },
                        onSelectFavorites = { model.category = null; model.onlyFavorites = true },
                        onSelectCategory = { model.onlyFavorites = false; model.category = it },
                        onSelectChannel = model::selectChannel,
                        onToggleFavorite = model::toggleFavorite
                    ),
                    tick = tick,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(padding),
                    scale = scale.coerceAtMost(1f),
                    remoteFocus = channelRemoteFocus,
                    remoteFilterIndex = channelFilterIndex,
                    remoteListIndex = channelListIndex,
                    listOpenRequest = channelListOpenRequest,
                    searchOpen = channelSearchOpen,
                    onSearchOpenChange = onChannelSearchOpenChange,
                    videoPreview = { videoHost(Modifier.fillMaxSize(), null) }
                )
                DashboardSection.SETTINGS -> SettingsScreen(
                    model = model, scope = scope, selectedSection = settingsSection,
                    onSectionChange = onSettingsSectionChange, remoteCategoryIndex = settingsCategoryIndex,
                    remoteNavigationActive = !mainNavigationFocused, remoteOptionIndex = settingsOptionIndex,
                    androidFullScreenSubmenus = androidSettingsNavigation,
                    onCategoryFocus = onSettingsCategoryFocus,
                    onOptionFocus = onSettingsOptionFocus,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(padding),
                    playbackEngineLabel = playbackEngineLabel
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
                    GuideProgrammeDetailsUiState(model.settings.language, channel, programme, next, model.settings.display.showProgrammeImages != false),
                    onDismissGuideProgrammeDetails, onOpenGuideProgrammeChannel, onGuideProgrammeDialogEvent
                )
            }
        }
    }
}
