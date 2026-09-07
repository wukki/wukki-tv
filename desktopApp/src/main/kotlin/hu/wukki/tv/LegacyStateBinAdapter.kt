package hu.wukki.tv

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.nio.file.Path

/** Reads the last Java-serialized desktop schema without coupling domain models to java.io. */
internal object LegacyStateBinAdapter {
    fun load(path: Path): AppState? = runCatching {
        LegacyObjectInputStream(path).use { stream ->
            (stream.readObject() as? LegacyAppState)?.toDomain()
        }
    }.getOrNull()
}

private class LegacyObjectInputStream(path: Path) :
    ObjectInputStream(BufferedInputStream(FileInputStream(path.toFile()))) {
    override fun readClassDescriptor(): ObjectStreamClass {
        val incoming = super.readClassDescriptor()
        val replacement = legacyClasses[incoming.name] ?: return incoming
        return checkNotNull(ObjectStreamClass.lookup(replacement))
    }

    private companion object {
        val legacyClasses = mapOf(
            "hu.wukki.tv.AppState" to LegacyAppState::class.java,
            "hu.wukki.tv.AppSettings" to LegacyAppSettings::class.java,
            "hu.wukki.tv.PlaybackSettings" to LegacyPlaybackSettings::class.java,
            "hu.wukki.tv.DisplaySettings" to LegacyDisplaySettings::class.java,
            "hu.wukki.tv.EpgSource" to LegacyEpgSource::class.java,
            "hu.wukki.tv.PlaylistDefinition" to LegacyPlaylistDefinition::class.java,
            "hu.wukki.tv.Channel" to LegacyChannel::class.java,
            "hu.wukki.tv.Programme" to LegacyProgramme::class.java
        )
    }
}

private class LegacyPlaybackSettings(
    val volume: Int,
    val bufferProfile: BufferProfile?,
    val autoPlayOnLaunch: Boolean?,
    val autoReconnect: Boolean,
    val reconnectAttempts: Int,
    val aspectRatio: AspectRatioMode?
) : Serializable {
    fun toDomain() = PlaybackSettings(
        volume = volume,
        bufferProfile = bufferProfile ?: BufferProfile.BALANCED,
        autoPlayOnLaunch = autoPlayOnLaunch ?: true,
        autoReconnect = autoReconnect,
        reconnectAttempts = reconnectAttempts,
        aspectRatio = aspectRatio ?: AspectRatioMode.AUTO
    )

}

private class LegacyDisplaySettings(
    val uiScale: Float,
    val channelListMode: ChannelListDisplayMode?,
    val showChannelProgramme: Boolean,
    val showMiniGuide: Boolean,
    val showLogos: Boolean,
    val showProgrammeImages: Boolean?
) : Serializable {
    fun toDomain() = DisplaySettings(
        uiScale = uiScale.takeIf { it > 0f } ?: 1f,
        channelListMode = channelListMode ?: ChannelListDisplayMode.NORMAL,
        showChannelProgramme = showChannelProgramme,
        showMiniGuide = showMiniGuide,
        showLogos = showLogos,
        showProgrammeImages = showProgrammeImages ?: true
    )

}

private class LegacyAppSettings(
    val language: AppLanguage?,
    val playlistRefresh: RefreshInterval?,
    val epgRefresh: RefreshInterval?,
    val playback: LegacyPlaybackSettings?,
    val display: LegacyDisplaySettings?
) : Serializable {
    fun toDomain() = AppSettings(
        language = language ?: AppLanguage.HUNGARIAN,
        playlistRefresh = playlistRefresh ?: RefreshInterval.MANUAL,
        epgRefresh = epgRefresh ?: RefreshInterval.MANUAL,
        playback = playback?.toDomain() ?: PlaybackSettings(),
        display = display?.toDomain() ?: DisplaySettings()
    )
}

private class LegacyEpgSource(
    val id: String?,
    val name: String?,
    val url: String?,
    val enabled: Boolean,
    val priority: Int,
    val lastUpdatedAt: Long?,
    val managedByPlaylist: Boolean
) : Serializable {
    fun toDomain(): EpgSource? = EpgSource(
        id = id ?: return null,
        name = name.orEmpty(),
        url = url ?: return null,
        enabled = enabled,
        priority = priority,
        lastUpdatedAt = lastUpdatedAt,
        managedByPlaylist = managedByPlaylist
    )
}

private class LegacyPlaylistDefinition(
    val id: String?,
    val name: String?,
    val location: String?,
    val source: PlaylistSource?,
    val updatedAt: Long
) : Serializable {
    fun toDomain(): PlaylistDefinition? = PlaylistDefinition(
        id = id ?: return null,
        name = name.orEmpty(),
        location = location ?: return null,
        source = source ?: PlaylistSource.URL,
        updatedAt = updatedAt
    )
}

private class LegacyChannel(
    val id: String?,
    val playlistId: String?,
    val name: String?,
    val streamUrl: String?,
    val tvgId: String?,
    val tvgName: String?,
    val tvgChno: Int?,
    val group: String?,
    val logo: String?,
    val favorite: Boolean,
    val epgChannelId: String?,
    val epgSourceId: String?,
    val tvgShiftHours: Double?
) : Serializable {
    fun toDomain(): Channel? = Channel(
        id = id ?: return null,
        playlistId = playlistId ?: return null,
        name = name.orEmpty(),
        streamUrl = streamUrl ?: return null,
        tvgId = tvgId,
        tvgName = tvgName,
        tvgChno = tvgChno,
        group = group.orEmpty(),
        logo = logo,
        favorite = favorite,
        epgChannelId = epgChannelId,
        epgSourceId = epgSourceId,
        tvgShiftHours = tvgShiftHours
    )
}

private class LegacyProgramme(
    val channelId: String?,
    val title: String?,
    val start: Long,
    val end: Long,
    val description: String?,
    val imageUrl: String?
) : Serializable {
    fun toDomain(): Programme? = Programme(
        channelId = channelId ?: return null,
        title = title.orEmpty(),
        start = start,
        end = end,
        description = description,
        imageUrl = imageUrl
    )
}

private class LegacyAppState(
    val playlists: List<LegacyPlaylistDefinition>?,
    val channels: List<LegacyChannel>?,
    val programmes: List<LegacyProgramme>?,
    val epgUrl: String?,
    val autoRefreshHours: Int,
    val lastChannelId: String?,
    val settings: LegacyAppSettings?,
    val epgSources: List<LegacyEpgSource>?,
    val epgProgrammesBySource: Map<String, List<LegacyProgramme>>?
) : Serializable {
    fun toDomain(): AppState = AppState(
        playlists = playlists.orEmpty().mapNotNull(LegacyPlaylistDefinition::toDomain),
        channels = channels.orEmpty().mapNotNull(LegacyChannel::toDomain),
        programmes = programmes.orEmpty().mapNotNull(LegacyProgramme::toDomain),
        epgUrl = epgUrl.orEmpty(),
        autoRefreshHours = autoRefreshHours,
        lastChannelId = lastChannelId,
        settings = settings?.toDomain() ?: AppSettings(),
        epgSources = epgSources.orEmpty().mapNotNull(LegacyEpgSource::toDomain),
        epgProgrammesBySource = epgProgrammesBySource.orEmpty().mapValues { (_, programmes) ->
            programmes.mapNotNull(LegacyProgramme::toDomain)
        }
    ).normalized()
}
