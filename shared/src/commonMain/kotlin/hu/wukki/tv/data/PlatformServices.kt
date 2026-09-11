package hu.wukki.tv

/** Common persistence boundary. Each platform owns its physical storage implementation. */
interface AppStateStore {
    suspend fun load(): LoadStateResult

    /** Returns only after the state has been written; failures must propagate to the caller. */
    suspend fun save(state: AppState)
}

data class LoadStateResult(
    val state: AppState,
    val cacheWarning: Boolean = false,
)

/** Synchronous boundary used inside the model's background dispatcher. */
fun interface RemoteTextLoader {
    fun load(url: String): String

    /** Production loaders enforce the request's decoded body limit before returning a String. */
    fun load(request: RemoteTextRequest): String = load(request.url)
}

/** Closeable byte stream boundary used for large remote documents without materializing a String. */
interface RemoteContent {
    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun close()
}

/** Opens decoded content. The caller owns and must close the returned value. */
fun interface RemoteContentLoader {
    fun load(request: RemoteTextRequest): RemoteContent
}

internal fun textBackedContentLoader(loader: RemoteTextLoader): RemoteContentLoader = RemoteContentLoader { request -> ByteArrayRemoteContent(loader.load(request).encodeToByteArray()) }

private class ByteArrayRemoteContent(
    private val bytes: ByteArray,
) : RemoteContent {
    private var position = 0

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (position >= bytes.size) return -1
        val count = minOf(length, bytes.size - position)
        bytes.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override fun close() = Unit
}

enum class RemoteTextKind(
    val maxBodyBytes: Int,
) {
    PLAYLIST(2 * 1024 * 1024),
    EPG(32 * 1024 * 1024),
}

data class RemoteTextRequest(
    val url: String,
    val kind: RemoteTextKind,
)

/** Platform-owned XMLTV parser used by the common model. */
fun interface XmlTvParser {
    fun parse(xml: String): List<Programme>

    fun parse(content: RemoteContent): List<Programme> {
        var bytes = ByteArray(DEFAULT_CONTENT_BUFFER_SIZE)
        var size = 0
        val buffer = ByteArray(DEFAULT_CONTENT_BUFFER_SIZE)
        while (true) {
            val count = content.read(buffer, 0, buffer.size)
            if (count < 0) break
            if (size + count > bytes.size) {
                bytes = bytes.copyOf(maxOf(bytes.size * 2, size + count))
            }
            buffer.copyInto(bytes, size, 0, count)
            size += count
        }
        return parse(bytes.copyOf(size).decodeToString())
    }
}

private const val DEFAULT_CONTENT_BUFFER_SIZE = 8 * 1024

data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val installationId: String,
    val appDataBytes: Long,
    val availableStorageBytes: Long,
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
    val deviceInfoProvider: DeviceInfoProvider,
    val remoteContentLoader: RemoteContentLoader = textBackedContentLoader(remoteTextLoader),
    val clock: Clock = SystemClock,
    val dispatchers: DispatcherProvider = DispatcherProvider(),
)

fun formatByteSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = safe.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rendered = if (unit == 0) safe.toString() else ((value * 10).toInt() / 10.0).toString()
    return "$rendered ${units[unit]}"
}
