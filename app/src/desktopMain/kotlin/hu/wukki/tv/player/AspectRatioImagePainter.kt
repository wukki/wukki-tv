package hu.wukki.tv

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min
import uk.co.caprica.vlcj.player.component.callback.CallbackImagePainter

/** Draws callback frames proportionally and crops only when the chosen mode requires it. */
internal class AspectRatioImagePainter(private val mode: AspectRatioMode) : CallbackImagePainter {
    override fun prepare(graphics: Graphics2D, component: JComponent) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    override fun paint(graphics: Graphics2D, component: JComponent, image: BufferedImage?) {
        val surfaceWidth = component.width
        val surfaceHeight = component.height
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        graphics.color = component.background ?: Color.BLACK
        graphics.fillRect(0, 0, surfaceWidth, surfaceHeight)
        image ?: return
        val target = targetBounds(surfaceWidth, surfaceHeight)
        val scale = if (mode == AspectRatioMode.AUTO) min(target.width / image.width, target.height / image.height) else max(target.width / image.width, target.height / image.height)
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale
        val previousClip = graphics.clip
        graphics.clipRect(target.x.toInt(), target.y.toInt(), target.width.toInt(), target.height.toInt())
        graphics.drawImage(image, (target.x + (target.width - drawnWidth) / 2).toInt(), (target.y + (target.height - drawnHeight) / 2).toInt(), drawnWidth.toInt(), drawnHeight.toInt(), null)
        graphics.clip = previousClip
    }

    private fun targetBounds(width: Int, height: Int): Bounds {
        val ratio = mode.targetRatio ?: return Bounds(0.0, 0.0, width.toDouble(), height.toDouble())
        return if (width.toDouble() / height > ratio) {
            val targetWidth = height * ratio; Bounds((width - targetWidth) / 2, 0.0, targetWidth, height.toDouble())
        } else {
            val targetHeight = width / ratio; Bounds(0.0, (height - targetHeight) / 2, width.toDouble(), targetHeight)
        }
    }
}

private data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double)
private val AspectRatioMode.targetRatio: Double?
    get() = when (this) {
        AspectRatioMode.AUTO, AspectRatioMode.FILL_CROP -> null
        AspectRatioMode.RATIO_16_9 -> 16.0 / 9.0
        AspectRatioMode.RATIO_4_3 -> 4.0 / 3.0
        AspectRatioMode.RATIO_21_9 -> 21.0 / 9.0
    }
