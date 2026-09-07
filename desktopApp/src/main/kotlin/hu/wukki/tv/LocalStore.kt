package hu.wukki.tv

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/** Desktop state lives in portable JSON; `state.bin` is read once for existing installations. */
internal class DesktopStateStore(private val directory: Path) : AppStateStore {
    private val jsonPath: Path = directory.resolve("state.json")
    private val legacyPath: Path = directory.resolve("state.bin")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }

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
        (LegacyStateBinAdapter.load(legacyPath) ?: AppState()).also(::save)
    }.getOrElse { AppState() }
}

object LocalStore : AppStateStore by DesktopStateStore(Path.of(System.getProperty("user.home"), ".wukki-tv"))
