package hu.wukki.tv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val Context.wukkiStateDataStore by preferencesDataStore(name = "wukki_tv_state")

/** Android state store: compact preferences plus a separately compressed, atomic EPG cache. */
internal class AndroidStateStore(
    context: Context,
    private val onSettingsSaved: (AppSettings) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val pendingStates = Channel<AppState>(Channel.CONFLATED)
    private val epgCacheFile = File(appContext.filesDir, "epg_cache.json.gz")
    private var lastPersistedCache: Map<String, List<Programme>>? = null

    init {
        scope.launch {
            for (state in pendingStates) persistLatest(state)
        }
    }

    fun load(): AppState = runBlocking(Dispatchers.IO) {
        val stored = appContext.wukkiStateDataStore.data.first()[STATE_KEY]
            ?.let { saved -> runCatching { json.decodeFromString<AppState>(saved) }.getOrNull() }
            ?: AppState()
        val fileCache = readEpgCache()
        val embeddedCache = stored.epgProgrammesBySource
            ?: stored.epgSources.orEmpty().firstOrNull()?.let { source ->
                stored.programmes.takeIf { it.isNotEmpty() }?.let { mapOf(source.id to it) }
            }
        val cache = fileCache ?: embeddedCache.orEmpty()
        lastPersistedCache = fileCache
        val migrated = stored.copy(programmes = emptyList(), epgProgrammesBySource = cache)
        if (fileCache == null && embeddedCache != null) pendingStates.trySend(migrated)
        migrated
    }

    fun save(state: AppState) {
        onSettingsSaved(state.settings ?: AppSettings())
        pendingStates.trySend(state)
    }

    private suspend fun persistLatest(state: AppState) {
        val cache = state.epgProgrammesBySource.orEmpty()
        if (cache !== lastPersistedCache) {
            if (writeEpgCache(cache)) lastPersistedCache = cache
        }
        val lightweightState = state.copy(programmes = emptyList(), epgProgrammesBySource = emptyMap())
        val encoded = runCatching { json.encodeToString(lightweightState) }.getOrNull() ?: return
        appContext.wukkiStateDataStore.edit { preferences -> preferences[STATE_KEY] = encoded }
    }

    private fun readEpgCache(): Map<String, List<Programme>>? = runCatching {
        if (!epgCacheFile.isFile) return@runCatching null
        GZIPInputStream(epgCacheFile.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use { reader ->
            json.decodeFromString<Map<String, List<Programme>>>(reader.readText())
        }
    }.getOrNull()

    private fun writeEpgCache(cache: Map<String, List<Programme>>): Boolean = runCatching {
        val temporary = File(epgCacheFile.parentFile, "${epgCacheFile.name}.tmp")
        GZIPOutputStream(temporary.outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(cache))
        }
        try {
            java.nio.file.Files.move(
                temporary.toPath(),
                epgCacheFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temporary.toPath(),
                epgCacheFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
        true
    }.getOrDefault(false)

    private companion object {
        val STATE_KEY = stringPreferencesKey("app_state")
    }
}

/** Installed by MainActivity before [WukkiModel] is composed. */
object LocalStore : AppStateStore {
    private var store: AndroidStateStore? = null

    fun install(context: Context) {
        if (store == null) {
            val appContext = context.applicationContext
            store = AndroidStateStore(appContext) { settings -> AndroidRefreshScheduler.sync(appContext, settings) }
        }
    }

    override fun load(): AppState = store?.load() ?: AppState()
    override fun save(state: AppState) = store?.save(state) ?: Unit
}

internal fun readRemoteText(url: String): String {
    val connection = java.net.URI(url).toURL().openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    java.io.BufferedInputStream(connection.getInputStream()).use { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) java.util.zip.GZIPInputStream(buffered) else buffered
        return decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
