package hu.wukki.tv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.wukkiStateDataStore by preferencesDataStore(name = "wukki_tv_state")

/** The caller owns save scheduling; this adapter returns only after the cache and preferences are written. */
internal class AndroidStateStore(
    private val dataStore: DataStore<Preferences>,
    private val epgCacheFile: EpgCacheFile,
    private val onStateSaved: (AppState) -> Unit = {},
) : AppStateStore {
    constructor(context: Context) : this(
        context.applicationContext.wukkiStateDataStore,
        EpgCacheFile(File(context.applicationContext.filesDir, "epg_cache.json.gz"), stateJson),
        { state -> AndroidRefreshScheduler.sync(context.applicationContext, state) },
    )

    private val access = Mutex()
    private var lastPersistedCache: Map<String, List<Programme>>? = null

    override suspend fun load(): LoadStateResult =
        withContext(Dispatchers.IO) {
            access.withLock {
                val stored =
                    dataStore.data
                        .first()[STATE_KEY]
                        ?.let { saved -> stateJson.decodeFromString<AppState>(saved) }
                        ?: AppState()
                val fileCache = epgCacheFile.read()
                val embeddedCache =
                    stored.epgProgrammesBySource.takeIf { it.isNotEmpty() }
                        ?: stored.epgSources.firstOrNull()?.let { source ->
                            stored.programmes.takeIf { it.isNotEmpty() }?.let { mapOf(source.id to it) }
                        }
                val cache = fileCache ?: embeddedCache.orEmpty()
                lastPersistedCache = fileCache
                val cacheWarning =
                    fileCache == null && embeddedCache == null &&
                        (epgCacheFile.exists() || stored.epgSources.any { it.lastUpdatedAt != null })
                val migrated =
                    stored.copy(
                        programmes = emptyList(),
                        epgProgrammesBySource = cache,
                        epgSources = if (cacheWarning) stored.epgSources.map { it.copy(lastUpdatedAt = null) } else stored.epgSources,
                    )
                if (fileCache == null && embeddedCache != null) persist(migrated)
                LoadStateResult(migrated, cacheWarning)
            }
        }

    override suspend fun save(state: AppState) =
        withContext(Dispatchers.IO) {
            access.withLock { persist(state) }
        }

    private suspend fun persist(state: AppState) {
        val cache = state.epgProgrammesBySource
        if (cache !== lastPersistedCache) {
            check(cache == lastPersistedCache || epgCacheFile.write(cache)) { "Could not persist EPG cache" }
            lastPersistedCache = cache
        }
        val lightweightState = state.copy(programmes = emptyList(), epgProgrammesBySource = emptyMap())
        val encoded = stateJson.encodeToString(lightweightState)
        dataStore.edit { preferences -> preferences[STATE_KEY] = encoded }
        onStateSaved(state)
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("app_state")
        val stateJson =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
    }
}
