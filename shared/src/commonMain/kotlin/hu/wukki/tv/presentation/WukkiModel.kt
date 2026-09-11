package hu.wukki.tv

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI state and playback intent adapter. Business mutations live in WukkiApplication. */
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
    var state by mutableStateOf(application.store.current)
        private set
    var selectedChannelId by mutableStateOf(
        state.lastChannelId?.takeIf { savedId -> state.channels.any { it.id == savedId } }
            ?: state.channels.firstOrNull()?.id,
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
            val sortedChannels = state.channels.sortedChannels()
            val normalizedQuery = normalize(query)
            ChannelDirectoryDerivedState(
                sortedChannels = sortedChannels,
                categories =
                    state.channels
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

    /** Increments for every displayed feedback so an earlier timeout cannot dismiss a newer one. */
    var feedbackToken by mutableIntStateOf(0)
        private set

    /** Increments only for an explicit request to start the selected channel. */
    var playbackRequestToken by mutableIntStateOf(0)
        private set

    init {
        application.store.observe { next ->
            val previous = state
            val selected = selectedChannelId
            state = next
            if (next.channels !== previous.channels) {
                selectedChannelId = matchingChannelId(selected, previous.channels, next.channels)
                    ?: next.lastChannelId
                    ?: next.channels.firstOrNull()?.id
            }
        }
    }

    val settings: AppSettings get() = state.settings
    val epgSources: List<EpgSource> get() = state.epgSources
    val officialPlaylist: PlaylistDefinition get() = state.playlists.single()
    val officialEpgSource: EpgSource? get() = epgSources.singleOrNull()
    val hasChannels: Boolean get() = state.channels.isNotEmpty()

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

    suspend fun refreshOfficialPlaylist(showFeedback: Boolean = true): Boolean = application.refreshPlaylist(::showRefreshEvent, showFeedback)

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

    fun selectedChannel(): Channel? = state.channels.firstOrNull { it.id == selectedChannelId }

    fun channelById(id: String?): Channel? = id?.let { channelId -> state.channels.firstOrNull { it.id == channelId } }

    fun categories(): List<String> = channelDirectoryState.value.categories

    fun filteredChannels(): List<Channel> = channelDirectoryState.value.filteredChannels

    /** Returns every fixed Wukki channel, independently of the directory filters. */
    fun guideChannels(): List<Channel> = channelDirectoryState.value.sortedChannels

    /** The continuous guide only spans programmes that can actually be shown for this playlist. */
    fun guideLatestProgrammeEnd(): Long? = application.epg.latestEnd(guideChannels())

    fun currentProgram(
        channel: Channel,
        now: Long = clock.nowMillis(),
    ): Programme? = state.let { application.epg.currentProgramme(channel, now) }

    fun nextProgram(
        channel: Channel,
        current: Programme,
    ): Programme? = state.let { application.epg.nextProgramme(channel, current) }

    /** Returns this channel's programmes that overlap the requested time range. */
    fun programmesFor(
        channel: Channel,
        from: Long,
        to: Long,
    ): List<Programme> = state.let { application.epg.programmesFor(channel, from, to) }

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

    private fun showRefreshEvent(event: RefreshEvent) {
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
    val categories: List<String>,
    val filteredChannels: List<Channel>,
)

private fun List<Channel>.sortedChannels(): List<Channel> = sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })
