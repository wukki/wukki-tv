package hu.wukki.tv.ui.navigation

/** Resolves a saved channel identity after filtering, playlist changes, or an EPG refresh. */
internal fun restoredChannelIndex(
    channelIds: List<String>,
    savedChannelId: String?,
    selectedChannelId: String?,
    fallbackIndex: Int
): Int {
    if (channelIds.isEmpty()) return 0
    return channelIds.indexOf(savedChannelId).takeIf { it >= 0 }
        ?: channelIds.indexOf(selectedChannelId).takeIf { it >= 0 }
        ?: fallbackIndex.coerceIn(0, channelIds.lastIndex)
}

/** Resolves the initial list focus when entering the Channels main section. */
internal fun activeChannelIndex(channelIds: List<String>, activeChannelId: String?): Int {
    if (channelIds.isEmpty()) return 0
    return channelIds.indexOf(activeChannelId).takeIf { it >= 0 } ?: 0
}
