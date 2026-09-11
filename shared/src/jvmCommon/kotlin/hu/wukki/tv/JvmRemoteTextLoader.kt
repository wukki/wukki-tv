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
        val uri =
            try {
                URI(request.url)
            } catch (exception: Exception) {
                throw AppOperationException(AppFailure.InvalidRemoteUrl, exception)
            }
        if ((!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) || uri.host.isNullOrBlank()) {
            throw AppOperationException(AppFailure.InvalidRemoteUrl)
        }
        val connection = uri.toURL().openConnection().configuredForWukki()
        try {
            if (connection is HttpURLConnection && connection.responseCode !in 200..299) {
                throw AppOperationException(AppFailure.HttpError(connection.responseCode))
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > request.kind.maxBodyBytes) {
                throw RemoteBodyTooLargeException(request.kind.maxBodyBytes)
            }
            return decodeRemoteText(connection.getInputStream(), request.kind.maxBodyBytes)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
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
    LimitedInputStream(input, maxBodyBytes).let(::BufferedInputStream).use { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) GZIPInputStream(buffered) else buffered
        LimitedInputStream(decoded, maxBodyBytes).use { limited ->
            limited.readBytes().toString(Charsets.UTF_8)
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
        val allowed = (limit - count + 1).coerceAtMost(length)
        if (allowed <= 0) throw RemoteBodyTooLargeException(limit)
        val read = super.read(buffer, offset, allowed)
        if (read > 0 && (count + read).also { count = it } > limit) throw RemoteBodyTooLargeException(limit)
        return read
    }
}
