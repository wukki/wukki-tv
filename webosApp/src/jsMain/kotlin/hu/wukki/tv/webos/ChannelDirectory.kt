package hu.wukki.tv.webos

import hu.wukki.tv.Channel

internal const val VIRTUAL_CHANNEL_ROW_HEIGHT = 86
private const val VIRTUAL_CHANNEL_BUFFER_ROWS = 3

internal data class ChannelRenderWindow(
    val start: Int,
    val endExclusive: Int,
)

internal fun filterAndSortChannels(
    channels: List<Channel>,
    query: String,
    category: String?,
): List<Channel> {
    val normalizedQuery = normalizeChannelText(query.trim())
    return channels
        .asSequence()
        .filter { channel -> category == null || channel.group == category }
        .filter { channel -> normalizedQuery.isEmpty() || normalizeChannelText(channel.name).contains(normalizedQuery) }
        .sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalizeChannelText(it.name) })
        .toList()
}

internal fun sortedChannelCategories(channels: List<Channel>): List<String> = channels.map { it.group }.distinct().sortedBy(::normalizeChannelText)

internal fun calculateChannelRenderWindow(
    itemCount: Int,
    scrollTop: Double,
    viewportHeight: Int,
): ChannelRenderWindow {
    if (itemCount <= 0) return ChannelRenderWindow(0, 0)
    val firstVisible = (scrollTop.coerceAtLeast(0.0) / VIRTUAL_CHANNEL_ROW_HEIGHT).toInt().coerceAtMost(itemCount - 1)
    val visibleRows = ((viewportHeight.coerceAtLeast(VIRTUAL_CHANNEL_ROW_HEIGHT) + VIRTUAL_CHANNEL_ROW_HEIGHT - 1) / VIRTUAL_CHANNEL_ROW_HEIGHT)
    val start = (firstVisible - VIRTUAL_CHANNEL_BUFFER_ROWS).coerceAtLeast(0)
    val end = (firstVisible + visibleRows + VIRTUAL_CHANNEL_BUFFER_ROWS).coerceAtMost(itemCount)
    return ChannelRenderWindow(start, end)
}

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
