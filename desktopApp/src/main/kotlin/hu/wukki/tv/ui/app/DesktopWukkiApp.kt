package hu.wukki.tv.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import hu.wukki.tv.DesktopAppGraph
import hu.wukki.tv.EmbeddedVlcPlayer
import hu.wukki.tv.PlaybackController

/** Desktop composition root. Android supplies its own Media3 playback engine. */
@Composable
fun DesktopWukkiApp() {
    val dependencies = remember { DesktopAppGraph.dependencies }
    AppBootstrapHost(DesktopAppGraph.bootstrap) { model ->
        val playbackController = remember { PlaybackController() }
        WukkiApp(
            dependencies = dependencies,
            playbackController = playbackController,
            videoHost = { modifier: Modifier, _ -> EmbeddedVlcPlayer(playbackController, modifier) },
            playbackEngineLabel = "VLC / libVLC",
            sharedModel = model,
            liveNavigationPointerModifier = { onShow -> desktopLiveNavigationPointerModifier(onShow) },
        )
    }
}
