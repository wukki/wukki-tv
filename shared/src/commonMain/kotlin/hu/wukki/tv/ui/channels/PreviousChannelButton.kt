package hu.wukki.tv.ui.channels

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.tr

@Composable
internal fun PreviousChannelButton(
    state: ChannelBrowserUiState,
    callbacks: ChannelBrowserCallbacks,
    focused: Boolean,
) {
    OutlinedButton(
        onClick = callbacks.onPreviousChannel,
        enabled = state.hasPreviousChannel,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) WukkiColors.focus else WukkiColors.border),
    ) {
        Text(tr(state.language, "channels.previous"))
    }
}
