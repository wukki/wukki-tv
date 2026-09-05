package hu.wukki.tv

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Streams the large EPG cache so Android never creates its full JSON representation as a String. */
@OptIn(ExperimentalSerializationApi::class)
internal class EpgCacheFile(
    private val file: File,
    private val json: Json
) {
    fun read(): Map<String, List<Programme>>? = runCatching {
        if (!file.isFile) return@runCatching null
        GZIPInputStream(file.inputStream().buffered()).use { input ->
            json.decodeFromStream<Map<String, List<Programme>>>(input)
        }
    }.getOrNull()

    fun write(cache: Map<String, List<Programme>>): Boolean {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        return runCatching {
            GZIPOutputStream(temporary.outputStream().buffered()).use { output ->
                json.encodeToStream(cache, output)
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { temporary.delete() }.isSuccess
    }
}
