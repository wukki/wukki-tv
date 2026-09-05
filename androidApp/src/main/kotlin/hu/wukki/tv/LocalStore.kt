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
import kotlinx.serialization.json.Json
import java.io.File

private val Context.wukkiStateDataStore by preferencesDataStore(name = "wukki_tv_state")

/** Android state store: compact preferences plus a separately compressed, atomic EPG cache. */
internal class AndroidStateStore(
    context: Context,
    private val onStateSaved: (AppState) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val pendingStates = Channel<AppState>(Channel.CONFLATED)
    private val epgCacheFile = EpgCacheFile(File(appContext.filesDir, "epg_cache.json.gz"), json)
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
        val fileCache = epgCacheFile.read()
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
        pendingStates.trySend(state)
    }

    private suspend fun persistLatest(state: AppState) {
        val cache = state.epgProgrammesBySource.orEmpty()
        if (cache !== lastPersistedCache) {
            if (cache == lastPersistedCache || epgCacheFile.write(cache)) lastPersistedCache = cache
        }
        val lightweightState = state.copy(programmes = emptyList(), epgProgrammesBySource = emptyMap())
        val encoded = runCatching { json.encodeToString(lightweightState) }.getOrNull() ?: return
        appContext.wukkiStateDataStore.edit { preferences -> preferences[STATE_KEY] = encoded }
        onStateSaved(state)
    }

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
            store = AndroidStateStore(appContext) { state -> AndroidRefreshScheduler.sync(appContext, state) }
        }
    }

    override fun load(): AppState = store?.load() ?: AppState()
    override fun save(state: AppState) = store?.save(state) ?: Unit
}
