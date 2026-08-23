package hu.wukki.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel

@Composable
fun EmbeddedVlcPlayer(controller: PlaybackController, modifier: Modifier = Modifier) {
    controller.component?.let { component ->
        SwingPanel(
            factory = {
                component.apply {
                    isFocusable = false
                    videoSurfaceComponent().isFocusable = false
                }
            },
            modifier = modifier
        )
    }
}

internal fun BufferProfile.vlcOption(): String = when (this) {
    BufferProfile.LOW_LATENCY -> ":network-caching=300"
    BufferProfile.BALANCED -> ":network-caching=1000"
    BufferProfile.STABLE -> ":network-caching=3000"
}

