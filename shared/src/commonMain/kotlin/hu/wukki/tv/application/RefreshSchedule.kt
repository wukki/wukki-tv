package hu.wukki.tv

internal fun EpgSource.isEpgRefreshDue(
    interval: RefreshInterval,
    now: Long,
): Boolean {
    val updatedAt = lastUpdatedAt
    return enabled && interval.hours > 0 && (updatedAt == null || now - updatedAt >= interval.hours * 60L * 60L * 1000L)
}

internal fun nextEpgRefreshDelayMillis(
    sources: List<EpgSource>,
    interval: RefreshInterval,
    now: Long,
): Long {
    val intervalMillis = interval.hours * 60L * 60L * 1000L
    if (intervalMillis <= 0L) return Long.MAX_VALUE
    val nextDueAt =
        sources
            .asSequence()
            .filter { it.enabled }
            .map { source ->
                (source.lastUpdatedAt ?: now) + intervalMillis
            }.minOrNull() ?: (now + intervalMillis)
    return (nextDueAt - now).coerceAtLeast(0L)
}

/** Delay until the first enabled source is due; zero means a background refresh is due now. */
fun epgRefreshDelayMillis(
    sources: List<EpgSource>,
    interval: RefreshInterval,
    now: Long,
): Long {
    if (interval == RefreshInterval.MANUAL) return Long.MAX_VALUE
    val enabledSources = sources.filter { it.enabled }
    if (enabledSources.isEmpty()) return Long.MAX_VALUE
    if (enabledSources.any { it.isEpgRefreshDue(interval, now) }) return 0L
    return nextEpgRefreshDelayMillis(enabledSources, interval, now)
}

/** Zero denotes a due refresh; MANUAL never schedules network work. */
fun playlistRefreshDelayMillis(
    updatedAt: Long,
    interval: RefreshInterval,
    now: Long,
): Long {
    if (interval == RefreshInterval.MANUAL) return Long.MAX_VALUE
    if (updatedAt <= 0L) return 0L
    return (updatedAt + interval.hours * 60L * 60L * 1000L - now).coerceAtLeast(0L)
}
