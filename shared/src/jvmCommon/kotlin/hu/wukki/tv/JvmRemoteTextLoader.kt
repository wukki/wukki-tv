package hu.wukki.tv

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.util.zip.GZIPInputStream

object JvmRemoteTextLoader : RemoteTextLoader {
    override fun load(url: String): String = load(RemoteTextRequest(url, RemoteTextKind.EPG))

    override fun load(request: RemoteTextRequest): String {
        val connection = openRemoteConnection(request)
        try {
            return decodeRemoteText(connection.getInputStream(), request.kind.maxBodyBytes)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }
}

/** Keeps the HTTP connection open until the SAX parser closes the decoded, size-limited stream. */
object JvmRemoteContentLoader : RemoteContentLoader {
    override fun load(request: RemoteTextRequest): RemoteContent {
        val connection = openRemoteConnection(request)
        return try {
            decodedRemoteContent(connection.getInputStream(), request.kind.maxBodyBytes) {
                (connection as? HttpURLConnection)?.disconnect()
            }
        } catch (exception: Exception) {
            (connection as? HttpURLConnection)?.disconnect()
            throw exception
        }
    }
}

private fun openRemoteConnection(request: RemoteTextRequest): URLConnection {
    val uri =
        try {
            URI(request.url)
        } catch (exception: Exception) {
            throw AppOperationException(AppFailure.InvalidRemoteUrl, exception)
        }
    if ((!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) || uri.host.isNullOrBlank()) {
        throw AppOperationException(AppFailure.InvalidRemoteUrl)
    }
    return uri.toURL().openConnection().configuredForWukki().also { connection ->
        if (connection is HttpURLConnection && connection.responseCode !in 200..299) {
            connection.disconnect()
            throw AppOperationException(AppFailure.HttpError(connection.responseCode))
        }
        if (connection.contentLengthLong > request.kind.maxBodyBytes) {
            (connection as? HttpURLConnection)?.disconnect()
            throw RemoteBodyTooLargeException(request.kind.maxBodyBytes)
        }
    }
}

internal fun URLConnection.configuredForWukki(): URLConnection =
    apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "WukkiTV/${WukkiBuildInfo.VERSION}")
    }

internal fun decodeRemoteText(
    input: InputStream,
    maxBodyBytes: Int,
): String =
    decodedRemoteStream(input, maxBodyBytes).use { decoded ->
        decoded.readBytes().toString(Charsets.UTF_8)
    }

private fun decodedRemoteStream(
    input: InputStream,
    maxBodyBytes: Int,
): InputStream =
    LimitedInputStream(input, maxBodyBytes).let(::BufferedInputStream).let { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) GZIPInputStream(buffered) else buffered
        LimitedInputStream(decoded, maxBodyBytes)
    }

internal fun decodedRemoteContent(
    input: InputStream,
    maxBodyBytes: Int,
    onClose: () -> Unit = {},
): RemoteContent = StreamRemoteContent(decodedRemoteStream(input, maxBodyBytes), onClose)

private class StreamRemoteContent(
    private val input: InputStream,
    private val onClose: () -> Unit,
) : RemoteContent {
    private var closed = false

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        return try {
            input.read(buffer, offset, length)
        } catch (exception: AppOperationException) {
            throw exception
        } catch (exception: Exception) {
            throw AppOperationException(AppFailure.NetworkUnavailable, exception)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            input.close()
        } finally {
            onClose()
        }
    }
}

internal class RemoteBodyTooLargeException(
    maxBodyBytes: Int,
) : AppOperationException(AppFailure.ResponseTooLarge, IllegalArgumentException("Remote response exceeds the $maxBodyBytes byte limit"))

private class LimitedInputStream(
    input: InputStream,
    private val limit: Int,
) : FilterInputStream(input) {
    private var count = 0

    override fun read(): Int {
        val value = super.read()
        if (value >= 0 && ++count > limit) throw RemoteBodyTooLargeException(limit)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        val allowed = (limit - count + 1).coerceAtMost(length)
        if (allowed <= 0) throw RemoteBodyTooLargeException(limit)
        val read = super.read(buffer, offset, allowed)
        if (read > 0 && (count + read).also { count = it } > limit) throw RemoteBodyTooLargeException(limit)
        return read
    }
}
