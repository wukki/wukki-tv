package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.settings.*

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
    val playbackController = remember { PlaybackController() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var activeSection by remember { mutableStateOf(DashboardSection.LIVE) }
    var overlayRequest by remember { mutableIntStateOf(0) }
    var programmeOverlayVisible by remember { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    val guideState = rememberEpgGuideState()
    val baseDensity = LocalDensity.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    DisposableEffect(playbackController) {
        onDispose { playbackController.release() }
    }
    LaunchedEffect(model.selectedChannelId, model.settings.playback, model.settings.display.showLogos) {
        playbackController.play(model.selectedChannel(), model.settings.playback, model.settings.display.showLogos)
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
    LaunchedEffect(channelNumberInput) {
        val pendingNumber = channelNumberInput
        if (pendingNumber.isNotEmpty()) {
            delay(3_000)
            if (channelNumberInput == pendingNumber) {
                val selected = model.selectChannelByNumber(pendingNumber)
                channelNumberInput = ""
                if (selected) overlayRequest++
            }
        }
    }
    LaunchedEffect(activeSection) {
        if (activeSection != DashboardSection.LIVE) channelNumberInput = ""
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
    LaunchedEffect(model.settings.epgRefresh) {
        val hours = model.settings.epgRefresh.hours
        if (hours > 0) {
            while (true) {
                delay(hours * 60L * 60L * 1000L)
                model.refreshAllEpg(scope)
            }
        }
    }

    val overlayChannel = model.selectedChannel()
    val overlayCurrent = overlayChannel?.let { model.currentProgram(it, tick) }
    val overlayNext = overlayChannel?.let { channel -> overlayCurrent?.let { model.nextProgram(channel, it) } }
    val hungarian = model.settings.language == AppLanguage.HUNGARIAN
    val playbackStatus = when (playbackController.state) {
        PlaybackState.IDLE, PlaybackState.PLAYING -> null
        PlaybackState.OPENING -> if (hungarian) "Betöltés" else "Opening"
        PlaybackState.BUFFERING -> if (hungarian) "Pufferelés" else "Buffering"
        PlaybackState.RECONNECTING -> if (hungarian) "Újracsatlakozás" else "Reconnecting"
        PlaybackState.ERROR -> if (hungarian) "Lejátszási hiba" else "Playback error"
    }?.let { label ->
        listOf(label, playbackController.detail.takeIf { hungarian }).filterNotNull().joinToString(" · ")
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
                    noEpgLabel = if (hungarian) "EPG nincs" else "No EPG",
                    nextLabel = if (hungarian) "Következő" else "Next",
                    currentTitle = overlayCurrent?.title,
                    currentStart = overlayCurrent?.start,
                    currentEnd = overlayCurrent?.end,
                    remainingText = remainingMinutes?.let { minutes ->
                        if (hungarian) "$minutes perc van hátra" else "$minutes min remaining"
                    },
                    nextTitle = overlayNext?.title,
                    nextStart = overlayNext?.start,
                    nextEnd = overlayNext?.end,
                    now = tick,
                    playbackStatus = playbackStatus,
                    playbackError = playbackController.state == PlaybackState.ERROR
                )
            )
        }
    }

    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * model.settings.display.uiScale)) {
        Column(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (activeSection == DashboardSection.GUIDE &&
                        guideState.handleKey(event.key, model.guideDataSource(), scope, guideDays(tick))
                    ) {
                        return@onPreviewKeyEvent true
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
                        if (event.key == Key.Escape && settingsSection != null) {
                            settingsSection = null
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
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
                    if (section == DashboardSection.SETTINGS) settingsSection = null
                    activeSection = section
                },
                settingsSection = settingsSection,
                onSettingsSectionChange = { settingsSection = it }
            )
        }
    }
}
