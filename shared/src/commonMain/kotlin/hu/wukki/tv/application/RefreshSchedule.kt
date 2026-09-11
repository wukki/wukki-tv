package hu.wukki.tv

internal fun EpgSource.isEpgRefreshDue(
    interval: RefreshInterval,
    now: Long,
): Boolean = enabled && interval.hours > 0 && (lastUpdatedAt == null || now - lastUpdatedAt >= interval.hours * 60L * 60L * 1000L)

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
