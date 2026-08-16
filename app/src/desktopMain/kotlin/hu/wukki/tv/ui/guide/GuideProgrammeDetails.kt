package hu.wukki.tv.ui.guide

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.ProgrammeArtwork
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.isBackKey
import hu.wukki.tv.ui.navigation.isConfirmKey

data class GuideProgrammeDetailsUiState(
    val language: AppLanguage,
    val channel: Channel,
    val programme: Programme,
    val nextProgramme: Programme?,
    val showProgrammeImages: Boolean
)

@Composable
fun GuideProgrammeDetails(
    state: GuideProgrammeDetailsUiState,
    focusedAction: GuideProgrammeDialogAction,
    onDismiss: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onRemoteEvent: (GuideProgrammeDialogEvent) -> Unit
) {
    val dialogFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { dialogFocusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRequester(dialogFocusRequester).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val dialogEvent = when {
                event.key.isBackKey() -> GuideProgrammeDialogEvent.BACK
                event.key == Key.DirectionLeft -> GuideProgrammeDialogEvent.LEFT
                event.key == Key.DirectionRight -> GuideProgrammeDialogEvent.RIGHT
                event.key.isConfirmKey() -> GuideProgrammeDialogEvent.CONFIRM
                else -> null
            }
            dialogEvent?.let(onRemoteEvent) != null
        },
        containerColor = WukkiColors.surfaceOverlay,
        titleContentColor = WukkiColors.textPrimary,
        textContentColor = WukkiColors.textSecondary,
        title = { Text(state.programme.displayTitle(state.language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.channel.name, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("${formatTime(state.programme.start)} – ${formatTime(state.programme.end)}")
                if (state.programme.imageUrl != null && state.showProgrammeImages) {
                    ProgrammeArtwork(state.programme, state.language, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                Text(state.programme.description?.takeIf { it.isNotBlank() } ?: tr(state.language, "epg.no.description"))
                state.nextProgramme?.let { Text("${tr(state.language, "epg.next")}: ${it.displayTitle(state.language)} · ${formatTime(it.start)}", color = WukkiColors.textMuted) }
            }
        },
        confirmButton = {
            Button(onClick = { onOpenChannel(state.channel.id) }, colors = ButtonDefaults.buttonColors(
                containerColor = if (focusedAction == GuideProgrammeDialogAction.OPEN) WukkiColors.primary else WukkiColors.surfaceInput,
                contentColor = if (focusedAction == GuideProgrammeDialogAction.OPEN) WukkiColors.textPrimary else WukkiColors.textSecondary
            )) { Text(tr(state.language, "action.open")) }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(
                containerColor = if (focusedAction == GuideProgrammeDialogAction.CANCEL) WukkiColors.primary else WukkiColors.surfaceInput,
                contentColor = if (focusedAction == GuideProgrammeDialogAction.CANCEL) WukkiColors.textPrimary else WukkiColors.textSecondary
            )) { Text(tr(state.language, "action.cancel")) }
        }
    )
}
