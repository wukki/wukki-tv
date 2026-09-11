package hu.wukki.tv

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmStreamingEpgTest {
    @Test
    fun `production content loader streams gzip XMLTV into SAX parser`() {
        val xml =
            """
            <tv><programme channel="rtl" start="20260820180000 +0200" stop="20260820183000 +0200">
            <title>Híradó</title></programme></tv>
            """.trimIndent()
        val compressed =
            ByteArrayOutputStream()
                .also { output ->
                    GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
                }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/guide.xml.gz") { exchange ->
            exchange.sendResponseHeaders(200, compressed.size.toLong())
            exchange.responseBody.use { it.write(compressed) }
        }
        server.start()
        try {
            val content =
                JvmRemoteContentLoader.load(
                    RemoteTextRequest(
                        "http://127.0.0.1:${server.address.port}/guide.xml.gz",
                        RemoteTextKind.EPG,
                    ),
                )

            try {
                assertEquals("Híradó", JvmXmlTvParser.parse(content).single().title)
            } finally {
                content.close()
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `SAX parser consumes content and caller closes it`() {
        var closed = false
        val xml =
            """
            <tv><programme channel="rtl" start="20260820180000 +0200" stop="20260820183000 +0200">
            <title>Híradó</title></programme></tv>
            """.trimIndent()
        val content = decodedRemoteContent(ByteArrayInputStream(xml.toByteArray()), 4_096) { closed = true }

        val programme =
            try {
                JvmXmlTvParser.parse(content).single()
            } finally {
                content.close()
            }

        assertEquals("Híradó", programme.title)
        assertTrue(closed)
    }

    @Test
    fun `streaming parser detects gzip and enforces the decoded byte limit`() {
        val xml = "<tv>${" ".repeat(4_096)}</tv>"
        val compressed =
            ByteArrayOutputStream()
                .also { output ->
                    GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
                }.toByteArray()
        val content = decodedRemoteContent(ByteArrayInputStream(compressed), 1_024)

        val exception =
            try {
                assertFailsWith<RemoteBodyTooLargeException> { JvmXmlTvParser.parse(content) }
            } finally {
                content.close()
            }

        assertEquals(AppFailure.ResponseTooLarge, exception.failure)
    }

    @Test
    fun `streaming parser rejects document type declarations`() {
        val content =
            decodedRemoteContent(
                ByteArrayInputStream("<!DOCTYPE tv [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><tv/>".toByteArray()),
                4_096,
            )

        try {
            assertFailsWith<Exception> { JvmXmlTvParser.parse(content) }
        } finally {
            content.close()
        }
    }
}
