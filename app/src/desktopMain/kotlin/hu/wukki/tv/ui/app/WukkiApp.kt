package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.settings.*
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.TvFocusZone
import hu.wukki.tv.ui.navigation.isBackKey
import hu.wukki.tv.ui.navigation.isConfirmKey
import hu.wukki.tv.ui.navigation.activeChannelIndex
import hu.wukki.tv.ui.navigation.restoredChannelIndex

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
import kotlin.math.ceil

@Composable
fun WukkiApp() {
    val model = remember { WukkiModel() }
    val playbackController = remember { PlaybackController(model.settings.language) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
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
    var settingsCategoryIndex by remember { mutableIntStateOf(0) }
    var settingsOptionIndex by remember { mutableIntStateOf(0) }
    var guideProgrammeDetailsVisible by remember { mutableStateOf(false) }
    var guideProgrammeDialogState by remember { mutableStateOf(GuideProgrammeDialogState()) }
    var automaticLaunchPending by remember { mutableStateOf(autoPlayOnLaunch) }
    var observedAutoPlaySetting by remember { mutableStateOf(autoPlayOnLaunch) }
    var overlayRequest by remember { mutableIntStateOf(0) }
    var programmeOverlayVisible by remember { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    val guideState = rememberEpgGuideState()
    val baseDensity = LocalDensity.current
    val mainSections = DashboardSection.entries

    fun activateSection(section: DashboardSection) {
        if (section == DashboardSection.CHANNELS && activeSection != DashboardSection.CHANNELS) {
            val visibleChannels = model.filteredChannels()
            channelListIndex = activeChannelIndex(visibleChannels.map { it.id }, model.selectedChannelId)
            channelFocusedId = visibleChannels.getOrNull(channelListIndex)?.id
            channelRemoteFocus = ChannelRemoteFocus.LIST
            channelListOpenRequest++
        }
        activeSection = section
        mainNavigationIndex = mainSections.indexOf(section).coerceAtLeast(0)
        focusZone = TvFocusZone.CONTENT
    }

    val visibleChannelIds = model.filteredChannels().map { it.id }
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

    fun openGuideProgrammeChannel(channelId: String) {
        model.selectChannel(channelId)
        guideProgrammeDetailsVisible = false
        activateSection(DashboardSection.LIVE)
        overlayRequest++
    }

    fun showGuideProgrammeDetails() {
        if (guideState.focusedProgramme(model.guideDataSource(), guideTimeline(tick, model.guideLatestProgrammeEnd())) != null) {
            guideProgrammeDetailsVisible = true
            guideProgrammeDialogState = GuideProgrammeDialogState()
        }
    }

    fun handleGuideProgrammeDialogEvent(dialogEvent: GuideProgrammeDialogEvent) {
        val transition = guideProgrammeDialogState.reduce(dialogEvent)
        guideProgrammeDialogState = transition.state
        when (transition.effect) {
            GuideProgrammeDialogEffect.DISMISS -> guideProgrammeDetailsVisible = false
            GuideProgrammeDialogEffect.OPEN_CHANNEL -> guideState.focusedProgramme(model.guideDataSource(), guideTimeline(tick, model.guideLatestProgrammeEnd()))?.first?.let { channel ->
                openGuideProgrammeChannel(channel.id)
            } ?: run { guideProgrammeDetailsVisible = false }
            GuideProgrammeDialogEffect.NONE -> Unit
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (autoPlayOnLaunch) model.requestPlayback()
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
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
        if (!observedAutoPlaySetting && autoPlayOnLaunch) model.requestPlayback()
        observedAutoPlaySetting = autoPlayOnLaunch
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
    LaunchedEffect(model.selectedChannelId, activeSection, overlayRequest) {
        if (activeSection == DashboardSection.LIVE && model.selectedChannel() != null) {
            programmeOverlayVisible = true
            delay(5_000)
            programmeOverlayVisible = false
        } else {
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
    LaunchedEffect(model.settings.playlistRefresh) {
        val hours = model.settings.playlistRefresh.hours
        if (hours > 0) {
            while (true) {
                delay(hours * 60L * 60L * 1000L)
                model.refreshAllPlaylists(scope)
            }
        }
    }
    val epgRefreshSources = model.epgSources.map { source -> Triple(source.id, source.enabled, source.lastUpdatedAt) }
    LaunchedEffect(model.settings.epgRefresh, epgRefreshSources) {
        val interval = model.settings.epgRefresh
        if (interval.hours > 0) {
            while (true) {
                val allDueRefreshesSucceeded = model.refreshDueEpgSources(interval)
                val waitMillis = if (allDueRefreshesSucceeded) {
                    model.nextEpgRefreshDelayMillis(interval)
                } else {
                    interval.hours * 60L * 60L * 1000L
                }
                delay(waitMillis.coerceAtLeast(1_000L))
            }
        }
    }

    val overlayChannel = model.selectedChannel()
    val overlayCurrent = overlayChannel?.let { model.currentProgram(it, tick) }
    val overlayNext = overlayChannel?.let { channel -> overlayCurrent?.let { model.nextProgram(channel, it) } }
    val language = model.settings.language
    val playbackStatus = when (playbackController.state) {
        PlaybackState.IDLE, PlaybackState.PLAYING -> null
        PlaybackState.OPENING -> tr(language, "playback.opening")
        PlaybackState.BUFFERING -> null
        PlaybackState.RECONNECTING -> tr(language, "playback.reconnecting")
        PlaybackState.ERROR -> tr(language, "playback.error")
    }?.let { label ->
        listOf(label, playbackController.detail).filterNotNull().joinToString(" · ")
    }
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
        playbackStatus,
        playbackController.state
    ) {
        overlayChannel?.let { channel ->
            val remainingMinutes = overlayCurrent?.end?.let { end ->
                ceil((end - tick).coerceAtLeast(0L) / 60_000.0).toInt()
            }
            playbackController.updateOverlay(
                PlaybackOverlayData(
                    channelId = channel.id,
                    channelNumber = channel.tvgChno?.toString() ?: "–",
                    channelName = channel.name,
                    logoUrl = channel.logo?.takeIf { model.settings.display.showLogos },
                    showProgrammeInfo = activeSection == DashboardSection.LIVE && programmeOverlayVisible,
                    showPreviewLogo = activeSection == DashboardSection.CHANNELS,
                    channelNumberInput = channelNumberInput.takeIf {
                        activeSection == DashboardSection.LIVE && it.isNotEmpty()
                    },
                    noEpgLabel = tr(language, "epg.none"),
                    nextLabel = tr(language, "epg.next"),
                    currentTitle = overlayCurrent?.displayTitle(language),
                    currentStart = overlayCurrent?.start,
                    currentEnd = overlayCurrent?.end,
                    remainingText = remainingMinutes?.let { minutes -> tr(language, "playback.remaining", minutes) },
                    nextTitle = overlayNext?.displayTitle(language),
                    nextStart = overlayNext?.start,
                    nextEnd = overlayNext?.end,
                    now = tick,
                    playbackStatus = playbackStatus,
                    playbackError = playbackController.state == PlaybackState.ERROR,
                    showBufferingSpinner = playbackController.state == PlaybackState.BUFFERING,
                    bufferingLabel = tr(language, "playback.buffering")
                )
            )
        }
    }

    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * model.settings.display.uiScale)) {
        Column(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                    if (event.key.isBackKey()) {
                        when {
                            guideProgrammeDetailsVisible -> guideProgrammeDetailsVisible = false
                            activeSection == DashboardSection.SETTINGS && settingsSection != null -> settingsSection = null
                            else -> {
                                mainNavigationIndex = mainSections.indexOf(activeSection).coerceAtLeast(0)
                                focusZone = TvFocusZone.MAIN_NAVIGATION
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (focusZone == TvFocusZone.MAIN_NAVIGATION) {
                        when (event.key) {
                            Key.DirectionUp, Key.PageUp -> mainNavigationIndex = (mainNavigationIndex - 1).coerceAtLeast(0)
                            Key.DirectionDown, Key.PageDown -> mainNavigationIndex = (mainNavigationIndex + 1).coerceAtMost(mainSections.lastIndex)
                            Key.DirectionRight -> focusZone = TvFocusZone.CONTENT
                            else -> if (event.key.isConfirmKey()) activateSection(mainSections[mainNavigationIndex]) else return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.GUIDE) {
                        if (event.key.isConfirmKey()) {
                            showGuideProgrammeDetails()
                            return@onPreviewKeyEvent true
                        }
                        if (guideState.handleKey(event.key, model.guideDataSource(), scope, guideTimeline(tick, model.guideLatestProgrammeEnd()))) return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.LIVE && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        if (channelNumberInput.isNotEmpty()) {
                            val selected = model.selectChannelByNumber(channelNumberInput)
                            channelNumberInput = ""
                            if (selected) overlayRequest++
                        } else {
                            overlayRequest++
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.SETTINGS) {
                        if (settingsSection == null) {
                            when (event.key) {
                                Key.DirectionUp, Key.PageUp -> settingsCategoryIndex = (settingsCategoryIndex - 1).coerceAtLeast(0)
                                Key.DirectionDown, Key.PageDown -> settingsCategoryIndex = (settingsCategoryIndex + 1).coerceAtMost(SettingsSection.entries.lastIndex)
                                Key.DirectionLeft -> focusZone = TvFocusZone.MAIN_NAVIGATION
                                Key.DirectionRight -> { settingsSection = SettingsSection.entries[settingsCategoryIndex]; settingsOptionIndex = 0 }
                                else -> if (event.key.isConfirmKey()) { settingsSection = SettingsSection.entries[settingsCategoryIndex]; settingsOptionIndex = 0 } else return@onPreviewKeyEvent false
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (settingsSection == SettingsSection.PLAYBACK) {
                            val optionCount = 6
                            when (event.key) {
                                Key.DirectionUp, Key.PageUp -> settingsOptionIndex = (settingsOptionIndex - 1).coerceAtLeast(0)
                                Key.DirectionDown, Key.PageDown -> settingsOptionIndex = (settingsOptionIndex + 1).coerceAtMost(optionCount - 1)
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    val delta = if (event.key == Key.DirectionLeft) -1 else 1
                                    when (settingsOptionIndex) {
                                        1 -> model.updatePlayback { it.copy(volume = (it.volume + delta * 5).coerceIn(0, 100)) }
                                        2 -> model.updatePlayback { current -> current.copy(bufferProfile = BufferProfile.entries[(current.bufferProfile.ordinal + delta).floorMod(BufferProfile.entries.size)]) }
                                        3 -> model.updatePlayback { current -> current.copy(aspectRatio = AspectRatioMode.entries[((current.aspectRatio ?: AspectRatioMode.AUTO).ordinal + delta).floorMod(AspectRatioMode.entries.size)]) }
                                        5 -> model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + delta).coerceIn(1, 10)) }
                                        else -> model.updatePlayback { it.copy(autoPlayOnLaunch = !(it.autoPlayOnLaunch != false)) }
                                    }
                                }
                                else -> if (event.key.isConfirmKey()) {
                                    when (settingsOptionIndex) {
                                        0 -> model.updatePlayback { it.copy(autoPlayOnLaunch = !(it.autoPlayOnLaunch != false)) }
                                        2 -> model.updatePlayback { current -> current.copy(bufferProfile = BufferProfile.entries[(current.bufferProfile.ordinal + 1).floorMod(BufferProfile.entries.size)]) }
                                        3 -> model.updatePlayback { current -> current.copy(aspectRatio = AspectRatioMode.entries[((current.aspectRatio ?: AspectRatioMode.AUTO).ordinal + 1).floorMod(AspectRatioMode.entries.size)]) }
                                        4 -> model.updatePlayback { it.copy(autoReconnect = !it.autoReconnect) }
                                        5 -> model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) }
                                    }
                                } else return@onPreviewKeyEvent false
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (settingsSection == SettingsSection.DISPLAY) {
                            when (event.key) {
                                Key.DirectionUp, Key.PageUp -> settingsOptionIndex = (settingsOptionIndex - 1).coerceAtLeast(0)
                                Key.DirectionDown, Key.PageDown -> settingsOptionIndex = (settingsOptionIndex + 1).coerceAtMost(4)
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    if (settingsOptionIndex == 0) {
                                        val values = listOf(.9f, 1f, 1.15f)
                                        val current = values.indexOf(model.settings.display.uiScale).coerceAtLeast(0)
                                        val delta = if (event.key == Key.DirectionLeft) -1 else 1
                                        model.updateDisplay { it.copy(uiScale = values[(current + delta).floorMod(values.size)]) }
                                    } else if (settingsOptionIndex == 1) {
                                        val current = (model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL).ordinal
                                        val delta = if (event.key == Key.DirectionLeft) -1 else 1
                                        model.updateDisplay { display ->
                                            display.copy(channelListMode = ChannelListDisplayMode.entries[(current + delta).floorMod(ChannelListDisplayMode.entries.size)])
                                        }
                                    } else toggleDisplayOption(model, settingsOptionIndex)
                                }
                                else -> if (event.key.isConfirmKey()) {
                                    if (settingsOptionIndex == 0) {
                                        val values = listOf(.9f, 1f, 1.15f)
                                        val current = values.indexOf(model.settings.display.uiScale).coerceAtLeast(0)
                                        model.updateDisplay { it.copy(uiScale = values[(current + 1).floorMod(values.size)]) }
                                    } else if (settingsOptionIndex == 1) {
                                        val current = (model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL).ordinal
                                        model.updateDisplay { display ->
                                            display.copy(channelListMode = ChannelListDisplayMode.entries[(current + 1).floorMod(ChannelListDisplayMode.entries.size)])
                                        }
                                    } else toggleDisplayOption(model, settingsOptionIndex)
                                } else return@onPreviewKeyEvent false
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (settingsSection == SettingsSection.LANGUAGE) {
                            if (event.key.isConfirmKey() || event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                                model.setLanguage(if (model.settings.language == AppLanguage.HUNGARIAN) AppLanguage.ENGLISH else AppLanguage.HUNGARIAN)
                                return@onPreviewKeyEvent true
                            }
                        }
                        return@onPreviewKeyEvent false
                    }
                    if (activeSection == DashboardSection.CHANNELS) {
                        val filterCount = model.categories().size + 2
                        when (channelRemoteFocus) {
                            ChannelRemoteFocus.FILTERS -> when (event.key) {
                                Key.DirectionLeft -> if (channelFilterIndex == 0) focusZone = TvFocusZone.MAIN_NAVIGATION else channelFilterIndex--
                                Key.DirectionRight -> if (channelFilterIndex >= filterCount - 1) channelRemoteFocus = ChannelRemoteFocus.SEARCH else channelFilterIndex++
                                Key.DirectionDown -> channelRemoteFocus = ChannelRemoteFocus.LIST
                                else -> if (event.key.isConfirmKey()) {
                                    when (channelFilterIndex) {
                                        0 -> { model.category = null; model.onlyFavorites = false }
                                        1 -> { model.category = null; model.onlyFavorites = true }
                                        else -> { model.onlyFavorites = false; model.category = model.categories()[channelFilterIndex - 2] }
                                    }
                                    channelListIndex = 0
                                } else return@onPreviewKeyEvent false
                            }
                            ChannelRemoteFocus.SEARCH -> when (event.key) {
                                Key.DirectionLeft -> channelRemoteFocus = ChannelRemoteFocus.FILTERS
                                Key.DirectionDown, Key.DirectionRight -> channelRemoteFocus = ChannelRemoteFocus.LIST
                                else -> return@onPreviewKeyEvent false
                            }
                            ChannelRemoteFocus.LIST -> when (event.key) {
                                Key.DirectionLeft -> focusZone = TvFocusZone.MAIN_NAVIGATION
                                Key.DirectionRight -> channelRemoteFocus = ChannelRemoteFocus.FAVORITE
                                Key.DirectionUp, Key.PageUp -> channelListIndex = (channelListIndex - 1).coerceAtLeast(0)
                                Key.DirectionDown, Key.PageDown -> channelListIndex = (channelListIndex + 1).coerceAtMost((model.filteredChannels().size - 1).coerceAtLeast(0))
                                else -> if (event.key.isConfirmKey()) model.filteredChannels().getOrNull(channelListIndex)?.let { model.selectChannel(it.id) } else return@onPreviewKeyEvent false
                            }
                            ChannelRemoteFocus.FAVORITE -> when (event.key) {
                                Key.DirectionLeft -> channelRemoteFocus = ChannelRemoteFocus.LIST
                                Key.DirectionUp, Key.PageUp -> channelListIndex = (channelListIndex - 1).coerceAtLeast(0)
                                Key.DirectionDown, Key.PageDown -> channelListIndex = (channelListIndex + 1).coerceAtMost((model.filteredChannels().size - 1).coerceAtLeast(0))
                                else -> if (event.key.isConfirmKey()) model.filteredChannels().getOrNull(channelListIndex)?.let { model.toggleFavorite(it.id) } else return@onPreviewKeyEvent false
                            }
                        }
                        return@onPreviewKeyEvent true
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
                        channelNumberInput = (channelNumberInput + digit).take(4)
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.PageDown, Key.DirectionDown -> {
                            channelNumberInput = ""
                            model.moveChannel(1)
                        }
                        Key.PageUp, Key.DirectionUp -> {
                            channelNumberInput = ""
                            model.moveChannel(-1)
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
        ) {
            DashboardScreen(
                model = model,
                playbackController = playbackController,
                scope = scope,
                tick = tick,
                activeSection = activeSection,
                guideState = guideState,
                onSectionChange = { section ->
                    activateSection(section)
                },
                settingsSection = settingsSection,
                onSettingsSectionChange = { settingsSection = it },
                mainNavigationFocused = focusZone == TvFocusZone.MAIN_NAVIGATION,
                mainNavigationSection = mainSections[mainNavigationIndex],
                channelRemoteFocus = channelRemoteFocus,
                channelFilterIndex = channelFilterIndex,
                channelListIndex = channelListIndex,
                channelListOpenRequest = channelListOpenRequest,
                channelSearchOpen = channelSearchOpen,
                onChannelSearchOpenChange = { open ->
                    channelSearchOpen = open
                    if (open) {
                        channelRemoteFocus = ChannelRemoteFocus.SEARCH
                    } else if (channelRemoteFocus == ChannelRemoteFocus.SEARCH) {
                        channelRemoteFocus = ChannelRemoteFocus.LIST
                    }
                },
                settingsCategoryIndex = settingsCategoryIndex,
                settingsOptionIndex = settingsOptionIndex,
                guideProgrammeDetailsVisible = guideProgrammeDetailsVisible,
                guideProgrammeDialogFocusedAction = guideProgrammeDialogState.focusedAction,
                onShowGuideProgrammeDetails = ::showGuideProgrammeDetails,
                onDismissGuideProgrammeDetails = { guideProgrammeDetailsVisible = false },
                onOpenGuideProgrammeChannel = ::openGuideProgrammeChannel,
                onGuideProgrammeDialogEvent = ::handleGuideProgrammeDialogEvent
            )
        }
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

private fun toggleDisplayOption(model: WukkiModel, optionIndex: Int) = model.updateDisplay { display ->
    when (optionIndex) {
        2 -> display.copy(showChannelProgramme = !display.showChannelProgramme)
        3 -> display.copy(showMiniGuide = !display.showMiniGuide)
        4 -> display.copy(showLogos = !display.showLogos)
        else -> display
    }
}
