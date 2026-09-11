package hu.wukki.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path

/** Desktop composition root for storage, networking, XML parsing, and diagnostics. */
object DesktopAppGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val dependencies: WukkiAppDependencies by lazy {
        WukkiAppDependencies(
            stateStore = DesktopStateStore(Path.of(System.getProperty("user.home"), ".wukki-tv")),
            remoteTextLoader = JvmRemoteTextLoader,
            xmlTvParser = JvmXmlTvParser,
            deviceInfoProvider = DesktopDeviceInfoProvider,
            dispatchers = DispatcherProvider(io = Dispatchers.IO),
        )
    }

    val bootstrap: AppBootstrap by lazy { AppBootstrap(dependencies, scope) }
}
