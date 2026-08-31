package hu.wukki.tv

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

class DesktopOverlayImageLoaderTest {
    private lateinit var loader: DesktopOverlayImageLoader

    @BeforeTest
    fun setUp() {
        loader = DesktopOverlayImageLoader()
    }

    @AfterTest
    fun tearDown() {
        loader.close()
    }

    @Test
    fun `decodes common raster programme artwork formats`() {
        val images = mapOf(
            "PNG" to imageIoBytes("png", BufferedImage.TYPE_INT_ARGB),
            "JPEG" to imageIoBytes("jpg", BufferedImage.TYPE_INT_RGB),
            "GIF" to imageIoBytes("gif", BufferedImage.TYPE_INT_ARGB),
            "WebP" to skiaWebPBytes()
        )

        images.forEach { (format, bytes) ->
            val decoded = assertNotNull(loader.decode(bytes), "$format artwork should be decoded")
            assertTrue(decoded.width > 0, "$format artwork width should be positive")
            assertTrue(decoded.height > 0, "$format artwork height should be positive")
        }
    }

    @Test
    fun `decodes SVG programme artwork`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="18" viewBox="0 0 32 18">
              <rect width="32" height="18" fill="#7c3aed"/>
            </svg>
        """.trimIndent().encodeToByteArray()

        val decoded = assertNotNull(loader.decode(svg))
        assertTrue(decoded.width > 0)
        assertTrue(decoded.height > 0)
    }

    private fun imageIoBytes(format: String, type: Int): ByteArray {
        val image = BufferedImage(4, 3, type)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(124, 58, 237)
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, format, output)) { "No ImageIO writer for $format" }
            output.toByteArray()
        }
    }

    private fun skiaWebPBytes(): ByteArray {
        val bitmap = Bitmap()
        try {
            check(bitmap.allocN32Pixels(4, 3))
            bitmap.erase(0xFF7C3AED.toInt())
            return Image.makeFromBitmap(bitmap).use { image ->
                requireNotNull(image.encodeToData(EncodedImageFormat.WEBP, 90)).use { it.bytes }
            }
        } finally {
            bitmap.close()
        }
    }
}
