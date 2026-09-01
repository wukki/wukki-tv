package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.settings.*
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.*

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WukkiApp(
    dependencies: WukkiAppDependencies,
    playbackController: PlaybackEngine,
    videoHost: @Composable (Modifier, LiveVideoGestures?) -> Unit,
    playbackEngineLabel: String,
    onActiveSectionChange: (DashboardSection) -> Unit = {},
    androidSettingsNavigation: Boolean = false,
    requireDoubleBackToExit: Boolean = false,
    onExitConfirmation: (String) -> Unit = {},
    onPlatformBackActionChange: ((() -> Boolean)?) -> Unit = {}
) {
    val model = remember(dependencies) { dependencies.createModel() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    var settingsNavigation by remember { mutableStateOf(SettingsNavigationState()) }
    val autoPlayOnLaunch = model.settings.playback.autoPlayOnLaunch != false
    var activeSection by remember { mutableStateOf(if (autoPlayOnLaunch) DashboardSection.LIVE else DashboardSection.CHANNELS) }
    var focusZone by remember { mutableStateOf(TvFocusZone.CONTENT) }
    var mainNavigationIndex by remember { mutableIntStateOf(DashboardSection.entries.indexOf(activeSection).coerceAtLeast(0)) }
    var channelRemoteFocus by remember { mutableStateOf(ChannelRemoteFocus.LIST) }
    var channelFilterIndex by remember { mutableIntStateOf(0) }
    var channelListIndex by remember { mutableIntStateOf(0) }
    var channelFocusedId by remember { mutableStateOf<String?>(null) }
    var channelSearchOpen by remember { mutableStateOf(false) }
    var channelListOpenRequest by remember { mutableIntStateOf(if (activeSection == DashboardSection.CHANNELS) 1 else 0) }
    var settingsDropdownOpenRequest by remember { mutableIntStateOf(0) }
    var settingsDropdownOptionIndex by remember { mutableIntStateOf(-1) }
    var settingsAboutOpenRequest by remember { mutableIntStateOf(0) }
    var guideProgrammeDetailsVisible by remember { mutableStateOf(false) }
    var guideProgrammeDialogState by remember { mutableStateOf(GuideProgrammeDialogState()) }
    var automaticLaunchPending by remember { mutableStateOf(autoPlayOnLaunch) }
    var observedAutoPlaySetting by remember { mutableStateOf(autoPlayOnLaunch) }
    var officialSourceReady by remember { mutableStateOf(false) }
    var overlayRequest by remember { mutableIntStateOf(0) }
    var programmeOverlayVisible by remember { mutableStateOf(false) }
    var liveChannelPreviewState by remember { mutableStateOf(LiveChannelPreviewState()) }
    var liveNavigationState by remember { mutableStateOf(LiveNavigationVisibilityState()) }
    var exitConfirmationState by remember { mutableStateOf(ExitConfirmationState()) }
    var channelNumberInput by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    val guideState = rememberEpgGuideState()
    val guideDataSource = remember(model) { model.guideDataSource() }
    val baseDensity = LocalDensity.current
    val mainSections = DashboardSection.entries

    fun dismissLiveChannelPreview(hidePanel: Boolean = false) {
        if (liveChannelPreviewState.isActive) {
            liveChannelPreviewState = liveChannelPreviewState.copy(
                channelId = null,
                interactionSequence = liveChannelPreviewState.interactionSequence + 1
            )
        }
        if (hidePanel) programmeOverlayVisible = false
    }

    fun handleLiveNavigation(event: LiveNavigationVisibilityEvent) {
        val result = liveNavigationState.reduce(event)
        liveNavigationState = result.state
        when (result.effect) {
            LiveNavigationVisibilityEffect.NONE -> Unit
            LiveNavigationVisibilityEffect.FOCUS_NAVIGATION -> {
                mainNavigationIndex = mainSections.indexOf(DashboardSection.LIVE).coerceAtLeast(0)
                focusZone = TvFocusZone.MAIN_NAVIGATION
            }
            LiveNavigationVisibilityEffect.FOCUS_CONTENT_IF_NAVIGATION_FOCUSED -> {
                if (focusZone == TvFocusZone.MAIN_NAVIGATION) focusZone = TvFocusZone.CONTENT
            }
        }
    }

    fun activateSection(section: DashboardSection) {
        exitConfirmationState = ExitConfirmationState()
        val previousSection = activeSection
        if (section == DashboardSection.LIVE && previousSection != DashboardSection.LIVE) {
            handleLiveNavigation(LiveNavigationVisibilityEvent.EnterLive)
        } else if (section != DashboardSection.LIVE && previousSection == DashboardSection.LIVE) {
            handleLiveNavigation(LiveNavigationVisibilityEvent.LeaveLive)
        }
        if (section != DashboardSection.LIVE) dismissLiveChannelPreview(hidePanel = true)
        if (section == DashboardSection.CHANNELS && activeSection != DashboardSection.CHANNELS) {
            val visibleChannels = model.filteredChannels()
            channelListIndex = activeChannelIndex(visibleChannels.map { it.id }, model.selectedChannelId)
            channelFocusedId = visibleChannels.getOrNull(channelListIndex)?.id
            channelRemoteFocus = ChannelRemoteFocus.LIST
            channelListOpenRequest++
        }
        if (section == DashboardSection.GUIDE && activeSection != DashboardSection.GUIDE) {
            guideState.focusCurrentProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd()), tick)
        }
        if (androidSettingsNavigation && section == DashboardSection.SETTINGS && activeSection != DashboardSection.SETTINGS) {
            settingsNavigation = settingsNavigation.copy(section = null, option = null)
        }
        activeSection = section
        mainNavigationIndex = mainSections.indexOf(section).coerceAtLeast(0)
        focusZone = TvFocusZone.CONTENT
    }

    val visibleChannels = model.filteredChannels()
    val visibleChannelIds = remember(visibleChannels) { visibleChannels.map { it.id } }
    LaunchedEffect(visibleChannelIds, model.selectedChannelId) {
        channelListIndex = restoredChannelIndex(
            channelIds = visibleChannelIds,
            savedChannelId = channelFocusedId,
            selectedChannelId = model.selectedChannelId,
            fallbackIndex = channelListIndex
        )
        channelFocusedId = visibleChannelIds.getOrNull(channelListIndex)
    }
    LaunchedEffect(channelListIndex, visibleChannelIds) {
        channelFocusedId = visibleChannelIds.getOrNull(channelListIndex)
    }

    fun selectChannelPreview(channelId: String) {
        val index = visibleChannelIds.indexOf(channelId)
        if (index < 0) return
        channelListIndex = index
        channelFocusedId = channelId
        channelRemoteFocus = ChannelRemoteFocus.LIST
    }

    fun openChannelFromBrowser(channelId: String) {
        model.selectChannel(channelId)
        activateSection(DashboardSection.LIVE)
        overlayRequest++
    }

    fun openGuideProgrammeChannel(channelId: String) {
        model.selectChannel(channelId)
        guideProgrammeDetailsVisible = false
        activateSection(DashboardSection.LIVE)
        overlayRequest++
    }

    fun switchLiveChannel(delta: Int) {
        channelNumberInput = ""
        dismissLiveChannelPreview()
        model.moveChannel(delta)
        overlayRequest++
    }

    fun toggleLiveProgrammeInfo() {
        if (programmeOverlayVisible) {
            dismissLiveChannelPreview(hidePanel = true)
        } else {
            dismissLiveChannelPreview()
            overlayRequest++
        }
    }

    fun handleLiveChannelPreview(event: LiveChannelPreviewEvent): Boolean {
        val result = liveChannelPreviewState.reduce(
            event = event,
            channelIds = model.filteredChannels().map { it.id },
            activeChannelId = model.selectedChannelId,
            panelVisible = programmeOverlayVisible
        )
        liveChannelPreviewState = result.state
        when (val effect = result.effect) {
            LiveChannelPreviewEffect.None -> if (result.handled) overlayRequest++
            LiveChannelPreviewEffect.Dismiss -> programmeOverlayVisible = false
            is LiveChannelPreviewEffect.OpenChannel -> {
                if (effect.channelId != model.selectedChannelId) model.selectChannel(effect.channelId)
                overlayRequest++
            }
        }
        return result.handled
    }

    val liveVideoGestures = LiveVideoGestures(
        onTap = ::toggleLiveProgrammeInfo,
        onNextChannel = { switchLiveChannel(1) },
        onPreviousChannel = { switchLiveChannel(-1) },
        onShowNavigation = {
            handleLiveNavigation(LiveNavigationVisibilityEvent.Reveal(focusNavigation = false))
        }
    )

    fun showGuideProgrammeDetails() {
        if (guideState.focusedProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd())) != null) {
            guideProgrammeDetailsVisible = true
            guideProgrammeDialogState = GuideProgrammeDialogState()
        }
    }

    fun handleGuideProgrammeDialogEvent(dialogEvent: GuideProgrammeDialogEvent) {
        val transition = guideProgrammeDialogState.reduce(dialogEvent)
        guideProgrammeDialogState = transition.state
        when (transition.effect) {
            GuideProgrammeDialogEffect.DISMISS -> guideProgrammeDetailsVisible = false
            GuideProgrammeDialogEffect.OPEN_CHANNEL -> guideState.focusedProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd()))?.first?.let { channel ->
                openGuideProgrammeChannel(channel.id)
            } ?: run { guideProgrammeDetailsVisible = false }
            GuideProgrammeDialogEffect.NONE -> Unit
        }
    }

    fun cycleRefresh(section: SettingsSection, delta: Int) {
        val isEpg = section == SettingsSection.EPG
        val intervals = if (isEpg) RefreshInterval.entries.toList() else {
            listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY)
        }
        val current = if (isEpg) model.settings.epgRefresh else model.settings.playlistRefresh
        val next = intervals[(intervals.indexOf(current).coerceAtLeast(0) + delta).mod(intervals.size)]
        if (isEpg) model.setEpgRefresh(next) else model.setPlaylistRefresh(next)
    }

    fun applySettingsEffect(effect: SettingsNavigationEffect) {
        when (effect) {
            SettingsNavigationEffect.None -> Unit
            SettingsNavigationEffect.ExitToMainMenu -> focusZone = TvFocusZone.MAIN_NAVIGATION
            is SettingsNavigationEffect.Adjust -> when (val option = effect.option) {
                is PlaybackSettingsOption -> model.updatePlayback { current ->
                    when (option) {
                        PlaybackSettingsOption.AUTOPLAY -> current.copy(autoPlayOnLaunch = !(current.autoPlayOnLaunch != false))
                        PlaybackSettingsOption.VOLUME -> current.copy(volume = (current.volume + effect.delta * 5).coerceIn(0, 100))
                        PlaybackSettingsOption.BUFFER -> current.copy(bufferProfile = BufferProfile.entries[(current.bufferProfile.ordinal + effect.delta).mod(BufferProfile.entries.size)])
                        PlaybackSettingsOption.ASPECT_RATIO -> current.copy(aspectRatio = AspectRatioMode.entries[((current.aspectRatio ?: AspectRatioMode.AUTO).ordinal + effect.delta).mod(AspectRatioMode.entries.size)])
                        PlaybackSettingsOption.RECONNECT -> current.copy(autoReconnect = !current.autoReconnect)
                        PlaybackSettingsOption.RETRIES -> current.copy(reconnectAttempts = (current.reconnectAttempts + effect.delta).coerceIn(1, 10))
                    }
                }
                is DisplaySettingsOption -> adjustDisplayOption(model, option, effect.delta)
                EpgSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.EPG, effect.delta)
                PlaylistSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.PLAYLISTS, effect.delta)
                LanguageSettingsOption.LANGUAGE -> model.setLanguage(if (model.settings.language == AppLanguage.HUNGARIAN) AppLanguage.ENGLISH else AppLanguage.HUNGARIAN)
                EpgSettingsOption.REFRESH, PlaylistSettingsOption.REFRESH -> Unit
                is ParentalSettingsOption, is AboutSettingsOption -> Unit
            }
            is SettingsNavigationEffect.Activate -> when (val option = effect.option) {
                PlaybackSettingsOption.AUTOPLAY -> model.updatePlayback { it.copy(autoPlayOnLaunch = !(it.autoPlayOnLaunch != false)) }
                PlaybackSettingsOption.BUFFER, PlaybackSettingsOption.ASPECT_RATIO, LanguageSettingsOption.LANGUAGE -> {
                    settingsDropdownOptionIndex = settingsNavigation.optionIndex
                    settingsDropdownOpenRequest++
                }
                PlaybackSettingsOption.RECONNECT -> model.updatePlayback { it.copy(autoReconnect = !it.autoReconnect) }
                PlaybackSettingsOption.RETRIES -> model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) }
                PlaybackSettingsOption.VOLUME -> Unit
                is DisplaySettingsOption -> adjustDisplayOption(model, option, 1)
                EpgSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.EPG, 1)
                EpgSettingsOption.REFRESH -> scope.launch { model.refreshOfficialEpg() }
                PlaylistSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.PLAYLISTS, 1)
                PlaylistSettingsOption.REFRESH -> scope.launch { model.refreshOfficialPlaylist() }
                is AboutSettingsOption -> if (
                    option == AboutSettingsOption.PRIVACY || option == AboutSettingsOption.LICENSES
                ) settingsAboutOpenRequest++
                is ParentalSettingsOption -> Unit
            }
        }
    }

    fun handleBackNavigation(): Boolean {
        if (activeSection == DashboardSection.LIVE && !liveNavigationState.visible) {
            exitConfirmationState = ExitConfirmationState()
            handleLiveNavigation(LiveNavigationVisibilityEvent.Reveal(focusNavigation = true))
            return true
        }
        val effect = AppBackNavigationState(
            guideDialogVisible = guideProgrammeDetailsVisible,
            channelSearchOpen = activeSection == DashboardSection.CHANNELS && channelSearchOpen,
            liveOverlayVisible = activeSection == DashboardSection.LIVE &&
                (liveChannelPreviewState.isActive || programmeOverlayVisible),
            settingsDetailOpen = activeSection == DashboardSection.SETTINGS && settingsNavigation.section != null,
            focusZone = focusZone
        ).reduce()
        return when (effect) {
            AppBackNavigationEffect.DISMISS_GUIDE_DIALOG -> {
                exitConfirmationState = ExitConfirmationState()
                guideProgrammeDetailsVisible = false
                true
            }
            AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH -> {
                exitConfirmationState = ExitConfirmationState()
                model.setChannelQuery("")
                channelSearchOpen = false
                channelRemoteFocus = ChannelRemoteFocus.LIST
                true
            }
            AppBackNavigationEffect.DISMISS_LIVE_OVERLAY -> {
                exitConfirmationState = ExitConfirmationState()
                dismissLiveChannelPreview(hidePanel = true)
                true
            }
            AppBackNavigationEffect.CLOSE_SETTINGS_DETAIL -> {
                exitConfirmationState = ExitConfirmationState()
                settingsNavigation = settingsNavigation.copy(section = null, option = null)
                true
            }
            AppBackNavigationEffect.FOCUS_MAIN_NAVIGATION -> {
                exitConfirmationState = ExitConfirmationState()
                mainNavigationIndex = mainSections.indexOf(activeSection).coerceAtLeast(0)
                focusZone = TvFocusZone.MAIN_NAVIGATION
                true
            }
            AppBackNavigationEffect.EXIT_APPLICATION -> {
                if (!requireDoubleBackToExit) return false
                val result = exitConfirmationState.requestExit(System.currentTimeMillis())
                exitConfirmationState = result.state
                when (result.effect) {
                    ExitConfirmationEffect.SHOW_HINT -> {
                        onExitConfirmation(tr(model.settings.language, "app.exit.confirm"))
                        true
                    }
                    ExitConfirmationEffect.EXIT -> false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        model.refreshOfficialPlaylist(showFeedback = false)
        officialSourceReady = true
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    LaunchedEffect(activeSection) {
        onActiveSectionChange(activeSection)
        if (activeSection == DashboardSection.LIVE) focusRequester.requestFocus()
    }
    LaunchedEffect(activeSection, liveNavigationState.visible, liveNavigationState.interactionSequence) {
        if (activeSection == DashboardSection.LIVE && liveNavigationState.visible) {
            delay(LIVE_NAVIGATION_TIMEOUT_MS)
            handleLiveNavigation(LiveNavigationVisibilityEvent.Timeout)
        }
    }
    LaunchedEffect(activeSection, settingsNavigation.section) {
        if (activeSection == DashboardSection.SETTINGS && settingsNavigation.section == SettingsSection.ABOUT && deviceInfo == null) {
            deviceInfo = withContext(Dispatchers.Default) { dependencies.deviceInfoProvider.collect() }
        }
    }
    val currentBackHandler = rememberUpdatedState { handleBackNavigation() }
    val currentBackRegistrar = rememberUpdatedState(onPlatformBackActionChange)
    DisposableEffect(Unit) {
        currentBackRegistrar.value { currentBackHandler.value() }
        onDispose { currentBackRegistrar.value(null) }
    }
    DisposableEffect(playbackController) {
        onDispose { playbackController.release() }
    }
    LaunchedEffect(
        model.playbackRequestToken,
        model.selectedChannelId,
        model.settings.playback,
        model.settings.display.showLogos,
        model.settings.language
    ) {
        if (model.playbackRequestToken > 0) {
            playbackController.play(model.selectedChannel(), model.settings.playback, model.settings.display.showLogos, model.settings.language)
        }
    }
    LaunchedEffect(autoPlayOnLaunch) {
        if (!observedAutoPlaySetting && autoPlayOnLaunch) {
            if (officialSourceReady) model.requestPlayback() else automaticLaunchPending = true
        }
        observedAutoPlaySetting = autoPlayOnLaunch
    }
    LaunchedEffect(officialSourceReady) {
        if (!officialSourceReady) return@LaunchedEffect
        if (autoPlayOnLaunch && model.selectedChannel() != null) {
            model.requestPlayback()
        } else if (autoPlayOnLaunch) {
            automaticLaunchPending = false
            activeSection = DashboardSection.CHANNELS
        }
    }
    LaunchedEffect(playbackController.successfullyPlayedChannelId) {
        playbackController.successfullyPlayedChannelId?.let { channelId ->
            model.markChannelPlaybackSuccessful(channelId)
            automaticLaunchPending = false
        }
    }
    LaunchedEffect(playbackController.state, automaticLaunchPending) {
        if (automaticLaunchPending && playbackController.state == PlaybackState.ERROR) {
            automaticLaunchPending = false
            activeSection = DashboardSection.CHANNELS
            model.showRawError(playbackController.detail ?: tr(model.settings.language, "playback.error"))
        }
    }
    val feedbackToken = model.feedbackToken
    LaunchedEffect(feedbackToken, model.feedbackKind) {
        val timeout = when (model.feedbackKind) {
            AppFeedbackKind.SUCCESS -> SUCCESS_FEEDBACK_TIMEOUT_MS
            AppFeedbackKind.ERROR -> ERROR_FEEDBACK_TIMEOUT_MS
            AppFeedbackKind.LOADING, null -> null
        } ?: return@LaunchedEffect
        delay(timeout)
        model.dismissFeedback(feedbackToken)
    }
    LaunchedEffect(model.selectedChannelId, activeSection, overlayRequest) {
        if (activeSection == DashboardSection.LIVE && model.selectedChannel() != null) {
            programmeOverlayVisible = true
            delay(5_000)
            if (liveChannelPreviewState.isActive) {
                handleLiveChannelPreview(LiveChannelPreviewEvent.TIMEOUT)
            } else {
                programmeOverlayVisible = false
            }
        } else {
            dismissLiveChannelPreview()
            programmeOverlayVisible = false
        }
    }
    LaunchedEffect(channelNumberInput, activeSection) {
        val pendingNumber = channelNumberInput
        if (activeSection == DashboardSection.LIVE && pendingNumber.isNotEmpty()) {
            delay(3_000)
            if (channelNumberInput == pendingNumber) {
                val selected = model.selectChannelByNumber(pendingNumber)
                channelNumberInput = ""
                if (selected) overlayRequest++
            }
        }
    }
    AutomaticRefreshEffects(model)

    val overlayChannel = model.channelById(liveChannelPreviewState.channelId) ?: model.selectedChannel()
    val overlayCurrent = overlayChannel?.let { model.currentProgram(it, tick) }
    val overlayNext = overlayChannel?.let { channel -> overlayCurrent?.let { model.nextProgram(channel, it) } }
    LaunchedEffect(
        overlayChannel,
        overlayCurrent,
        overlayNext,
        tick,
        activeSection,
        programmeOverlayVisible,
        channelNumberInput,
        model.settings.language,
        model.settings.display.showLogos,
        model.settings.display.showProgrammeImages,
        playbackController.state,
        playbackController.detail
    ) {
        overlayChannel?.let { channel ->
            playbackController.updateOverlay(
                playbackOverlayData(
                    channel = channel,
                    currentProgramme = overlayCurrent,
                    nextProgramme = overlayNext,
                    now = tick,
                    section = activeSection,
                    showProgrammeInfo = programmeOverlayVisible,
                    channelNumberInput = channelNumberInput,
                    language = model.settings.language,
                    showLogos = model.settings.display.showLogos,
                    showProgrammeImages = model.settings.display.showProgrammeImages != false,
                    playbackState = playbackController.state,
                    playbackDetail = playbackController.detail
                )
            )
        }
    }

    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * model.settings.display.uiScale)) {
        Column(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Android forwards the system Back key to OnBackPressedDispatcher.
                    // Consuming it here too would advance the back hierarchy twice.
                    if (androidSettingsNavigation && event.key == Key.Back) return@onPreviewKeyEvent false
                    if (!event.key.isBackKey()) exitConfirmationState = ExitConfirmationState()
                    if (guideProgrammeDetailsVisible) {
                        val dialogEvent = when {
                            event.key.isBackKey() -> GuideProgrammeDialogEvent.BACK
                            event.key == Key.DirectionLeft -> GuideProgrammeDialogEvent.LEFT
                            event.key == Key.DirectionRight -> GuideProgrammeDialogEvent.RIGHT
                            event.key.isConfirmKey() -> GuideProgrammeDialogEvent.CONFIRM
                            else -> null
                        }
                        if (dialogEvent != null) {
                            handleGuideProgrammeDialogEvent(dialogEvent)
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    if (activeSection == DashboardSection.CHANNELS && channelRemoteFocus == ChannelRemoteFocus.SEARCH) {
                        if (event.key == Key.Escape) {
                            model.setChannelQuery("")
                            channelSearchOpen = false
                            channelRemoteFocus = ChannelRemoteFocus.LIST
                            return@onPreviewKeyEvent true
                        }
                        if (event.key == Key.Backspace) {
                            // Let the focused text field consume Backspace character-by-character.
                            // For an empty value this is intentionally a no-op, not global Back navigation.
                            return@onPreviewKeyEvent false
                        }
                    }
                    if (event.key.isBackKey()) {
                        return@onPreviewKeyEvent handleBackNavigation()
                    }
                    if (activeSection == DashboardSection.LIVE) {
                        event.key.livePreviewEvent()?.let { previewEvent ->
                            handleLiveChannelPreview(previewEvent)
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (focusZone == TvFocusZone.MAIN_NAVIGATION) {
                        val wasLive = activeSection == DashboardSection.LIVE
                        val remoteKey = event.key.toRemoteKey() ?: return@onPreviewKeyEvent false
                        val result = MainMenuNavigationState(mainNavigationIndex).reduce(remoteKey, mainSections.size)
                        mainNavigationIndex = result.state.index
                        when (val effect = result.effect) {
                            MainMenuNavigationEffect.None -> Unit
                            MainMenuNavigationEffect.EnterContent -> focusZone = TvFocusZone.CONTENT
                            is MainMenuNavigationEffect.Activate -> activateSection(mainSections[effect.index])
                        }
                        if (result.handled && wasLive && activeSection == DashboardSection.LIVE) {
                            handleLiveNavigation(LiveNavigationVisibilityEvent.Interact)
                        }
                        return@onPreviewKeyEvent result.handled
                    }
                    if (activeSection == DashboardSection.GUIDE) {
                        if (event.key.isConfirmKey()) {
                            showGuideProgrammeDetails()
                            return@onPreviewKeyEvent true
                        }
                        if (guideState.handleKey(event.key, guideDataSource, scope, guideTimeline(tick, model.guideLatestProgrammeEnd()))) return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.LIVE && event.key.isConfirmKey()) {
                        if (channelNumberInput.isNotEmpty()) {
                            dismissLiveChannelPreview()
                            val selected = model.selectChannelByNumber(channelNumberInput)
                            channelNumberInput = ""
                            if (selected) overlayRequest++
                        } else if (liveChannelPreviewState.isActive) {
                            handleLiveChannelPreview(LiveChannelPreviewEvent.CONFIRM)
                        } else {
                            overlayRequest++
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.SETTINGS) {
                        val remoteKey = event.key.toRemoteKey() ?: return@onPreviewKeyEvent false
                        val result = settingsNavigation.reduce(remoteKey)
                        settingsNavigation = result.state
                        applySettingsEffect(result.effect)
                        return@onPreviewKeyEvent result.handled
                    }
                    if (activeSection == DashboardSection.CHANNELS) {
                        val filterCount = model.categories().size + 2
                        val remoteKey = event.key.toRemoteKey() ?: return@onPreviewKeyEvent false
                        val result = ChannelNavigationState(channelRemoteFocus, channelFilterIndex, channelListIndex).reduce(
                            remoteKey, filterCount, model.filteredChannels().size, model.query.isNotEmpty()
                        )
                        channelRemoteFocus = result.state.focus
                        channelFilterIndex = result.state.filterIndex
                        channelListIndex = result.state.channelIndex
                        when (val effect = result.effect) {
                            ChannelNavigationEffect.None -> Unit
                            ChannelNavigationEffect.ExitToMainMenu -> focusZone = TvFocusZone.MAIN_NAVIGATION
                            is ChannelNavigationEffect.ActivateFilter -> {
                                when (effect.index) {
                                    0 -> model.showAllChannels()
                                    1 -> model.showFavoriteChannels()
                                    else -> model.showChannelCategory(model.categories()[effect.index - 2])
                                }
                                channelListIndex = 0
                            }
                            is ChannelNavigationEffect.OpenChannel -> model.filteredChannels().getOrNull(effect.index)?.let {
                                openChannelFromBrowser(it.id)
                            }
                            is ChannelNavigationEffect.ToggleFavorite -> model.filteredChannels().getOrNull(effect.index)?.let { model.toggleFavorite(it.id) }
                        }
                        return@onPreviewKeyEvent result.handled
                    }
                    val digit = when (event.key) {
                        Key.One, Key.NumPad1 -> "1"
                        Key.Two, Key.NumPad2 -> "2"
                        Key.Three, Key.NumPad3 -> "3"
                        Key.Four, Key.NumPad4 -> "4"
                        Key.Five, Key.NumPad5 -> "5"
                        Key.Six, Key.NumPad6 -> "6"
                        Key.Seven, Key.NumPad7 -> "7"
                        Key.Eight, Key.NumPad8 -> "8"
                        Key.Nine, Key.NumPad9 -> "9"
                        Key.Zero, Key.NumPad0 -> "0"
                        else -> null
                    }
                    if (digit != null) {
                        if (activeSection != DashboardSection.LIVE) return@onPreviewKeyEvent false
                        dismissLiveChannelPreview(hidePanel = true)
                        channelNumberInput = (channelNumberInput + digit).take(4)
                        return@onPreviewKeyEvent true
                    }
                    val liveChannelDelta = event.key.liveImmediateChannelDelta()
                        ?: return@onPreviewKeyEvent false
                    switchLiveChannel(liveChannelDelta)
                    true
                }
        ) {
            DashboardScreen(
                model = model,
                scope = scope,
                tick = tick,
                activeSection = activeSection,
                liveNavigationVisible = liveNavigationState.visible,
                guideDataSource = guideDataSource,
                guideState = guideState,
                onSectionChange = { section ->
                    if (section == DashboardSection.LIVE && activeSection == DashboardSection.LIVE) {
                        handleLiveNavigation(LiveNavigationVisibilityEvent.Interact)
                    }
                    activateSection(section)
                },
                settingsSection = settingsNavigation.section,
                onSettingsSectionChange = { section ->
                    val categoryIndex = section?.let { SettingsSection.entries.indexOf(it).coerceAtLeast(0) }
                        ?: settingsNavigation.categoryIndex
                    settingsNavigation = SettingsNavigationState(
                        section = section,
                        categoryIndex = categoryIndex,
                        option = section?.options()?.firstOrNull()
                    )
                },
                mainNavigationFocused = focusZone == TvFocusZone.MAIN_NAVIGATION,
                mainNavigationSection = mainSections[mainNavigationIndex],
                channelRemoteFocus = channelRemoteFocus,
                channelFilterIndex = channelFilterIndex,
                channelListIndex = channelListIndex,
                channelListOpenRequest = channelListOpenRequest,
                channelPreviewId = channelFocusedId ?: model.selectedChannelId,
                onChannelPreviewSelect = ::selectChannelPreview,
                onOpenChannel = ::openChannelFromBrowser,
                channelSearchOpen = channelSearchOpen,
                onChannelSearchOpenChange = { open ->
                    channelSearchOpen = open
                    if (open) {
                        channelRemoteFocus = ChannelRemoteFocus.SEARCH
                    } else if (channelRemoteFocus == ChannelRemoteFocus.SEARCH) {
                        channelRemoteFocus = ChannelRemoteFocus.LIST
                    }
                },
                settingsCategoryIndex = settingsNavigation.categoryIndex,
                settingsOptionIndex = settingsNavigation.optionIndex,
                settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                settingsAboutOpenRequest = settingsAboutOpenRequest,
                androidSettingsNavigation = androidSettingsNavigation,
                onSettingsCategoryFocus = { index ->
                    settingsNavigation = settingsNavigation.copy(categoryIndex = index.coerceIn(0, SettingsSection.entries.lastIndex))
                },
                onSettingsOptionFocus = { index ->
                    settingsNavigation.section?.options()?.getOrNull(index)?.let { option ->
                        settingsNavigation = settingsNavigation.copy(option = option)
                    }
                },
                guideProgrammeDetailsVisible = guideProgrammeDetailsVisible,
                onShowGuideProgrammeDetails = ::showGuideProgrammeDetails,
                onDismissGuideProgrammeDetails = { guideProgrammeDetailsVisible = false },
                onOpenGuideProgrammeChannel = ::openGuideProgrammeChannel,
                onGuideProgrammeDialogEvent = ::handleGuideProgrammeDialogEvent,
                guideProgrammeDialogAction = guideProgrammeDialogState.focusedAction,
                videoHost = videoHost,
                liveVideoGestures = liveVideoGestures,
                playbackEngineLabel = playbackEngineLabel,
                deviceInfo = deviceInfo
            )
        }
    }
}

private fun adjustDisplayOption(model: WukkiModel, option: DisplaySettingsOption, delta: Int) = model.updateDisplay { display ->
    when (option) {
        DisplaySettingsOption.UI_SCALE -> {
            val values = listOf(.9f, 1f, 1.15f)
            val current = values.indexOf(display.uiScale).coerceAtLeast(0)
            display.copy(uiScale = values[(current + delta).mod(values.size)])
        }
        DisplaySettingsOption.CHANNEL_LIST -> {
            val current = (display.channelListMode ?: ChannelListDisplayMode.NORMAL).ordinal
            display.copy(channelListMode = ChannelListDisplayMode.entries[(current + delta).mod(ChannelListDisplayMode.entries.size)])
        }
        DisplaySettingsOption.PROGRAMME -> display.copy(showChannelProgramme = !display.showChannelProgramme)
        DisplaySettingsOption.MINI_GUIDE -> display.copy(showMiniGuide = !display.showMiniGuide)
        DisplaySettingsOption.LOGOS -> display.copy(showLogos = !display.showLogos)
        DisplaySettingsOption.PROGRAMME_IMAGES -> display.copy(showProgrammeImages = !(display.showProgrammeImages != false))
    }
}

private const val SUCCESS_FEEDBACK_TIMEOUT_MS = 3_000L
private const val ERROR_FEEDBACK_TIMEOUT_MS = 8_000L
private const val LIVE_NAVIGATION_TIMEOUT_MS = 5_000L
