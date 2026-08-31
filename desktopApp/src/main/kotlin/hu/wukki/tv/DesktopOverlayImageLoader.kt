package hu.wukki.tv

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.SuccessResult
import coil3.executeBlocking
import coil3.toBitmap
import hu.wukki.tv.ui.components.wukkiImageRequest
import java.awt.image.BufferedImage

/** Decodes network and in-memory overlay artwork through Coil and Skia. */
internal class DesktopOverlayImageLoader(
    private val imageLoader: ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
) : AutoCloseable {

    fun load(url: String): BufferedImage? = decode(url)

    internal fun decode(data: Any): BufferedImage? {
        val request = wukkiImageRequest(
            context = PlatformContext.INSTANCE,
            data = data,
            width = TARGET_WIDTH,
            height = TARGET_HEIGHT
        )
        val image = (imageLoader.executeBlocking(request) as? SuccessResult)?.image ?: return null
        val width = image.width.takeIf { it > 0 }?.coerceAtMost(TARGET_WIDTH) ?: TARGET_WIDTH
        val height = image.height.takeIf { it > 0 }?.coerceAtMost(TARGET_HEIGHT) ?: TARGET_HEIGHT
        val bitmap = image.toBitmap(width, height)
        return try {
            BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { output ->
                val pixels = IntArray(width * height)
                var offset = 0
                for (y in 0 until height) {
                    for (x in 0 until width) pixels[offset++] = bitmap.getColor(x, y)
                }
                output.setRGB(0, 0, width, height, pixels, 0, width)
            }
        } finally {
            if (image !is BitmapImage || image.bitmap !== bitmap) bitmap.close()
        }
    }

    override fun close() {
        imageLoader.shutdown()
    }

    private companion object {
        const val TARGET_WIDTH = 640
        const val TARGET_HEIGHT = 360
    }
}
