package hu.wukki.tv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val Context.wukkiStateDataStore by preferencesDataStore(name = "wukki_tv_state")

/** Android equivalent of the desktop state.bin store. State is kept as one atomic DataStore value. */
class AndroidStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun load(): AppState = runBlocking(Dispatchers.IO) {
        appContext.wukkiStateDataStore.data.first()[STATE_KEY]
            ?.let { saved -> runCatching { json.decodeFromString<AppState>(saved) }.getOrNull() }
            ?: AppState()
    }

    fun save(state: AppState) {
        val encoded = runCatching { json.encodeToString(state) }.getOrNull() ?: return
        AndroidRefreshScheduler.sync(appContext, state.settings ?: AppSettings())
        scope.launch {
            appContext.wukkiStateDataStore.edit { preferences -> preferences[STATE_KEY] = encoded }
        }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("app_state")
    }
}

/** Installed by MainActivity before [WukkiModel] is composed. */
object LocalStore : AppStateStore {
    private var store: AndroidStateStore? = null

    fun install(context: Context) {
        if (store == null) store = AndroidStateStore(context)
    }

    override fun load(): AppState = store?.load() ?: AppState()
    override fun save(state: AppState) = store?.save(state) ?: Unit
}

actual object PlatformAppServices {
    actual val stateStore: AppStateStore = LocalStore
    actual val remoteTextLoader: RemoteTextLoader = RemoteTextLoader(::readRemoteText)
}

private fun readRemoteText(url: String): String {
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
