package hu.wukki.tv

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Process-wide Android composition root shared by MainActivity and WorkManager workers. */
object AndroidAppGraph {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshService = RefreshService(processScope)
    private var appBootstrap: AppBootstrap? = null

    /** The sole content owner survives Activity recreation and also exists in worker-only processes. */
    @Synchronized
    fun bootstrap(context: Context): AppBootstrap =
        appBootstrap ?: run {
            AppBootstrap(install(context), processScope, refreshService).also { appBootstrap = it }
        }

    fun flushInBackground() {
        processScope.launch {
            try {
                appBootstrap?.flush()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // StateWriter retains the error for the UI and a later retry.
            }
        }
    }

    /** Workers submit requests to the same owner the UI observes; they never load a private snapshot. */
    suspend fun requestRefresh(
        context: Context,
        type: String?,
    ): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val ready = bootstrap(context).awaitReady()
            val owner = ready.model
            val success =
                when (type) {
                    "PLAYLIST" -> owner.refreshDuePlaylist()
                    "EPG" -> owner.refreshDueEpgSources(owner.settings.epgRefresh)
                    else -> false
                }
            ready.writer.flush()
            success
        }

    @Volatile
    private var installedDependencies: WukkiAppDependencies? = null

    fun install(context: Context): WukkiAppDependencies =
        installedDependencies ?: synchronized(this) {
            installedDependencies ?: run {
                val appContext = context.applicationContext
                WukkiAppDependencies(
                    stateStore = AndroidStateStore(appContext),
                    remoteTextLoader = JvmRemoteTextLoader,
                    xmlTvParser = JvmXmlTvParser,
                    deviceInfoProvider = AndroidDeviceInfoProvider(appContext),
                ).also { installedDependencies = it }
            }
        }
}
