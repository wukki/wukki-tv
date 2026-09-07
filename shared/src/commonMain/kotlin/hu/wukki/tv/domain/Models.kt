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
    val autoPlayOnLaunch: Boolean = true,
    val autoReconnect: Boolean = true,
    val reconnectAttempts: Int = 3,
    val aspectRatio: AspectRatioMode = AspectRatioMode.AUTO
)

@KotlinSerializable
data class DisplaySettings(
    val uiScale: Float = 1f,
    val channelListMode: ChannelListDisplayMode = ChannelListDisplayMode.NORMAL,
    val showChannelProgramme: Boolean = true,
    val showMiniGuide: Boolean = true,
    val showLogos: Boolean = true,
    val showProgrammeImages: Boolean = true
)

@KotlinSerializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.HUNGARIAN,
    val playlistRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val epgRefresh: RefreshInterval = RefreshInterval.MANUAL,
    val playback: PlaybackSettings = PlaybackSettings(),
    val display: DisplaySettings = DisplaySettings()
)

@KotlinSerializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val lastUpdatedAt: Long? = null,
    val managedByPlaylist: Boolean = false
)

@KotlinSerializable
data class PlaylistDefinition(
    val id: String,
    val name: String,
    val location: String,
    val source: PlaylistSource,
    val updatedAt: Long
)

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
)

@KotlinSerializable
data class Programme(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null,
    /** Optional artwork URL supplied by XMLTV's programme icon metadata. */
    val imageUrl: String? = null
)

@KotlinSerializable
data class AppState(
    val playlists: List<PlaylistDefinition> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val epgUrl: String = "",
    val autoRefreshHours: Int = 0,
    val lastChannelId: String? = null,
    val settings: AppSettings = AppSettings(),
    val epgSources: List<EpgSource> = emptyList(),
    val epgProgrammesBySource: Map<String, List<Programme>> = emptyMap()
) {
    fun normalized(): AppState {
        val legacyPlaylistRefresh = RefreshInterval.entries.firstOrNull { it.hours == autoRefreshHours }
            ?: RefreshInterval.MANUAL
        val migratedSettings = if (autoRefreshHours > 0 && settings.playlistRefresh == RefreshInterval.MANUAL) {
            settings.copy(playlistRefresh = legacyPlaylistRefresh)
        } else settings
        val migratedSources = epgSources.ifEmpty { epgUrl.takeIf { it.isNotBlank() }?.let {
            listOf(EpgSource(id = "legacy-epg", name = "EPG", url = it, lastUpdatedAt = null))
        }.orEmpty() }
        val migratedCache = epgProgrammesBySource.ifEmpty {
            migratedSources.firstOrNull()?.let { mapOf(it.id to programmes) }.orEmpty()
        }
        val migratedChannels = channels.map { channel ->
            channel.copy(
                name = channel.name.takeUnless { it.trim().equals(LEGACY_UNKNOWN_CHANNEL_NAME, ignoreCase = true) }
                    ?: UNKNOWN_CHANNEL_NAME_ID,
                group = channel.group.takeUnless { it.isBlank() || it.trim().equals(LEGACY_OTHER_CATEGORY_NAME, ignoreCase = true) }
                    ?: OTHER_CATEGORY_ID
            )
        }
        return copy(
            settings = migratedSettings,
            autoRefreshHours = 0,
            channels = migratedChannels,
            programmes = emptyList(),
            epgSources = migratedSources,
            epgProgrammesBySource = migratedCache
        )
    }
}

const val OTHER_CATEGORY_ID = "__wukki_other__"
const val UNKNOWN_CHANNEL_NAME_ID = "__wukki_unknown_channel__"
private const val LEGACY_OTHER_CATEGORY_NAME = "Egyéb"
private const val LEGACY_UNKNOWN_CHANNEL_NAME = "Ismeretlen csatorna"
