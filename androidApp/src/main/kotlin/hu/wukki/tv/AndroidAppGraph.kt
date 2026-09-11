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
    private var applicationBootstrap: ApplicationBootstrap? = null
    private var appBootstrap: AppBootstrap? = null

    /** The sole content owner survives Activity recreation and also exists in worker-only processes. */
    @Synchronized
    fun bootstrap(context: Context): AppBootstrap =
        appBootstrap ?: run {
            AppBootstrap(applicationBootstrap(context), processScope).also { appBootstrap = it }
        }

    /** Process-owned headless bootstrap used by both the UI adapter and WorkManager. */
    @Synchronized
    fun applicationBootstrap(context: Context): ApplicationBootstrap =
        applicationBootstrap ?: run {
            ApplicationBootstrap(install(context), processScope, refreshService).also { applicationBootstrap = it }
        }

    fun flushInBackground() {
        processScope.launch {
            try {
                applicationBootstrap?.flush()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // StateWriter retains the error for the UI and a later retry.
            }
        }
    }

    /** Workers invoke application use cases directly; no presentation model is constructed. */
    suspend fun runHeadlessRefresh(
        context: Context,
        type: BackgroundRefreshType,
    ): BackgroundRefreshResult =
        withContext(Dispatchers.Main.immediate) {
            applicationBootstrap(context).runRefresh(type)
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
                    remoteContentLoader = JvmRemoteContentLoader,
                    dispatchers = DispatcherProvider(io = Dispatchers.IO),
                ).also { installedDependencies = it }
            }
        }
}
