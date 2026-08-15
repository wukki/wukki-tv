package hu.wukki.tv

import java.io.Serializable

enum class PlaylistSource { URL, FILE }
enum class AppLanguage { HUNGARIAN, ENGLISH }
enum class RefreshInterval(val hours: Int) { MANUAL(0), SIX_HOURS(6), TWELVE_HOURS(12), DAILY(24) }
enum class BufferProfile { LOW_LATENCY, BALANCED, STABLE }
enum class AspectRatioMode { AUTO, RATIO_16_9, RATIO_4_3, RATIO_21_9, FILL_CROP }
enum class ChannelListDisplayMode { COMPACT, NORMAL, DETAILED }

data class PlaybackSettings(
    val volume: Int = 100,
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    /** Nullable only for compatibility with settings serialized before autoplay support. */
    val autoPlayOnLaunch: Boolean? = true,
    val autoReconnect: Boolean = true,
    val reconnectAttempts: Int = 3,
    /** Nullable only for compatibility with settings serialized before this field existed. */
    val aspectRatio: AspectRatioMode? = AspectRatioMode.AUTO
) : Serializable {
    companion object {
        /** Retains compatibility with playback settings saved before aspect-ratio support. */
        @JvmField
        val serialVersionUID: Long = -8523174791077887180L
    }
}

data class DisplaySettings(
    val uiScale: Float = 1f,
    /** Nullable only for compatibility with state written before channel-list modes existed. */
    val channelListMode: ChannelListDisplayMode? = ChannelListDisplayMode.NORMAL,
    val showChannelProgramme: Boolean = true,
    val showMiniGuide: Boolean = true,
    val showLogos: Boolean = true
) : Serializable

data class AppSettings(
    val language: AppLanguage = AppLanguage.HUNGARIAN,
    val playlistRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val epgRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val playback: PlaybackSettings = PlaybackSettings(),
    val display: DisplaySettings = DisplaySettings()
) : Serializable

data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val lastUpdatedAt: Long? = null,
    val managedByPlaylist: Boolean = false
) : Serializable

data class PlaylistDefinition(
    val id: String,
    val name: String,
    val location: String,
    val source: PlaylistSource,
    val updatedAt: Long
) : Serializable

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
    val epgSourceId: String? = null
) : Serializable {
    companion object {
        /** Preserves compatibility with playlists saved before `tvg-chno` was added. */
        @JvmField
        val serialVersionUID: Long = -1321689634413548830L
    }
}

data class Programme(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null
) : Serializable {
    companion object {
        @JvmField
        val serialVersionUID: Long = -2907961961909864784L
    }
}

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
) : Serializable {
    companion object {
        @JvmField
        val serialVersionUID: Long = -8266148574268495181L
    }

    fun normalized(): AppState {
        val loadedSettings = settings ?: AppSettings(playlistRefresh = RefreshInterval.entries.first { it.hours == autoRefreshHours })
        // Java serialization supplies null for fields that did not exist in older state files.
        // Normalising here preserves the intended, enabled-by-default autoplay behaviour.
        val migratedSettings = loadedSettings.copy(
            playback = loadedSettings.playback.copy(autoPlayOnLaunch = loadedSettings.playback.autoPlayOnLaunch ?: true),
            display = loadedSettings.display.copy(channelListMode = loadedSettings.display.channelListMode ?: ChannelListDisplayMode.NORMAL)
        )
        val migratedSources = epgSources ?: epgUrl.takeIf { it.isNotBlank() }?.let {
            listOf(EpgSource(id = "legacy-epg", name = "EPG", url = it, lastUpdatedAt = null))
        }.orEmpty()
        val migratedCache = epgProgrammesBySource ?: migratedSources.firstOrNull()?.let { mapOf(it.id to programmes) }.orEmpty()
        return copy(settings = migratedSettings, epgSources = migratedSources, epgProgrammesBySource = migratedCache)
    }
}
