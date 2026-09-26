package hu.wukki.tv.ui.guide

import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.displayName
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.isBackKey
import hu.wukki.tv.ui.navigation.isConfirmKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Immutable
data class GuideProgrammeDetailsUiState(
    val language: AppLanguage,
    val channel: Channel,
    val programme: Programme,
    val nextProgramme: Programme?,
    val focusedAction: GuideProgrammeDialogAction,
)

@Composable
fun GuideProgrammeDetails(
    state: GuideProgrammeDetailsUiState,
    onDismiss: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onRemoteEvent: (GuideProgrammeDialogEvent) -> Unit,
    handleSystemBackKey: Boolean = true,
) {
    val dialogFocusRequester = remember { FocusRequester() }
    val detailsScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { dialogFocusRequester.requestFocus() }
    BoxWithConstraints {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier =
                Modifier.width(minOf(560.dp, maxWidth - 32.dp)).focusRequester(dialogFocusRequester).focusable().onPreviewKeyEvent { event ->
                    handleGuideProgrammeKeyEvent(event, handleSystemBackKey, onRemoteEvent, detailsScrollState, scope)
                },
            title = { Text(state.programme.displayTitle(state.language), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(detailsScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(state.channel.displayName(state.language), fontWeight = FontWeight.SemiBold)
                    Text("${formatTime(state.programme.start)} – ${formatTime(state.programme.end)}")
                    Text(state.programme.description?.takeIf { it.isNotBlank() } ?: tr(state.language, "epg.no.description"))
                    state.nextProgramme?.let { Text("${tr(state.language, "epg.next")}: ${it.displayTitle(state.language)} · ${formatTime(it.start)}") }
                }
            },
            confirmButton = {
                if (state.focusedAction == GuideProgrammeDialogAction.OPEN) {
                    Button(onClick = { onOpenChannel(state.channel.id) }) { Text(tr(state.language, "action.open")) }
                } else {
                    TextButton(onClick = { onOpenChannel(state.channel.id) }) { Text(tr(state.language, "action.open")) }
                }
            },
            dismissButton = {
                if (state.focusedAction == GuideProgrammeDialogAction.CANCEL) {
                    Button(onClick = onDismiss) { Text(tr(state.language, "action.cancel")) }
                } else {
                    TextButton(onClick = onDismiss) { Text(tr(state.language, "action.cancel")) }
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
    }
}

private fun handleGuideProgrammeKeyEvent(
    event: KeyEvent,
    handleSystemBackKey: Boolean,
    onRemoteEvent: (GuideProgrammeDialogEvent) -> Unit,
    scrollState: ScrollState,
    scope: CoroutineScope,
): Boolean {
    if (event.type != KeyEventType.KeyDown || (!handleSystemBackKey && event.key == Key.Back)) return false
    val dialogEvent =
        when {
            event.key.isBackKey() -> GuideProgrammeDialogEvent.BACK
            event.key == Key.DirectionLeft -> GuideProgrammeDialogEvent.LEFT
            event.key == Key.DirectionRight -> GuideProgrammeDialogEvent.RIGHT
            event.key.isConfirmKey() -> GuideProgrammeDialogEvent.CONFIRM
            else -> null
        }
    if (dialogEvent != null) {
        onRemoteEvent(dialogEvent)
        return true
    }
    val target =
        when (event.key) {
            Key.DirectionUp, Key.PageUp -> (scrollState.value - 220).coerceAtLeast(0)
            Key.DirectionDown, Key.PageDown -> (scrollState.value + 220).coerceAtMost(scrollState.maxValue)
            else -> return false
        }
    scope.launch { scrollState.animateScrollTo(target) }
    return true
}
