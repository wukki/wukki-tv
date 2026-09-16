package hu.wukki.tv.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AspectRatioMode
import hu.wukki.tv.PlaybackEngine
import hu.wukki.tv.PlaybackQuickSettings
import hu.wukki.tv.QuickSetting
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.availableSettings
import hu.wukki.tv.nextTrack
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.RemoteKey
import hu.wukki.tv.ui.navigation.isBackKey
import hu.wukki.tv.ui.navigation.toRemoteKey
import kotlinx.coroutines.delay

@Composable
internal fun PlaybackDialogs(
    session: AppSessionState,
    model: WukkiModel,
    playback: PlaybackEngine,
    openChannels: () -> Unit,
) {
    PlaybackQuickSettingsDialog(session, model, playback)
    PlaybackRecoveryDialog(session, model, playback, openChannels)
}

@Composable
internal fun PlaybackQuickSettingsDialog(
    session: AppSessionState,
    model: WukkiModel,
    playback: PlaybackEngine,
) {
    LaunchedEffect(model.selectedChannelId, session.activeSection, playback.recovery) {
        session.quickSettingsVisible = false
    }
    if (!session.quickSettingsVisible || session.activeSection != DashboardSection.LIVE) return
    var snapshot by remember { mutableStateOf(playback.quickSettings()) }
    LaunchedEffect(playback) {
        while (true) {
            snapshot = playback.quickSettings()
            delay(500)
        }
    }
    PlaybackQuickSettingsPanel(
        snapshot,
        model.settings.language,
        onChange = { setting, delta ->
            playback.changeQuickSetting(snapshot, setting, delta)
            snapshot = playback.quickSettings()
        },
        onDismiss = { session.quickSettingsVisible = false },
    )
}

internal fun PlaybackEngine.changeQuickSetting(
    snapshot: PlaybackQuickSettings,
    setting: QuickSetting,
    delta: Int,
) {
    if (setting == QuickSetting.ASPECT) {
        val current = snapshot.aspect ?: return
        val modes = AspectRatioMode.entries
        setQuickAspectRatio(modes[(current.ordinal + delta + modes.size) % modes.size])
    } else {
        snapshot.nextTrack(setting, delta)?.let { selectTrack(setting, it.id) }
    }
}

@Composable
fun PlaybackQuickSettingsPanel(
    snapshot: PlaybackQuickSettings,
    language: AppLanguage,
    onChange: (QuickSetting, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        PlaybackQuickSettingsContent(snapshot, language, onChange, onDismiss)
    }
}

@Composable
fun PlaybackQuickSettingsContent(
    snapshot: PlaybackQuickSettings,
    language: AppLanguage,
    onChange: (QuickSetting, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = snapshot.availableSettings()
    var focused by remember { mutableIntStateOf(0) }
    LaunchedEffect(options) { focused = focused.coerceIn(0, options.size) }
    Surface(
        color = WukkiColors.surface,
        modifier =
            Modifier.widthIn(max = 600.dp).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key.isBackKey()) {
                    onDismiss()
                    return@onPreviewKeyEvent true
                }
                when (event.key.toRemoteKey()) {
                    RemoteKey.UP -> focused = (focused - 1).coerceAtLeast(0)
                    RemoteKey.DOWN -> focused = (focused + 1).coerceAtMost(options.size)
                    RemoteKey.LEFT -> options.getOrNull(focused)?.let { onChange(it, -1) }
                    RemoteKey.RIGHT, RemoteKey.CONFIRM -> options.getOrNull(focused)?.let { onChange(it, 1) } ?: onDismiss()
                    null -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(language, "playback.quick.title"))
            Text(tr(language, "playback.quick.temporary"), color = WukkiColors.textMuted)
            options.forEachIndexed { index, setting ->
                QuickSettingButton(
                    label = snapshot.settingLabel(setting, language),
                    focused = focused == index,
                    onFocus = { focused = index },
                    onClick = { onChange(setting, 1) },
                )
            }
            QuickSettingButton(tr(language, "action.close"), focused == options.size, { focused = options.size }, onDismiss)
        }
    }
}

@Composable
private fun QuickSettingButton(
    label: String,
    focused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focused) { if (focused) requester.requestFocus() }
    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() }.focusRequester(requester),
        border = if (focused) BorderStroke(2.dp, WukkiColors.focus) else null,
    ) { Text(label) }
}

internal fun PlaybackQuickSettings.settingLabel(
    setting: QuickSetting,
    language: AppLanguage,
): String {
    val title =
        when (setting) {
            QuickSetting.ASPECT -> "settings.playback.aspect"
            QuickSetting.AUDIO -> "playback.quick.audio"
            QuickSetting.SUBTITLES -> "playback.quick.subtitles"
        }
    val value =
        if (setting == QuickSetting.ASPECT) {
            when (aspect) {
                AspectRatioMode.AUTO -> tr(language, "aspect.auto")
                AspectRatioMode.FILL_CROP -> tr(language, "aspect.fill")
                AspectRatioMode.RATIO_16_9 -> "16:9"
                AspectRatioMode.RATIO_4_3 -> "4:3"
                AspectRatioMode.RATIO_21_9 -> "21:9"
                null -> ""
            }
        } else {
            val tracks = if (setting == QuickSetting.AUDIO) audio else subtitles
            val selected = tracks.firstOrNull { it.selected }
            when {
                selected?.id == "off" -> tr(language, "playback.quick.off")
                selected == null -> tr(language, "aspect.auto")
                else -> selected.name?.takeIf { it.isNotBlank() } ?: tr(language, "playback.quick.track", tracks.indexOf(selected) + 1)
            }
        }
    return "${tr(language, title)}: $value"
}
