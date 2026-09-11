package hu.wukki.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Compact preferences and a separate compressed EPG cache; older embedded caches migrate on load. */
@OptIn(ExperimentalSerializationApi::class)
internal class DesktopStateStore(
    private val directory: Path,
    private val atomicMove: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    },
) : AppStateStore {
    private val jsonPath = directory.resolve("state.json")
    private val legacyPath = directory.resolve("state.bin")
    private val cachePath = directory.resolve("epg_cache.json.gz")
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    private val access = Mutex()
    private var savedCache: Map<String, List<Programme>>? = null
    private var savedSources: List<EpgSource>? = null

    override suspend fun load(): LoadStateResult =
        withContext(Dispatchers.IO) {
            access.withLock {
                val legacy = !Files.exists(jsonPath) && Files.exists(legacyPath)
                val stored =
                    when {
                        Files.exists(jsonPath) -> Files.newInputStream(jsonPath).buffered().use { json.decodeFromStream<AppState>(it) }
                        legacy -> checkNotNull(LegacyStateBinAdapter.load(legacyPath)) { "Could not read legacy state" }
                        else -> AppState()
                    }
                val embedded =
                    stored.epgProgrammesBySource.ifEmpty {
                        stored.epgSources
                            .firstOrNull()
                            ?.let { mapOf(it.id to stored.programmes) }
                            .orEmpty()
                            .takeIf { stored.programmes.isNotEmpty() }
                            .orEmpty()
                    }
                if (legacy || embedded.isNotEmpty()) {
                    val migrated = stored.copy(programmes = emptyList(), epgProgrammesBySource = embedded)
                    persist(migrated)
                    return@withLock LoadStateResult(migrated)
                }
                val cached = readCache()?.takeIf { it.epgSources == stored.epgSources }
                savedCache = cached?.epgProgrammesBySource
                savedSources = cached?.epgSources
                val warning = cached == null && (Files.exists(cachePath) || stored.epgSources.any { it.lastUpdatedAt != null })
                LoadStateResult(
                    stored.copy(
                        epgProgrammesBySource = savedCache.orEmpty(),
                        epgSources = if (warning) stored.epgSources.map { it.copy(lastUpdatedAt = null) } else stored.epgSources,
                    ),
                    cacheWarning = warning,
                )
            }
        }

    override suspend fun save(state: AppState) =
        withContext(Dispatchers.IO) {
            access.withLock { persist(state) }
        }

    private fun persist(state: AppState) {
        if (state.epgProgrammesBySource !== savedCache || state.epgSources != savedSources) {
            if (state.epgProgrammesBySource != savedCache || state.epgSources != savedSources) {
                val cache = AppState(epgSources = state.epgSources, epgProgrammesBySource = state.epgProgrammesBySource)
                atomicWrite(cachePath) { output -> GZIPOutputStream(output).use { json.encodeToStream(cache, it) } }
            }
            savedCache = state.epgProgrammesBySource
            savedSources = state.epgSources
        }
        val preferences = state.copy(programmes = emptyList(), epgProgrammesBySource = emptyMap())
        atomicWrite(jsonPath) { json.encodeToStream(preferences, it) }
    }

    private fun readCache(): AppState? =
        try {
            if (Files.exists(cachePath)) {
                GZIPInputStream(Files.newInputStream(cachePath).buffered()).use { json.decodeFromStream<AppState>(it) }
            } else {
                null
            }
        } catch (_: Exception) {
            // Cache is replaceable. Unlike user preferences, a bad cache must not prevent startup.
            null
        }

    private fun atomicWrite(
        target: Path,
        write: (OutputStream) -> Unit,
    ) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, target.fileName.toString(), ".tmp")
        try {
            Files.newOutputStream(temporary).buffered().use(write)
            try {
                atomicMove(temporary, target)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
