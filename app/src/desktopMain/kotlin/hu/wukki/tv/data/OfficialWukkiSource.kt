package hu.wukki.tv

/**
 * The only playlist distributed by the application.
 *
 * Keeping this policy outside [WukkiModel] makes the one-time local-state migration
 * deterministic and independently testable.
 */
object OfficialWukkiSource {
    const val PLAYLIST_ID = "wukki-official-playlist"
    const val EPG_SOURCE_ID = "wukki-official-epg"
    const val PLAYLIST_NAME = "Wukki TV"
    const val PLAYLIST_URL = "https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u"

    /**
     * Keeps only cache data that originated from the official M3U and rewrites it to
     * stable identifiers. All user-managed playlist and EPG sources are intentionally removed.
     */
    fun provision(loadedState: AppState, now: Long = System.currentTimeMillis()): AppState {
        val state = loadedState.normalized()
        val previousOfficialIds = state.playlists
            .filter { playlist -> playlist.source == PlaylistSource.URL && playlist.location == PLAYLIST_URL }
            .map { it.id }
            .toSet()
        val cachedChannels = state.channels
            .filter { it.playlistId in previousOfficialIds }

        val cachedEpgSource = state.epgSources.orEmpty().firstOrNull { source ->
            source.managedByPlaylist && state.epgProgrammesBySource.orEmpty()[source.id] != null
        }
        val cachedProgrammes = cachedEpgSource
            ?.let { source -> state.epgProgrammesBySource.orEmpty()[source.id].orEmpty() }
            .orEmpty()
        val officialEpg = cachedEpgSource?.let { source ->
            EpgSource(
                id = EPG_SOURCE_ID,
                name = "$PLAYLIST_NAME EPG",
                url = source.url,
                enabled = true,
                priority = 0,
                lastUpdatedAt = source.lastUpdatedAt,
                managedByPlaylist = true
            )
        }
        val migratedChannels = cachedChannels.map { channel ->
            channel.copy(
                playlistId = PLAYLIST_ID,
                epgSourceId = channel.epgSourceId?.takeIf { it == cachedEpgSource?.id }?.let { EPG_SOURCE_ID }
            )
        }
        val previousDefinition = state.playlists
            .filter { playlist -> playlist.id in previousOfficialIds }
            .maxByOrNull { it.updatedAt }
        val lastChannelId = state.lastChannelId?.takeIf { id -> migratedChannels.any { it.id == id } }

        return state.copy(
            playlists = listOf(
                PlaylistDefinition(
                    id = PLAYLIST_ID,
                    name = PLAYLIST_NAME,
                    location = PLAYLIST_URL,
                    source = PlaylistSource.URL,
                    updatedAt = previousDefinition?.updatedAt ?: now
                )
            ),
            channels = migratedChannels,
            programmes = cachedProgrammes,
            epgUrl = officialEpg?.url.orEmpty(),
            lastChannelId = lastChannelId,
            epgSources = officialEpg?.let(::listOf).orEmpty(),
            epgProgrammesBySource = officialEpg?.let { mapOf(EPG_SOURCE_ID to cachedProgrammes) }.orEmpty()
        )
    }

    fun sameChannel(left: Channel, right: Channel): Boolean {
        val leftTvgId = left.tvgId?.trim().orEmpty()
        val rightTvgId = right.tvgId?.trim().orEmpty()
        return if (leftTvgId.isNotEmpty() && rightTvgId.isNotEmpty()) {
            leftTvgId.equals(rightTvgId, ignoreCase = true)
        } else {
            normalize(left.name) == normalize(right.name)
        }
    }
}
