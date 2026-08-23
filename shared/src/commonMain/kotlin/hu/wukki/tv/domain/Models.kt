package hu.wukki.tv

import kotlinx.serialization.Serializable as KotlinSerializable

@KotlinSerializable enum class PlaylistSource { URL, FILE }
@KotlinSerializable enum class AppLanguage { HUNGARIAN, ENGLISH }
@KotlinSerializable enum class RefreshInterval(val hours: Int) { MANUAL(0), SIX_HOURS(6), TWELVE_HOURS(12), DAILY(24) }
@KotlinSerializable enum class BufferProfile { LOW_LATENCY, BALANCED, STABLE }
@KotlinSerializable enum class AspectRatioMode { AUTO, RATIO_16_9, RATIO_4_3, RATIO_21_9, FILL_CROP }
@KotlinSerializable enum class ChannelListDisplayMode { COMPACT, NORMAL, DETAILED }

@KotlinSerializable
data class PlaybackSettings(
    val volume: Int = 100,
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    /** Nullable only for compatibility with settings serialized before autoplay support. */
    val autoPlayOnLaunch: Boolean? = true,
    val autoReconnect: Boolean = true,
    val reconnectAttempts: Int = 3,
    /** Nullable only for compatibility with settings serialized before this field existed. */
    val aspectRatio: AspectRatioMode? = AspectRatioMode.AUTO
) : Persistable {
    companion object { const val serialVersionUID: Long = -8523174791077887180L }
}

@KotlinSerializable
data class DisplaySettings(
    val uiScale: Float = 1f,
    /** Nullable only for compatibility with state written before channel-list modes existed. */
    val channelListMode: ChannelListDisplayMode? = ChannelListDisplayMode.NORMAL,
    val showChannelProgramme: Boolean = true,
    val showMiniGuide: Boolean = true,
    val showLogos: Boolean = true,
    /** Nullable only for compatibility with state written before programme-image support. */
    val showProgrammeImages: Boolean? = true
) : Persistable {
    companion object { const val serialVersionUID: Long = -4713068168860025348L }
}

@KotlinSerializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.HUNGARIAN,
    val playlistRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val epgRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val playback: PlaybackSettings = PlaybackSettings(),
    val display: DisplaySettings = DisplaySettings()
) : Persistable

@KotlinSerializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val lastUpdatedAt: Long? = null,
    val managedByPlaylist: Boolean = false
) : Persistable

@KotlinSerializable
data class PlaylistDefinition(
    val id: String,
    val name: String,
    val location: String,
    val source: PlaylistSource,
    val updatedAt: Long
) : Persistable

@KotlinSerializable
data class Channel(
    val id: String,
    val playlistId: String,
    val name: String,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgChno: Int? = null,
    val group: String,
    val logo: String?,
    val favorite: Boolean = false,
    val epgChannelId: String? = null,
    val epgSourceId: String? = null,
    /** Optional M3U `tvg-shift`, expressed in hours, applied when this channel's EPG is shown. */
    val tvgShiftHours: Double? = null
) : Persistable {
    companion object { const val serialVersionUID: Long = -1321689634413548830L }
}

@KotlinSerializable
data class Programme(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null,
    /** Optional artwork URL supplied by XMLTV's programme icon metadata. */
    val imageUrl: String? = null
) : Persistable {
    companion object { const val serialVersionUID: Long = -2907961961909864784L }
}

@KotlinSerializable
data class AppState(
    val playlists: List<PlaylistDefinition> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val epgUrl: String = "",
    val autoRefreshHours: Int = 0,
    val lastChannelId: String? = null,
    val settings: AppSettings? = null,
    val epgSources: List<EpgSource>? = null,
    val epgProgrammesBySource: Map<String, List<Programme>>? = null
) : Persistable {
    companion object { const val serialVersionUID: Long = -8266148574268495181L }
    fun normalized(): AppState {
        val loadedSettings = settings ?: AppSettings(playlistRefresh = RefreshInterval.entries.first { it.hours == autoRefreshHours })
        // Java serialization supplies null for fields that did not exist in older state files.
        // Normalising here preserves the intended, enabled-by-default autoplay behaviour.
        val migratedSettings = loadedSettings.copy(
            playback = loadedSettings.playback.copy(autoPlayOnLaunch = loadedSettings.playback.autoPlayOnLaunch ?: true),
            display = loadedSettings.display.copy(
                channelListMode = loadedSettings.display.channelListMode ?: ChannelListDisplayMode.NORMAL,
                showProgrammeImages = loadedSettings.display.showProgrammeImages ?: true
            )
        )
        val migratedSources = epgSources ?: epgUrl.takeIf { it.isNotBlank() }?.let {
            listOf(EpgSource(id = "legacy-epg", name = "EPG", url = it, lastUpdatedAt = null))
        }.orEmpty()
        val migratedCache = epgProgrammesBySource ?: migratedSources.firstOrNull()?.let { mapOf(it.id to programmes) }.orEmpty()
        return copy(settings = migratedSettings, epgSources = migratedSources, epgProgrammesBySource = migratedCache)
    }
}
