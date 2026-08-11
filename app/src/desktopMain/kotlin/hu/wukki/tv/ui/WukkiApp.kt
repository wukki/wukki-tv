package hu.wukki.tv

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
    var settingsSection by remember { mutableStateOf(SettingsSection.PLAYBACK) }
    var activeSection by remember { mutableStateOf(DashboardSection.LIVE) }
    var overlayRequest by remember { mutableIntStateOf(0) }
    var programmeOverlayVisible by remember { mutableStateOf(false) }
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
                    showVideoChrome = activeSection == DashboardSection.LIVE,
                    showProgrammeInfo = activeSection == DashboardSection.LIVE && programmeOverlayVisible,
                    liveLabel = if (hungarian) "ÉLŐ" else "LIVE",
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
                        guideState.handleKey(event.key, model, scope)
                    ) {
                        return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.LIVE && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        overlayRequest++
                        return@onPreviewKeyEvent true
                    }
                    if (activeSection == DashboardSection.SETTINGS) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.PageDown, Key.DirectionDown -> model.moveChannel(1)
                        Key.PageUp, Key.DirectionUp -> model.moveChannel(-1)
                        Key.One -> model.selectChannelByNumber("1")
                        Key.Two -> model.selectChannelByNumber("2")
                        Key.Three -> model.selectChannelByNumber("3")
                        Key.Four -> model.selectChannelByNumber("4")
                        Key.Five -> model.selectChannelByNumber("5")
                        Key.Six -> model.selectChannelByNumber("6")
                        Key.Seven -> model.selectChannelByNumber("7")
                        Key.Eight -> model.selectChannelByNumber("8")
                        Key.Nine -> model.selectChannelByNumber("9")
                        Key.Zero -> model.selectChannelByNumber("0")
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
                onSectionChange = { activeSection = it },
                settingsSection = settingsSection,
                onSettingsSectionChange = { settingsSection = it }
            )
        }
    }
}
