package hu.wukki.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Wukki TV") {
        MaterialTheme(colorScheme = WukkiColorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = AppBackground, contentColor = Color.White) {
                WukkiApp()
            }
        }
    }
}
