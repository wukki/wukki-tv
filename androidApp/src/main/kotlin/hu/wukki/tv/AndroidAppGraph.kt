package hu.wukki.tv

import android.content.Context
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** Process-wide Android composition root shared by MainActivity and WorkManager workers. */
object AndroidAppGraph {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshService = RefreshService(processScope)
    private var contentModel: WukkiModel? = null

    /** The sole content owner survives Activity recreation and also exists in worker-only processes. */
    @MainThread
    fun model(context: Context): WukkiModel = contentModel ?: install(context).let { dependencies ->
        WukkiModel(dependencies.stateStore.load(), dependencies.remoteTextLoader,
            dependencies.xmlTvParser, dependencies.stateStore::save, refreshService)
            .also { contentModel = it }
    }

    /** Workers submit requests to the same owner the UI observes; they never load a private snapshot. */
    suspend fun requestRefresh(context: Context, type: String?): Boolean = withContext(Dispatchers.Main.immediate) {
        val owner = model(context)
        val success = when (type) {
            "PLAYLIST" -> owner.refreshDuePlaylist()
            "EPG" -> owner.refreshDueEpgSources(owner.settings.epgRefresh)
            else -> false
        }
        LocalStore.flush()
        success
    }

    @Volatile
    private var installedDependencies: WukkiAppDependencies? = null

    fun install(context: Context): WukkiAppDependencies = installedDependencies ?: synchronized(this) {
        installedDependencies ?: run {
            val appContext = context.applicationContext
            LocalStore.install(appContext)
            WukkiAppDependencies(
                stateStore = LocalStore,
                remoteTextLoader = JvmRemoteTextLoader,
                xmlTvParser = JvmXmlTvParser,
                deviceInfoProvider = AndroidDeviceInfoProvider(appContext)
            ).also { installedDependencies = it }
        }
    }
}
