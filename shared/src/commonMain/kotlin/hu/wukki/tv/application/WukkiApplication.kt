package hu.wukki.tv

/** Compose-free application composition, also usable without a presentation model. */
class WukkiApplication(
    val store: ApplicationStore,
    sourceLoader: RemoteTextLoader,
    parser: XmlTvParser,
    refreshService: RefreshService? = null,
    val clock: Clock = SystemClock,
    dispatchers: DispatcherProvider = DispatcherProvider(),
) {
    val channels: ChannelRepository = StoreChannelRepository(store)
    val epg: EpgRepository = StoreEpgRepository(store)
    val settings: SettingsRepository = StoreSettingsRepository(store)
    val selectChannel = SelectChannel(channels)
    val toggleFavorite = ToggleFavorite(channels)
    val updateSettings = UpdateSettings(settings)
    private val coordinator = RefreshCoordinator(refreshService)
    val refreshEpg = RefreshOfficialEpg(epg, sourceLoader, parser, coordinator, clock, dispatchers)
    val refreshPlaylist = RefreshOfficialPlaylist(channels, epg, refreshEpg, sourceLoader, coordinator, clock, dispatchers)

    fun playlistRefreshDelay(now: Long = clock.nowMillis()): Long = playlistRefreshDelayMillis(channels.playlist.updatedAt, settings.settings.playlistRefresh, now)

    suspend fun refreshDuePlaylist(
        emit: (RefreshEvent) -> Unit,
        now: Long = clock.nowMillis(),
    ): Boolean = if (playlistRefreshDelay(now) == 0L) refreshPlaylist(emit, false) else true

    suspend fun refreshDueEpg(
        interval: RefreshInterval,
        emit: (RefreshEvent) -> Unit,
        now: Long = clock.nowMillis(),
    ): Boolean {
        val source = epg.sources.singleOrNull() ?: return true
        return if (source.isEpgRefreshDue(interval, now)) refreshEpg(source.id, emit, false) else true
    }
}
