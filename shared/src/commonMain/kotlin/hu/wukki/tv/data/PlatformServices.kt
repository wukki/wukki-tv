package hu.wukki.tv

/** Common persistence boundary. Each platform owns its physical storage implementation. */
interface AppStateStore {
    fun load(): AppState
    fun save(state: AppState)
}

/** Keeps the legacy desktop binary reader source-compatible without exposing `java.io` to common code. */
expect interface Persistable

/** Synchronous boundary used inside the model's background dispatcher. */
fun interface RemoteTextLoader {
    fun load(url: String): String

    /** Production loaders enforce the request's decoded body limit before returning a String. */
    fun load(request: RemoteTextRequest): String = load(request.url)
}

enum class RemoteTextKind(val maxBodyBytes: Int) {
    PLAYLIST(2 * 1024 * 1024),
    EPG(32 * 1024 * 1024)
}

data class RemoteTextRequest(val url: String, val kind: RemoteTextKind)

/** Platform-owned XMLTV parser used by the common model. */
fun interface XmlTvParser {
    fun parse(xml: String): List<Programme>
}

data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val installationId: String,
    val appDataBytes: Long,
    val availableStorageBytes: Long
)

/** Platform-owned diagnostics provider used by the common About screen. */
fun interface DeviceInfoProvider {
    fun collect(): DeviceInfo
}

/** Explicit application boundary assembled by each platform entry point. */
data class WukkiAppDependencies(
    val stateStore: AppStateStore,
    val remoteTextLoader: RemoteTextLoader,
    val xmlTvParser: XmlTvParser,
    val deviceInfoProvider: DeviceInfoProvider
)

fun formatByteSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = safe.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    val rendered = if (unit == 0) safe.toString() else ((value * 10).toInt() / 10.0).toString()
    return "$rendered ${units[unit]}"
}
