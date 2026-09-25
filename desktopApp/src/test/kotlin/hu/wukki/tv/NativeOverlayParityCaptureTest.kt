package hu.wukki.tv

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Captures the actual Java2D overlay path, independently of VLC and network streams. */
class NativeOverlayParityCaptureTest {
    @Test
    fun captureNativeOverlay() {
        val output = File(System.getProperty("parity.output")).resolveSibling("desktop-native").apply { mkdirs() }
        val width = Integer.getInteger("parity.width", 1920)
        val height = Integer.getInteger("parity.height", 1080)
        val base =
            PlaybackOverlayData(
                channelId = "ref-1",
                channelNumber = "1",
                channelName = "Hírek",
                logoUrl = null,
                programmeImageUrl = null,
                showProgrammeInfo = true,
                showPreviewLogo = false,
                channelNumberInput = null,
                programme = PlaybackProgrammeOverlay(title = "Esti műsor – Hírek"),
            )
        mapOf(
            "programme-info" to base,
            "buffering" to base.copy(showProgrammeInfo = false, buffering = PlaybackBufferingOverlay("Pufferelés…")),
            "channel-number" to base.copy(showProgrammeInfo = false, channelNumberInput = "123"),
        ).forEach { (name, data) ->
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = Color.BLACK
                graphics.fillRect(0, 0, width, height)
                renderPlaybackOverlay(graphics, RenderedPlaybackOverlay(data), width, height)
            } finally {
                graphics.dispose()
            }
            ImageIO.write(image, "png", File(output, "$name.png"))
        }
    }
}
