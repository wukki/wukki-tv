package hu.wukki.tv.webos

import hu.wukki.tv.Channel

private const val VIRTUAL_CHANNEL_BUFFER_ROWS = 3

internal enum class WebOsChannelFilter {
    ALL,
    FAVORITES,
    RECENT,
    CATEGORY,
}

internal enum class WebOsChannelEmptyState {
    NO_DATA,
    LOAD_FAILED,
    NO_SEARCH_RESULTS,
    NO_FAVORITES,
    NO_RECENT,
    NO_CATEGORY_RESULTS,
}

internal enum class WebOsChannelEmptyAction {
    REFRESH,
    CLEAR_SEARCH,
    SHOW_ALL,
}

internal data class ChannelRenderWindow(
    val start: Int,
    val endExclusive: Int,
)

internal fun filterAndSortChannels(
    channels: List<Channel>,
    query: String,
    filter: WebOsChannelFilter = WebOsChannelFilter.ALL,
    category: String? = null,
    recentChannelIds: List<String> = emptyList(),
): List<Channel> {
    val normalizedQuery = normalizeChannelText(query.trim())
    val filtered =
        channels.filter { channel ->
            val matchesFilter =
                when (filter) {
                    WebOsChannelFilter.ALL -> true
                    WebOsChannelFilter.FAVORITES -> channel.favorite
                    WebOsChannelFilter.RECENT -> channel.id in recentChannelIds.distinct().take(10)
                    WebOsChannelFilter.CATEGORY -> channel.group == category
                }
            matchesFilter && (normalizedQuery.isEmpty() || normalizeChannelText(channel.name).contains(normalizedQuery))
        }
    if (filter == WebOsChannelFilter.RECENT) {
        val byId = filtered.associateBy(Channel::id)
        return recentChannelIds.distinct().take(10).mapNotNull(byId::get)
    }
    return filtered.sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalizeChannelText(it.name) })
}

internal fun sortedChannelCategories(channels: List<Channel>): List<String> = channels.map(Channel::group).distinct().sortedBy(::normalizeChannelText)

internal fun channelEmptyState(
    hasChannels: Boolean,
    visibleCount: Int,
    query: String,
    filter: WebOsChannelFilter,
    loadFailed: Boolean,
): WebOsChannelEmptyState? =
    when {
        visibleCount > 0 -> null
        query.isNotBlank() -> WebOsChannelEmptyState.NO_SEARCH_RESULTS
        filter == WebOsChannelFilter.RECENT -> WebOsChannelEmptyState.NO_RECENT
        filter == WebOsChannelFilter.FAVORITES -> WebOsChannelEmptyState.NO_FAVORITES
        filter == WebOsChannelFilter.CATEGORY -> WebOsChannelEmptyState.NO_CATEGORY_RESULTS
        loadFailed -> WebOsChannelEmptyState.LOAD_FAILED
        !hasChannels -> WebOsChannelEmptyState.NO_DATA
        else -> null
    }

internal fun WebOsChannelEmptyState.action(): WebOsChannelEmptyAction =
    when (this) {
        WebOsChannelEmptyState.NO_DATA,
        WebOsChannelEmptyState.LOAD_FAILED,
        -> WebOsChannelEmptyAction.REFRESH

        WebOsChannelEmptyState.NO_SEARCH_RESULTS -> WebOsChannelEmptyAction.CLEAR_SEARCH

        WebOsChannelEmptyState.NO_FAVORITES,
        WebOsChannelEmptyState.NO_RECENT,
        WebOsChannelEmptyState.NO_CATEGORY_RESULTS,
        -> WebOsChannelEmptyAction.SHOW_ALL
    }

internal fun channelRowHeight(displayMode: String): Int =
    when (displayMode) {
        "COMPACT" -> 64
        "DETAILED" -> 120
        else -> 88
    }

internal fun calculateChannelRenderWindow(
    itemCount: Int,
    scrollTop: Double,
    viewportHeight: Int,
    rowHeight: Int = channelRowHeight("NORMAL"),
): ChannelRenderWindow {
    if (itemCount <= 0) return ChannelRenderWindow(0, 0)
    val firstVisible = (scrollTop.coerceAtLeast(0.0) / rowHeight).toInt().coerceAtMost(itemCount - 1)
    val visibleRows = ((viewportHeight.coerceAtLeast(rowHeight) + rowHeight - 1) / rowHeight)
    val start = (firstVisible - VIRTUAL_CHANNEL_BUFFER_ROWS).coerceAtLeast(0)
    val end = (firstVisible + visibleRows + VIRTUAL_CHANNEL_BUFFER_ROWS).coerceAtMost(itemCount)
    return ChannelRenderWindow(start, end)
}

internal fun mergeFavoriteState(
    refreshed: List<Channel>,
    cached: List<Channel>,
): List<Channel> {
    val favoriteKeys = cached.filter(Channel::favorite).flatMap(::stableChannelKeys).toSet()
    return refreshed.map { channel ->
        channel.copy(favorite = stableChannelKeys(channel).any(favoriteKeys::contains))
    }
}

private fun stableChannelKeys(channel: Channel): List<String> =
    listOfNotNull(
        "id:${channel.id}",
        channel.tvgId?.takeIf(String::isNotBlank)?.let { "tvg:$it" },
        channel.streamUrl.takeIf(String::isNotBlank)?.let { "url:$it" },
    )

internal fun normalizeChannelText(value: String): String =
    buildString(value.length) {
        value.lowercase().forEach { character ->
            append(
                when (character) {
                    'á', 'à', 'â', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'ö', 'ő' -> 'o'
                    'ú', 'ù', 'û', 'ü', 'ű' -> 'u'
                    else -> character
                },
            )
        }
    }
