package hu.wukki.tv

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URI
import java.util.zip.GZIPInputStream

object JvmRemoteTextLoader : RemoteTextLoader {
    override fun load(url: String): String {
        val connection = URI(url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        return decodeRemoteText(connection.getInputStream())
    }
}

internal fun decodeRemoteText(input: InputStream): String = BufferedInputStream(input).use { buffered ->
    buffered.mark(2)
    val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
    buffered.reset()
    val decoded = if (gzip) GZIPInputStream(buffered) else buffered
    decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
