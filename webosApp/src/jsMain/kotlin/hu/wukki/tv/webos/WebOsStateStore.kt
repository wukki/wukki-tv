package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.normalizedChannelHistory
import kotlin.js.JSON
import kotlin.js.jsTypeOf

internal const val WEBOS_STATE_SCHEMA_VERSION = 1
internal const val WEBOS_STATE_STORAGE_KEY = "hu.wukki.tv.webos.state.v1"

internal data class WebOsSettings(
    val language: String = "HUNGARIAN",
    val playlistRefreshHours: Int = 0,
    val epgRefreshHours: Int = 0,
    val volume: Int = 100,
    val bufferProfile: String = "BALANCED",
    val autoPlayOnLaunch: Boolean = true,
    val autoReconnect: Boolean = true,
    val reconnectAttempts: Int = 3,
    val aspectRatio: String = "AUTO",
    val uiScale: Double = 1.0,
    val channelListMode: String = "NORMAL",
    val showChannelProgramme: Boolean = true,
    val showMiniGuide: Boolean = true,
    val showLogos: Boolean = true,
    val showProgrammeImages: Boolean = true,
)

internal data class WebOsStoredState(
    val playlistUrl: String,
    val playlistUpdatedAt: Long,
    val channels: List<Channel>,
    val lastChannelId: String? = null,
    val recentChannelIds: List<String> = emptyList(),
    val settings: WebOsSettings = WebOsSettings(),
) {
    fun normalized(): WebOsStoredState {
        val normalizedHistory = normalizedChannelHistory(recentChannelIds.ifEmpty { listOfNotNull(lastChannelId) }, channels)
        return copy(
            lastChannelId = lastChannelId?.takeIf { id -> channels.any { it.id == id } },
            recentChannelIds = normalizedHistory,
        )
    }
}

internal data class WebOsStateLoadResult(
    val state: WebOsStoredState? = null,
    val error: String? = null,
)

internal class WebOsStateStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    fun load(): WebOsStateLoadResult {
        val raw =
            try {
                read()
            } catch (error: Throwable) {
                return WebOsStateLoadResult(error = "A helyi tároló nem olvasható: ${error.message ?: error}")
            } ?: return WebOsStateLoadResult()

        return try {
            val envelope = JSON.parse<dynamic>(raw)
            val version = requiredInt(envelope.schemaVersion, "schemaVersion")
            require(version == WEBOS_STATE_SCHEMA_VERSION) { "Nem támogatott tárolási séma: $version" }
            val state = decodeState(envelope.state).normalized()
            require(state.channels.isNotEmpty()) { "A mentett csatornalista üres." }
            WebOsStateLoadResult(state = state)
        } catch (error: Throwable) {
            WebOsStateLoadResult(error = "A mentett állapot sérült: ${error.message ?: error}")
        }
    }

    fun save(state: WebOsStoredState): String? {
        if (state.channels.isEmpty()) return "Üres csatornalista nem menthető."
        return try {
            val envelope = js("({})")
            envelope.schemaVersion = WEBOS_STATE_SCHEMA_VERSION
            envelope.state = encodeState(state.normalized())
            write(JSON.stringify(envelope))
            null
        } catch (error: Throwable) {
            "A helyi állapot nem menthető: ${error.message ?: error}"
        }
    }
}

private fun encodeState(state: WebOsStoredState): dynamic {
    val encoded = js("({})")
    encoded.playlistUrl = state.playlistUrl
    encoded.playlistUpdatedAt = state.playlistUpdatedAt.toDouble()
    encoded.channels = state.channels.map(::encodeChannel).toTypedArray()
    encoded.lastChannelId = state.lastChannelId
    encoded.recentChannelIds = state.recentChannelIds.toTypedArray()
    encoded.settings = encodeSettings(state.settings)
    return encoded
}

private fun decodeState(encoded: dynamic): WebOsStoredState {
    require(encoded != null) { "Hiányzik a state mező." }
    return WebOsStoredState(
        playlistUrl = requiredString(encoded.playlistUrl, "playlistUrl"),
        playlistUpdatedAt = requiredLong(encoded.playlistUpdatedAt, "playlistUpdatedAt"),
        channels = requiredArray(encoded.channels, "channels").map(::decodeChannel),
        lastChannelId = optionalString(encoded.lastChannelId),
        recentChannelIds = requiredArray(encoded.recentChannelIds, "recentChannelIds").map { requiredString(it, "recentChannelIds[]") },
        settings = decodeSettings(encoded.settings),
    )
}

private fun encodeChannel(channel: Channel): dynamic {
    val encoded = js("({})")
    encoded.id = channel.id
    encoded.playlistId = channel.playlistId
    encoded.name = channel.name
    encoded.streamUrl = channel.streamUrl
    encoded.tvgId = channel.tvgId
    encoded.tvgName = channel.tvgName
    encoded.tvgChno = channel.tvgChno
    encoded.group = channel.group
    encoded.logo = channel.logo
    encoded.favorite = channel.favorite
    encoded.epgChannelId = channel.epgChannelId
    encoded.epgSourceId = channel.epgSourceId
    encoded.tvgShiftHours = channel.tvgShiftHours
    return encoded
}

private fun decodeChannel(encoded: dynamic): Channel =
    Channel(
        id = requiredString(encoded.id, "channel.id"),
        playlistId = requiredString(encoded.playlistId, "channel.playlistId"),
        name = requiredString(encoded.name, "channel.name"),
        streamUrl = requiredString(encoded.streamUrl, "channel.streamUrl"),
        tvgId = optionalString(encoded.tvgId),
        tvgName = optionalString(encoded.tvgName),
        tvgChno = optionalInt(encoded.tvgChno),
        group = requiredString(encoded.group, "channel.group"),
        logo = optionalString(encoded.logo),
        favorite = optionalBoolean(encoded.favorite) ?: false,
        epgChannelId = optionalString(encoded.epgChannelId),
        epgSourceId = optionalString(encoded.epgSourceId),
        tvgShiftHours = optionalDouble(encoded.tvgShiftHours),
    )

private fun encodeSettings(settings: WebOsSettings): dynamic {
    val encoded = js("({})")
    encoded.language = settings.language
    encoded.playlistRefreshHours = settings.playlistRefreshHours
    encoded.epgRefreshHours = settings.epgRefreshHours
    encoded.volume = settings.volume
    encoded.bufferProfile = settings.bufferProfile
    encoded.autoPlayOnLaunch = settings.autoPlayOnLaunch
    encoded.autoReconnect = settings.autoReconnect
    encoded.reconnectAttempts = settings.reconnectAttempts
    encoded.aspectRatio = settings.aspectRatio
    encoded.uiScale = settings.uiScale
    encoded.channelListMode = settings.channelListMode
    encoded.showChannelProgramme = settings.showChannelProgramme
    encoded.showMiniGuide = settings.showMiniGuide
    encoded.showLogos = settings.showLogos
    encoded.showProgrammeImages = settings.showProgrammeImages
    return encoded
}

private fun decodeSettings(encoded: dynamic): WebOsSettings {
    if (encoded == null) return WebOsSettings()
    return WebOsSettings(
        language = optionalString(encoded.language) ?: "HUNGARIAN",
        playlistRefreshHours = optionalInt(encoded.playlistRefreshHours) ?: 0,
        epgRefreshHours = optionalInt(encoded.epgRefreshHours) ?: 0,
        volume = optionalInt(encoded.volume) ?: 100,
        bufferProfile = optionalString(encoded.bufferProfile) ?: "BALANCED",
        autoPlayOnLaunch = optionalBoolean(encoded.autoPlayOnLaunch) ?: true,
        autoReconnect = optionalBoolean(encoded.autoReconnect) ?: true,
        reconnectAttempts = optionalInt(encoded.reconnectAttempts) ?: 3,
        aspectRatio = optionalString(encoded.aspectRatio) ?: "AUTO",
        uiScale = optionalDouble(encoded.uiScale) ?: 1.0,
        channelListMode = optionalString(encoded.channelListMode) ?: "NORMAL",
        showChannelProgramme = optionalBoolean(encoded.showChannelProgramme) ?: true,
        showMiniGuide = optionalBoolean(encoded.showMiniGuide) ?: true,
        showLogos = optionalBoolean(encoded.showLogos) ?: true,
        showProgrammeImages = optionalBoolean(encoded.showProgrammeImages) ?: true,
    )
}

private fun requiredArray(
    value: dynamic,
    field: String,
): Array<dynamic> {
    require(js("Array.isArray(value)") as Boolean) { "A(z) $field mező nem lista." }
    return value as Array<dynamic>
}

private fun requiredString(
    value: dynamic,
    field: String,
): String {
    require(jsTypeOf(value) == "string" && (value as String).isNotBlank()) { "A(z) $field mező hiányzik." }
    return value
}

private fun optionalString(value: dynamic): String? = if (value == null || jsTypeOf(value) != "string") null else value as String

private fun requiredInt(
    value: dynamic,
    field: String,
): Int = optionalInt(value) ?: error("A(z) $field mező hiányzik.")

private fun requiredLong(
    value: dynamic,
    field: String,
): Long = optionalDouble(value)?.toLong() ?: error("A(z) $field mező hiányzik.")

private fun optionalInt(value: dynamic): Int? = optionalDouble(value)?.toInt()

private fun optionalDouble(value: dynamic): Double? = if (jsTypeOf(value) == "number") value as Double else null

private fun optionalBoolean(value: dynamic): Boolean? = if (jsTypeOf(value) == "boolean") value as Boolean else null
