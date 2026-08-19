package hu.wukki.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import hu.wukki.tv.ui.app.WukkiApp
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.WukkiColorScheme
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1470.dp, 920.dp), placement = WindowPlacement.Maximized)
    Window(onCloseRequest = ::exitApplication, title = "WukkiTV", state = windowState) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(1024, 640) }
        MaterialTheme(colorScheme = WukkiColorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = WukkiColors.background, contentColor = WukkiColors.textPrimary) {
                WukkiApp()
            }
        }
    }
}
