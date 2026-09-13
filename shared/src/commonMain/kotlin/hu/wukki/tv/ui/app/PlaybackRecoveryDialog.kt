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
import androidx.compose.runtime.remember
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
import hu.wukki.tv.PlaybackEngine
import hu.wukki.tv.PlaybackOverlayAction
import hu.wukki.tv.PlaybackRecoveryState
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.toAppRemoteKey

/** A shared dialog keeps recovery above both the Android surface and desktop Swing video. */
@Composable
internal fun PlaybackRecoveryDialog(
    session: AppSessionState,
    model: WukkiModel,
    playback: PlaybackEngine,
    openChannels: () -> Unit,
) {
    val recovery = playback.recovery ?: return
    if (session.activeSection != DashboardSection.LIVE) return
    val controller =
        remember(session, model, playback) {
            PlaybackRecoveryController(session, playback) {
                model.setChannelQuery("")
                model.showAllChannels()
                openChannels()
            }
        }
    LaunchedEffect(recovery.actions) {
        session.playbackRecoveryNavigation = PlaybackRecoveryNavigationState()
    }
    val language = model.settings.language
    val reconnecting = recovery.reconnectAttempt != null
    Dialog(onDismissRequest = {
        if (session.playbackRecoveryNavigation.showTechnicalDetail) {
            controller.perform(PlaybackOverlayAction.DETAILS)
        } else {
            controller.perform(if (reconnecting) PlaybackOverlayAction.CANCEL_RECONNECT else PlaybackOverlayAction.CHANNELS)
        }
    }) {
        Surface(
            color = WukkiColors.surface,
            modifier =
                Modifier.widthIn(max = 600.dp).onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && controller.handleRemote(event.key.toAppRemoteKey())
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecoveryContent(recovery, language, model.selectedChannel()?.name.orEmpty(), session, controller)
            }
        }
    }
}

private fun PlaybackOverlayAction.recoveryLabelKey(): String =
    when (this) {
        PlaybackOverlayAction.RETRY -> "playback.recovery.retry"
        PlaybackOverlayAction.CHANNELS -> "nav.channels"
        PlaybackOverlayAction.CANCEL_RECONNECT -> "action.cancel"
        PlaybackOverlayAction.DETAILS -> "playback.recovery.details"
    }

@Composable
private fun RecoveryContent(
    recovery: PlaybackRecoveryState,
    language: AppLanguage,
    channelName: String,
    session: AppSessionState,
    controller: PlaybackRecoveryController,
) {
    Text(tr(language, if (recovery.reconnectAttempt != null) "playback.reconnecting" else "playback.error"))
    Text(channelName)
    Text(
        if (recovery.reconnectAttempt != null) {
            tr(language, "playback.recovery.counter", recovery.reconnectAttempt, recovery.reconnectAttempts)
        } else {
            tr(language, "playback.recovery.help")
        },
    )
    if (session.playbackRecoveryNavigation.showTechnicalDetail) {
        Text(recovery.technicalDetail ?: tr(language, "error.unknown"))
    }
    RecoveryButtons(recovery, language, session, controller)
}

@Composable
private fun RecoveryButtons(
    recovery: PlaybackRecoveryState,
    language: AppLanguage,
    session: AppSessionState,
    controller: PlaybackRecoveryController,
) {
    recovery.actions.forEachIndexed { index, action ->
        val requester = remember { FocusRequester() }
        val focused = index == session.playbackRecoveryNavigation.focusedIndex
        LaunchedEffect(focused) {
            if (focused) requester.requestFocus()
        }
        Button(
            onClick = { controller.perform(action) },
            modifier =
                Modifier
                    .onFocusChanged {
                        if (it.isFocused) {
                            session.playbackRecoveryNavigation =
                                session.playbackRecoveryNavigation.copy(focusedIndex = index)
                        }
                    }.focusRequester(requester),
            border =
                if (index == session.playbackRecoveryNavigation.focusedIndex) {
                    BorderStroke(2.dp, WukkiColors.focus)
                } else {
                    null
                },
        ) {
            Text(tr(language, action.recoveryLabelKey()))
        }
    }
}
