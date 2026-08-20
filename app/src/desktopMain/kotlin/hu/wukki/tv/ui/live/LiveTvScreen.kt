package hu.wukki.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hu.wukki.tv.ui.components.WukkiColors

data class LiveTvUiState(val hasChannel: Boolean, val emptyMessage: String)

/** Video host supplied as a slot so this feature never depends on PlaybackController. */
@Composable
fun LiveTvScreen(state: LiveTvUiState, scale: Float, video: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp))).background(WukkiColors.video)
            .border(1.dp, WukkiColors.border, RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp)))
    ) {
        if (state.hasChannel) video() else Text(state.emptyMessage, color = WukkiColors.textMuted, modifier = Modifier.align(Alignment.Center))
    }
}
