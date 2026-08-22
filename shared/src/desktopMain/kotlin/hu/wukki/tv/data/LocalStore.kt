package hu.wukki.tv

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/** Desktop state lives in portable JSON; `state.bin` is read once for existing installations. */
internal class DesktopStateStore(private val directory: Path) : AppStateStore {
    private val jsonPath: Path = directory.resolve("state.json")
    private val legacyPath: Path = directory.resolve("state.bin")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun load(): AppState = loadJson() ?: loadLegacy()

    override fun save(state: AppState) {
        runCatching {
            Files.createDirectories(directory)
            val temporary = jsonPath.resolveSibling("state.json.tmp")
            Files.newBufferedWriter(temporary).use { writer -> writer.write(json.encodeToString(AppState.serializer(), state)) }
            Files.move(temporary, jsonPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    private fun loadJson(): AppState? = runCatching {
        if (!Files.isRegularFile(jsonPath)) return null
        Files.newBufferedReader(jsonPath).use { reader -> json.decodeFromString(AppState.serializer(), reader.readText()) }
    }.getOrNull()

    private fun loadLegacy(): AppState = runCatching {
        if (!Files.isRegularFile(legacyPath)) return AppState()
        ObjectInputStream(BufferedInputStream(FileInputStream(legacyPath.toFile()))).use { stream ->
            (stream.readObject() as? AppState ?: AppState()).also(::save)
        }
    }.getOrElse { AppState() }
}

object LocalStore : AppStateStore by DesktopStateStore(Path.of(System.getProperty("user.home"), ".wukki-tv"))

actual object PlatformAppServices {
    actual val stateStore: AppStateStore = LocalStore
    actual val remoteTextLoader: RemoteTextLoader = RemoteTextLoader(::readRemoteText)
}

private fun readRemoteText(url: String): String {
    val connection = java.net.URI(url).toURL().openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    BufferedInputStream(connection.getInputStream()).use { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) java.util.zip.GZIPInputStream(buffered) else buffered
        return decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
