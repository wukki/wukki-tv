package hu.wukki.tv

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteFailureTest {
    @Test
    fun `HTTP status is retained as a typed error`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/unavailable") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val error =
                assertFailsWith<AppOperationException> {
                    JvmRemoteTextLoader.load("http://127.0.0.1:${server.address.port}/unavailable")
                }
            assertEquals(AppFailure.HttpError(503), error.failure)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `invalid URL is typed before connecting`() {
        val error = assertFailsWith<AppOperationException> { JvmRemoteTextLoader.load("file:///tmp/playlist") }
        assertEquals(AppFailure.InvalidRemoteUrl, error.failure)
    }
}
