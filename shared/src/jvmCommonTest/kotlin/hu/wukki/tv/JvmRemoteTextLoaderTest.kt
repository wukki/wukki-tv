package hu.wukki.tv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmRemoteTextLoaderTest {
    @Test
    fun `decodes plain UTF-8 content`() {
        assertEquals("Műsorújság", decodeRemoteText(ByteArrayInputStream("Műsorújság".toByteArray())))
    }

    @Test
    fun `detects and decodes gzip content`() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { it.write("Élő adás") }

        assertEquals("Élő adás", decodeRemoteText(ByteArrayInputStream(output.toByteArray())))
    }
}
