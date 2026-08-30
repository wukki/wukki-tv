package hu.wukki.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities

@Composable
fun EmbeddedVlcPlayer(controller: PlaybackController, modifier: Modifier = Modifier) {
    controller.component?.let { component ->
        SwingPanel(
            factory = {
                component.apply {
                    isFocusable = false
                    videoSurfaceComponent().apply {
                        isFocusable = false
                        val composeMouseForwarder = object : MouseAdapter() {
                            override fun mousePressed(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseReleased(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseEntered(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseExited(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseDragged(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseMoved(event: MouseEvent) = forwardToCompose(component, event)
                            override fun mouseWheelMoved(event: MouseWheelEvent) = forwardToCompose(component, event)
                        }
                        addMouseListener(composeMouseForwarder)
                        addMouseMotionListener(composeMouseForwarder)
                        addMouseWheelListener(composeMouseForwarder)
                    }
                }
            },
            modifier = modifier
        )
    }
}

private fun forwardToCompose(interopView: Component, event: MouseEvent) {
    val composeInteropGroup = interopView.parent ?: return
    composeInteropGroup.dispatchEvent(SwingUtilities.convertMouseEvent(event.component, event, composeInteropGroup))
    event.consume()
}

internal fun BufferProfile.vlcOption(): String = when (this) {
    BufferProfile.LOW_LATENCY -> ":network-caching=300"
    BufferProfile.BALANCED -> ":network-caching=1000"
    BufferProfile.STABLE -> ":network-caching=3000"
}
