package hu.wukki.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue

/** UI state and playback intent adapter. Business mutations live in WukkiApplication. */
@Stable
class WukkiModel(
    private val application: WukkiApplication,
) {
    constructor(
        initialState: AppState,
        sourceLoader: RemoteTextLoader,
        xmlTvParser: XmlTvParser,
        stateSaver: (AppState) -> Unit,
        refreshService: RefreshService? = null,
        clock: Clock = SystemClock,
        dispatchers: DispatcherProvider = DispatcherProvider(),
    ) : this(WukkiApplication(ApplicationStore(initialState, stateSaver, clock), sourceLoader, xmlTvParser, refreshService, clock, dispatchers))

    private val clock: Clock get() = application.clock
    private var channels by mutableStateOf(application.store.current.channels, referentialEqualityPolicy())
    private var playlists by mutableStateOf(application.store.current.playlists, referentialEqualityPolicy())
    var settings by mutableStateOf(application.store.current.settings)
        private set
    var epgSources by mutableStateOf(application.store.current.epgSources, referentialEqualityPolicy())
        private set
    private var epgProgrammes = application.store.current.epgProgrammesBySource

    /** Compatibility snapshot; feature UI reads the narrower observable slices below. */
    var state by mutableStateOf(application.store.current)
        private set

    /** Changes only when the immutable EPG programme snapshot is replaced. */
    var epgContentVersion by mutableIntStateOf(0)
        private set

    var selectedChannelId by mutableStateOf(
        state.lastChannelId?.takeIf { savedId -> channels.any { it.id == savedId } }
            ?: channels.firstOrNull()?.id,
    )
        private set
    var query by mutableStateOf("")
        private set
    var category by mutableStateOf<String?>(null)
        private set
    var onlyFavorites by mutableStateOf(false)
        private set
    private val channelDirectoryState =
        derivedStateOf {
            val sortedChannels = channels.sortedChannels()
            val normalizedQuery = normalize(query)
            ChannelDirectoryDerivedState(
                sortedChannels = sortedChannels,
                channelsById = sortedChannels.associateBy(Channel::id),
                categories =
                    channels
                        .asSequence()
                        .map(::channelCategoryName)
                        .distinct()
                        .sorted()
                        .toList(),
                filteredChannels =
                    sortedChannels.filter { channel ->
                        (!onlyFavorites || channel.favorite) &&
                            (category == null || channelCategoryName(channel) == category) &&
                            (query.isBlank() || normalize(channel.name).contains(normalizedQuery))
                    },
            )
        }
    var status by mutableStateOf<UserMessage?>(null)
        private set
    var error by mutableStateOf<UserMessage?>(null)
        private set
    var feedbackKind by mutableStateOf<AppFeedbackKind?>(null)
        private set
    var playlistLoadFailed by mutableStateOf(false)
        private set
    var playlistRefreshInProgress by mutableStateOf(false)
        private set

    /** Increments for every displayed feedback so an earlier timeout cannot dismiss a newer one. */
    var feedbackToken by mutableIntStateOf(0)
        private set

    /** Increments only for an explicit request to start the selected channel. */
    var playbackRequestToken by mutableIntStateOf(0)
        private set

    init {
        application.store.observe { next ->
            val previousChannels = channels
            state = next
            if (next.channels !== previousChannels) {
                channels = next.channels
                selectedChannelId = matchingChannelId(selectedChannelId, previousChannels, next.channels)
                    ?: next.lastChannelId
                    ?: next.channels.firstOrNull()?.id
            }
            if (next.playlists !== playlists) playlists = next.playlists
            if (next.settings != settings) settings = next.settings
            if (next.epgSources !== epgSources) epgSources = next.epgSources
            if (next.epgProgrammesBySource !== epgProgrammes) {
                epgProgrammes = next.epgProgrammesBySource
                epgContentVersion++
            }
        }
    }

    val officialPlaylist: PlaylistDefinition get() = playlists.single()
    val officialEpgSource: EpgSource? get() = epgSources.singleOrNull()
    val hasChannels: Boolean get() = channels.isNotEmpty()
    val channelCount: Int get() = channels.size

    /** For diagnostics that do not have a translation key yet. */
    fun showRawError(message: String) = showError(UserMessage.Raw(message))

    fun dismissFeedback(token: Int) {
        if (token != feedbackToken) return
        status = null
        error = null
        feedbackKind = null
    }

    fun setLanguage(language: AppLanguage) = updateSettings { it.copy(language = language) }

    fun setPlaylistRefresh(interval: RefreshInterval) = updateSettings { it.copy(playlistRefresh = interval) }

    fun setEpgRefresh(interval: RefreshInterval) = updateSettings { it.copy(epgRefresh = interval) }

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) = application.updateSettings.playback(transform)

    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) = application.updateSettings.display(transform)

    fun setChannelQuery(value: String) {
        query = value
    }

    fun showAllChannels() {
        category = null
        onlyFavorites = false
    }

    fun showFavoriteChannels() {
        category = null
        onlyFavorites = true
    }

    fun showChannelCategory(value: String) {
        category = value
        onlyFavorites = false
    }

    suspend fun refreshOfficialPlaylist(showFeedback: Boolean = true): Boolean {
        if (playlistRefreshInProgress) return false
        playlistRefreshInProgress = true
        return try {
            application.refreshPlaylist(::showRefreshEvent, showFeedback).also { succeeded ->
                if (succeeded) playlistLoadFailed = false
            }
        } finally {
            playlistRefreshInProgress = false
        }
    }

    fun nextPlaylistRefreshDelayMillis(now: Long = clock.nowMillis()): Long = application.playlistRefreshDelay(now)

    suspend fun refreshDuePlaylist(now: Long = clock.nowMillis()): Boolean = application.refreshDuePlaylist(::showRefreshEvent, now)

    suspend fun refreshOfficialEpg(showFeedback: Boolean = true): Boolean = application.refreshEpg(officialEpgSource?.id, ::showRefreshEvent, showFeedback)

    suspend fun refreshEpgSource(
        sourceId: String,
        showFeedback: Boolean = true,
    ): Boolean = application.refreshEpg(sourceId, ::showRefreshEvent, showFeedback)

    suspend fun refreshDueEpgSources(
        interval: RefreshInterval,
        now: Long = clock.nowMillis(),
    ): Boolean = application.refreshDueEpg(interval, ::showRefreshEvent, now)

    fun nextEpgRefreshDelayMillis(
        interval: RefreshInterval,
        now: Long = clock.nowMillis(),
    ): Long = nextEpgRefreshDelayMillis(epgSources, interval, now)

    fun toggleFavorite(id: String) = application.toggleFavorite(id)

    fun selectChannel(id: String) {
        if (application.selectChannel(id) == null) return
        selectedChannelId = id
        requestPlayback()
    }

    fun requestPlayback() {
        if (selectedChannel() != null) playbackRequestToken++
    }

    /** Only a successful playback event updates the remembered channel. */
    fun markChannelPlaybackSuccessful(id: String) = application.channels.markPlaybackSuccessful(id)

    fun selectedChannel(): Channel? = selectedChannelId?.let(channelDirectoryState.value.channelsById::get)

    fun channelById(id: String?): Channel? = id?.let(channelDirectoryState.value.channelsById::get)

    fun categories(): List<String> = channelDirectoryState.value.categories

    fun filteredChannels(): List<Channel> = channelDirectoryState.value.filteredChannels

    /** Returns every fixed Wukki channel, independently of the directory filters. */
    fun guideChannels(): List<Channel> = channelDirectoryState.value.sortedChannels

    /** The continuous guide only spans programmes that can actually be shown for this playlist. */
    fun guideLatestProgrammeEnd(): Long? {
        epgContentVersion
        return application.epg.latestEnd(guideChannels())
    }

    fun currentProgram(
        channel: Channel,
        now: Long = clock.nowMillis(),
    ): Programme? {
        epgContentVersion
        return application.epg.currentProgramme(channel, now)
    }

    fun nextProgram(
        channel: Channel,
        current: Programme,
    ): Programme? {
        epgContentVersion
        return application.epg.nextProgramme(channel, current)
    }

    /** Returns this channel's programmes that overlap the requested time range. */
    fun programmesFor(
        channel: Channel,
        from: Long,
        to: Long,
    ): List<Programme> {
        epgContentVersion
        return application.epg.programmesFor(channel, from, to)
    }

    fun moveChannel(delta: Int) {
        val channels = filteredChannels()
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == selectedChannelId }.let { if (it < 0) 0 else it }
        selectChannel(channels[(index + delta).mod(channels.size)].id)
    }

    fun selectChannelByNumber(number: String): Boolean {
        val requestedNumber = number.toIntOrNull()?.takeIf { it > 0 } ?: return false
        val channels = guideChannels()
        val channel =
            channels.firstOrNull { it.tvgChno == requestedNumber }
                ?: channels.getOrNull(requestedNumber - 1)
                ?: return false
        selectChannel(channel.id)
        return true
    }

    private fun channelCategoryName(channel: Channel): String = channel.group.ifBlank { OTHER_CATEGORY_ID }

    private fun showError(message: UserMessage) = showFeedback(AppFeedbackKind.ERROR, message)

    private fun showFeedback(
        kind: AppFeedbackKind,
        message: UserMessage,
    ) {
        when (kind) {
            AppFeedbackKind.ERROR -> {
                error = message
                status = null
            }

            AppFeedbackKind.LOADING, AppFeedbackKind.SUCCESS -> {
                status = message
                error = null
            }
        }
        feedbackKind = kind
        feedbackToken++
    }

    internal fun showRefreshEvent(event: RefreshEvent) {
        when (event) {
            RefreshEvent.PlaylistLoading -> {
                playlistRefreshInProgress = true
            }

            is RefreshEvent.PlaylistLoaded -> {
                playlistRefreshInProgress = false
                playlistLoadFailed = false
            }

            is RefreshEvent.Failed -> {
                if (event.playlistUnavailable || playlistRefreshInProgress && event.sourceName == null) {
                    playlistRefreshInProgress = false
                    playlistLoadFailed = event.playlistUnavailable
                }
            }

            is RefreshEvent.EpgLoading, is RefreshEvent.EpgLoaded -> {
                // EPG refreshes do not change channel-list availability.
            }
        }
        val (kind, message) = event.feedback()
        showFeedback(kind, message)
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = application.updateSettings(transform)
}

sealed interface UserMessage {
    data class Key(
        val key: String,
        val arguments: List<Any?> = emptyList(),
    ) : UserMessage

    data class Raw(
        val value: String,
    ) : UserMessage
}

enum class AppFeedbackKind { LOADING, SUCCESS, ERROR }

private data class ChannelDirectoryDerivedState(
    val sortedChannels: List<Channel>,
    val channelsById: Map<String, Channel>,
    val categories: List<String>,
    val filteredChannels: List<Channel>,
)

private fun List<Channel>.sortedChannels(): List<Channel> = sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })
