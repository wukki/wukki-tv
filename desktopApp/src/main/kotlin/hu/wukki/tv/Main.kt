package hu.wukki.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import hu.wukki.tv.ui.app.DesktopWukkiApp
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.WukkiColorScheme
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.WindowFocusListener
import java.awt.event.WindowStateListener

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1470.dp, 920.dp), placement = WindowPlacement.Maximized)
    val screenWakeCoordinator = remember { ScreenWakeCoordinator(createDesktopScreenWakeController()) }
    Window(
        onCloseRequest = {
            screenWakeCoordinator.close()
            exitApplication()
        },
        title = "WukkiTV",
        state = windowState
    ) {
        DisposableEffect(window) {
            fun updateWakeState() {
                val minimized = window.extendedState and Frame.ICONIFIED != 0
                screenWakeCoordinator.update(window.isFocused && !minimized)
            }
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(event: java.awt.event.WindowEvent) = updateWakeState()
                override fun windowLostFocus(event: java.awt.event.WindowEvent) = updateWakeState()
            }
            val stateListener = WindowStateListener { updateWakeState() }
            window.addWindowFocusListener(focusListener)
            window.addWindowStateListener(stateListener)
            updateWakeState()
            onDispose {
                window.removeWindowFocusListener(focusListener)
                window.removeWindowStateListener(stateListener)
                screenWakeCoordinator.close()
            }
        }
        LaunchedEffect(Unit) { window.minimumSize = Dimension(1024, 640) }
        MaterialTheme(colorScheme = WukkiColorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = WukkiColors.background, contentColor = WukkiColors.textPrimary) {
                DesktopWukkiApp()
            }
        }
    }
}
