package hu.wukki.tv

import android.content.Context

/** Process-wide Android composition root shared by MainActivity and WorkManager workers. */
object AndroidAppGraph {
    @Volatile
    private var installedDependencies: WukkiAppDependencies? = null

    fun install(context: Context): WukkiAppDependencies = installedDependencies ?: synchronized(this) {
        installedDependencies ?: run {
            val appContext = context.applicationContext
            LocalStore.install(appContext)
            WukkiAppDependencies(
                stateStore = LocalStore,
                remoteTextLoader = RemoteTextLoader(::readRemoteText),
                xmlTvParser = EpgParser,
                deviceInfoProvider = AndroidDeviceInfoProvider(appContext)
            ).also { installedDependencies = it }
        }
    }
}
