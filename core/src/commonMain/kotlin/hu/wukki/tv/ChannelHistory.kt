package hu.wukki.tv

private const val CHANNEL_HISTORY_LIMIT = 10

/** Newest successful playback first; stable ordering also defines the previous channel. */
fun normalizedChannelHistory(
    ids: List<String>,
    channels: List<Channel>,
): List<String> {
    val available = channels.mapTo(mutableSetOf()) { it.id }
    return ids.filter { it in available }.distinct().take(CHANNEL_HISTORY_LIMIT)
}
