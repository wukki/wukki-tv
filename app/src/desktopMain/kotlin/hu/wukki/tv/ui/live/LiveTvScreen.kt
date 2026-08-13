package hu.wukki.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class LiveTvUiState(val hasChannel: Boolean, val emptyMessage: String)

/** Video host supplied as a slot so this feature never depends on PlaybackController. */
@Composable
fun LiveTvScreen(state: LiveTvUiState, scale: Float, video: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(top = (38.dp * scale).coerceAtLeast(20.dp), end = (36.dp * scale).coerceAtLeast(18.dp), bottom = (120.dp * scale).coerceAtLeast(54.dp))
            .clip(RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp))).background(Color.Black)
            .border(1.dp, Color(0xFF172536), RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp)))
    ) {
        if (state.hasChannel) video() else Text(state.emptyMessage, color = Color(0xFF93A0B5), modifier = Modifier.align(Alignment.Center))
    }
}
