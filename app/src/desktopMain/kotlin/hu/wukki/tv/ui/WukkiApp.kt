package hu.wukki.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun WukkiApp() {
    val model = remember { WukkiModel() }
    val playbackController = remember { PlaybackController() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf(SettingsSection.PLAYBACK) }
    var activeSection by remember { mutableStateOf(DashboardSection.CHANNELS) }
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

    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * model.settings.display.uiScale)) {
        Column(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (showSettings && event.key == Key.Escape) {
                        showSettings = false
                        return@onPreviewKeyEvent true
                    }
                    if (!showSettings && activeSection == DashboardSection.GUIDE &&
                        guideState.handleKey(event.key, model, scope)
                    ) {
                        return@onPreviewKeyEvent true
                    }
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
            if (showSettings) SettingsScreen(model = model, scope = scope, initialSection = settingsSection, onBack = { showSettings = false })
            else DashboardScreen(
                model = model,
                playbackController = playbackController,
                scope = scope,
                tick = tick,
                activeSection = activeSection,
                guideState = guideState,
                onSectionChange = { activeSection = it },
                onOpenSettings = { section -> settingsSection = section; showSettings = true }
            )
        }
    }
}
