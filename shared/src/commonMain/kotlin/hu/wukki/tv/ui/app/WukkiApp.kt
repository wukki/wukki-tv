package hu.wukki.tv.ui.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import hu.wukki.tv.AppFeedbackKind
import hu.wukki.tv.LiveVideoGestures
import hu.wukki.tv.PlaybackEngine
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.WukkiAppDependencies
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.guide.rememberEpgGuideState
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.LiveNavigationVisibilityEvent
import hu.wukki.tv.ui.navigation.restoredChannelIndex
import hu.wukki.tv.ui.navigation.toAppRemoteKey
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onPlatformBackActionChange: ((() -> Boolean)?) -> Unit = {},
    uiActive: Boolean = true,
    runForegroundRefreshes: Boolean = true,
    sharedModel: WukkiModel,
) {
    val model = sharedModel
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val autoPlayOnLaunch = model.settings.playback.autoPlayOnLaunch
    val guideState = rememberEpgGuideState()
    val guideDataSource = remember(model) { model.guideDataSource() }
    val baseDensity = LocalDensity.current
    val session = remember { AppSessionState(autoPlayOnLaunch) }
    val currentExitConfirmation = rememberUpdatedState(onExitConfirmation)
    val controller =
        remember(
            session,
            model,
            scope,
            guideState,
            guideDataSource,
            androidSettingsNavigation,
            requireDoubleBackToExit,
        ) {
            AppSessionController(
                session,
                model,
                scope,
                guideState,
                guideDataSource,
                androidSettingsNavigation,
                requireDoubleBackToExit,
            ) { message -> currentExitConfirmation.value(message) }
        }
    with(session) {
        with(controller) {
            PlaybackRecoveryDialog(session, model, playbackController) { activateSection(DashboardSection.CHANNELS) }
            val uiPolicy = uiLifecyclePolicy(activeSection, uiActive, runForegroundRefreshes)
            val visibleChannels = model.filteredChannels()
            val visibleChannelIds = remember(visibleChannels) { visibleChannels.map { it.id } }
            LaunchedEffect(visibleChannelIds, model.selectedChannelId) {
                channelListIndex =
                    restoredChannelIndex(
                        channelIds = visibleChannelIds,
                        savedChannelId = channelFocusedId,
                        selectedChannelId = model.selectedChannelId,
                        fallbackIndex = channelListIndex,
                    )
                channelFocusedId = visibleChannelIds.getOrNull(channelListIndex)
            }
            LaunchedEffect(channelListIndex, visibleChannelIds) {
                channelFocusedId = visibleChannelIds.getOrNull(channelListIndex)
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                officialSourceReady = true
            }
            LaunchedEffect(activeSection, uiPolicy.runClock) {
                if (!uiPolicy.runClock) return@LaunchedEffect
                tick = System.currentTimeMillis()
                while (true) {
                    delay(30_000)
                    tick = System.currentTimeMillis()
                }
            }
            LaunchedEffect(activeSection) {
                onActiveSectionChange(activeSection)
                if (activeSection == DashboardSection.LIVE) focusRequester.requestFocus()
            }
            LaunchedEffect(activeSection, liveNavigationState.visible, liveNavigationState.interactionSequence, uiPolicy.runTimeouts) {
                if (uiPolicy.runTimeouts && activeSection == DashboardSection.LIVE && liveNavigationState.visible) {
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
                model.settings.language,
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
                }
            }
            val feedbackToken = model.feedbackToken
            LaunchedEffect(feedbackToken, model.feedbackKind, uiPolicy.runTimeouts) {
                if (!uiPolicy.runTimeouts) return@LaunchedEffect
                val timeout =
                    when (model.feedbackKind) {
                        AppFeedbackKind.SUCCESS -> SUCCESS_FEEDBACK_TIMEOUT_MS
                        AppFeedbackKind.ERROR -> ERROR_FEEDBACK_TIMEOUT_MS
                        AppFeedbackKind.LOADING, null -> null
                    } ?: return@LaunchedEffect
                delay(timeout)
                model.dismissFeedback(feedbackToken)
            }
            LaunchedEffect(model.selectedChannelId, activeSection, overlayRequest, uiPolicy.runTimeouts) {
                if (uiPolicy.runTimeouts && activeSection == DashboardSection.LIVE && model.selectedChannel() != null) {
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
            LaunchedEffect(channelNumberInput, activeSection, uiPolicy.runTimeouts) {
                val pendingNumber = channelNumberInput
                if (uiPolicy.runTimeouts && activeSection == DashboardSection.LIVE && pendingNumber.isNotEmpty()) {
                    delay(3_000)
                    if (channelNumberInput == pendingNumber) {
                        val selected = model.selectChannelByNumber(pendingNumber)
                        channelNumberInput = ""
                        if (selected) overlayRequest++
                    }
                }
            }
            AutomaticRefreshEffects(model, uiPolicy.runAutomaticRefreshes)

            val overlayChannel = model.channelById(liveChannelPreviewState.channelId) ?: model.selectedChannel()
            val overlayUsesProgrammeData = activeSection == DashboardSection.LIVE || activeSection == DashboardSection.CHANNELS
            val overlayCurrent = overlayChannel?.takeIf { overlayUsesProgrammeData }?.let { model.currentProgram(it, tick) }
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
                playbackController.detail,
                playbackController.recovery,
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
                            showProgrammeImages = model.settings.display.showProgrammeImages,
                            playbackState = playbackController.state,
                            playbackDetail = playbackController.detail,
                            recovery = playbackController.recovery,
                        ),
                    )
                }
            }

            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * model.settings.display.uiScale)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                // Android forwards the system Back key to OnBackPressedDispatcher.
                                // Consuming it here too would advance the back hierarchy twice.
                                if (androidSettingsNavigation && event.key == Key.Back) return@onPreviewKeyEvent false
                                dispatchRemote(event.key.toAppRemoteKey())
                            },
                ) {
                    DashboardScreen(
                        session = session,
                        callbacks = callbacks,
                        model = model,
                        scope = scope,
                        guideDataSource = guideDataSource,
                        guideState = guideState,
                        androidSettingsNavigation = androidSettingsNavigation,
                        videoHost = videoHost,
                        liveVideoGestures = liveVideoGestures,
                        playbackEngineLabel = playbackEngineLabel,
                        playingChannelId = playbackController.successfullyPlayedChannelId,
                    )
                }
            }
        }
    }
}

private const val SUCCESS_FEEDBACK_TIMEOUT_MS = 3_000L
private const val ERROR_FEEDBACK_TIMEOUT_MS = 8_000L
private const val LIVE_NAVIGATION_TIMEOUT_MS = 5_000L
