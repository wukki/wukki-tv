package hu.wukki.tv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLConnection
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmRemoteTextLoaderTest {
    @Test
    fun `decodes plain UTF-8 content`() {
        assertEquals("Műsorújság", decodeRemoteText(ByteArrayInputStream("Műsorújság".toByteArray()), 1024))
    }

    @Test
    fun `detects and decodes gzip content`() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { it.write("Élő adás") }

        assertEquals("Élő adás", decodeRemoteText(ByteArrayInputStream(output.toByteArray()), 1024))
    }

    @Test
    fun `rejects plain and gzip bodies beyond the decoded limit`() {
        assertFailsWith<RemoteBodyTooLargeException> {
            decodeRemoteText(ByteArrayInputStream(ByteArray(9)), 8)
        }
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(ByteArray(4_096)) }
        val compressed = output.toByteArray()
        // The transport fits; only decompression crosses the boundary.
        kotlin.test.assertTrue(compressed.size < 1_024)

        assertFailsWith<RemoteBodyTooLargeException> {
            decodeRemoteText(ByteArrayInputStream(compressed), 1_024)
        }
    }

    @Test
    fun `accepts a body exactly at the limit`() {
        assertEquals("12345678", decodeRemoteText(ByteArrayInputStream("12345678".toByteArray()), 8))
    }

    @Test
    fun `configures timeouts and versioned user agent`() {
        val connection = object : URLConnection(URI("https://example.test/guide.xml").toURL()) {
            override fun connect() = Unit
        }.configuredForWukki()

        assertEquals(15_000, connection.connectTimeout)
        assertEquals(30_000, connection.readTimeout)
        assertEquals("WukkiTV/${WukkiBuildInfo.VERSION}", connection.getRequestProperty("User-Agent"))
    }
}
