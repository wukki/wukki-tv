package hu.wukki.tv

interface ChannelRepository {
    val channels: List<Channel>
    val playlist: PlaylistDefinition
    val lastChannelId: String?

    fun replace(channels: List<Channel>)

    fun markRefreshed(at: Long)

    fun toggleFavorite(id: String)

    fun markPlaybackSuccessful(id: String)
}

interface EpgRepository {
    val sources: List<EpgSource>

    fun synchronize(url: String?): EpgSource?

    fun replace(
        source: EpgSource,
        programmes: List<Programme>,
        at: Long,
    ): Boolean

    fun hasProgrammes(source: EpgSource): Boolean

    fun programmes(channel: Channel): List<Programme>

    fun currentProgramme(
        channel: Channel,
        now: Long,
    ): Programme? = programmes(channel).firstOrNull { now in it.start until it.end }

    fun nextProgramme(
        channel: Channel,
        current: Programme,
    ): Programme? = programmes(channel).firstOrNull { it.start >= current.end }

    fun programmesFor(
        channel: Channel,
        from: Long,
        to: Long,
    ): List<Programme> = programmes(channel).filter { it.end > from && it.start < to }

    fun latestEnd(channels: List<Channel>): Long?
}

interface SettingsRepository {
    val settings: AppSettings

    fun update(transform: (AppSettings) -> AppSettings)
}

class StoreChannelRepository(
    private val store: ApplicationStore,
) : ChannelRepository {
    override val channels: List<Channel> get() = store.current.channels
    override val playlist: PlaylistDefinition get() = store.current.playlists.single()
    override val lastChannelId: String? get() = store.current.lastChannelId

    override fun replace(channels: List<Channel>) =
        store.update { state ->
            val merged =
                channels.map { fresh ->
                    fresh.copy(favorite = state.channels.firstOrNull { OfficialWukkiSource.sameChannel(it, fresh) }?.favorite == true)
                }
            state.copy(channels = merged, lastChannelId = matchingChannelId(state.lastChannelId, state.channels, merged))
        }

    override fun markRefreshed(at: Long) = store.update { it.copy(playlists = listOf(it.playlists.single().copy(updatedAt = at))) }

    override fun toggleFavorite(id: String) =
        store.update { state ->
            state.copy(channels = state.channels.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
        }

    override fun markPlaybackSuccessful(id: String) {
        if (channels.any { it.id == id }) store.update { it.copy(lastChannelId = id) }
    }
}

class StoreSettingsRepository(
    private val store: ApplicationStore,
) : SettingsRepository {
    override val settings: AppSettings get() = store.current.settings

    override fun update(transform: (AppSettings) -> AppSettings) =
        store.update { state ->
            val updated = transform(state.settings)
            state.copy(settings = updated, autoRefreshHours = updated.playlistRefresh.hours)
        }
}

internal fun matchingChannelId(
    previousId: String?,
    previous: List<Channel>,
    refreshed: List<Channel>,
): String? {
    val channel = previous.firstOrNull { it.id == previousId } ?: return null
    return refreshed.firstOrNull { OfficialWukkiSource.sameChannel(channel, it) }?.id
}
