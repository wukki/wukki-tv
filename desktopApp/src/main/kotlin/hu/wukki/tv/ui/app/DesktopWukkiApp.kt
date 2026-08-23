package hu.wukki.tv.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import hu.wukki.tv.EmbeddedVlcPlayer
import hu.wukki.tv.PlaybackController

/** Desktop composition root. Android supplies its own Media3 playback engine. */
@Composable
fun DesktopWukkiApp() {
    val playbackController = remember { PlaybackController() }
    WukkiApp(
        playbackController = playbackController,
        videoHost = { modifier: Modifier, _ -> EmbeddedVlcPlayer(playbackController, modifier) },
        playbackEngineLabel = "VLC / libVLC",
        useExpandedDesktopNavigation = true
    )
}
