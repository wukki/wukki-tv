package hu.wukki.tv

/** Desktop composition root for storage, networking, XML parsing, and diagnostics. */
object DesktopAppGraph {
    val dependencies: WukkiAppDependencies by lazy {
        WukkiAppDependencies(
            stateStore = LocalStore,
            remoteTextLoader = RemoteTextLoader(::readRemoteText),
            xmlTvParser = EpgParser,
            deviceInfoProvider = DesktopDeviceInfoProvider
        )
    }
}
