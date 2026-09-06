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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
    sharedModel: WukkiModel? = null
) {
    val model = sharedModel ?: remember(dependencies) { dependencies.createModel() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val autoPlayOnLaunch = model.settings.playback.autoPlayOnLaunch != false
    val guideState = rememberEpgGuideState()
    val guideDataSource = remember(model) { model.guideDataSource() }
    val baseDensity = LocalDensity.current
    val session = remember { AppSessionState(autoPlayOnLaunch) }
    val controller = AppSessionController(session, model, scope, guideState, guideDataSource,
        androidSettingsNavigation, requireDoubleBackToExit, onExitConfirmation)
    with(session) {
        with(controller) {

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

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
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
                            dispatchRemote(event.key.toAppRemoteKey())
                        }
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
                        playbackEngineLabel = playbackEngineLabel
                    )
                }
            }
        }
    }
}

private const val SUCCESS_FEEDBACK_TIMEOUT_MS = 3_000L
private const val ERROR_FEEDBACK_TIMEOUT_MS = 8_000L
private const val LIVE_NAVIGATION_TIMEOUT_MS = 5_000L
