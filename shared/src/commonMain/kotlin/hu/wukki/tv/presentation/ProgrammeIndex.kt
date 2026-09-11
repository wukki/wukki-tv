package hu.wukki.tv

import kotlin.math.roundToLong

/**
 * Immutable lookup index for XMLTV programmes. Building it once avoids scanning and sorting the
 * complete EPG cache for every channel row and every Guide recomposition.
 */
internal class ProgrammeIndex(
    sourceProgrammes: Map<String, List<Programme>>,
) {
    private val programmesByChannel =
        sourceProgrammes.mapValues { (_, programmes) ->
            programmes
                .groupBy { it.channelId.normalizedEpgId() }
                .mapValues { (_, channelProgrammes) -> channelProgrammes.sortedBy(Programme::start) }
        }
    private val latestEndByChannel =
        programmesByChannel.mapValues { (_, channels) ->
            channels.mapValues { (_, programmes) -> programmes.maxOfOrNull(Programme::end) }
        }
    private val shiftedProgrammes = mutableMapOf<ShiftedChannelKey, List<Programme>>()

    fun programmes(channel: Channel): List<Programme> {
        val sourceId = channel.epgSourceId ?: return emptyList()
        val epgChannelId = channel.epgChannelId?.normalizedEpgId() ?: return emptyList()
        val base = programmesByChannel[sourceId]?.get(epgChannelId).orEmpty()
        val offsetMillis = channel.tvgShiftHours.toOffsetMillis()
        if (offsetMillis == 0L || base.isEmpty()) return base
        return shiftedProgrammes.getOrPut(ShiftedChannelKey(sourceId, epgChannelId, offsetMillis)) {
            base.map { programme -> programme.copy(start = programme.start + offsetMillis, end = programme.end + offsetMillis) }
        }
    }

    fun currentProgramme(
        channel: Channel,
        now: Long,
    ): Programme? {
        val programmes = programmes(channel)
        val candidate = programmes.indexOfLastStartAtOrBefore(now)
        return programmes.getOrNull(candidate)?.takeIf { now < it.end }
    }

    fun nextProgramme(
        channel: Channel,
        current: Programme,
    ): Programme? {
        val programmes = programmes(channel)
        return programmes.getOrNull(programmes.lowerBoundByStart(current.end))
    }

    fun programmesFor(
        channel: Channel,
        from: Long,
        to: Long,
    ): List<Programme> {
        if (to <= from) return emptyList()
        val programmes = programmes(channel)
        if (programmes.isEmpty()) return emptyList()
        val firstStartingInRange = programmes.lowerBoundByStart(from)
        val startIndex = (firstStartingInRange - 1).coerceAtLeast(0)
        val endIndex = programmes.lowerBoundByStart(to)
        if (startIndex >= endIndex) return emptyList()
        return programmes.subList(startIndex, endIndex).filter { it.end > from }
    }

    fun latestEnd(channels: List<Channel>): Long? =
        channels
            .asSequence()
            .mapNotNull { channel ->
                val sourceId = channel.epgSourceId ?: return@mapNotNull null
                val epgChannelId = channel.epgChannelId?.normalizedEpgId() ?: return@mapNotNull null
                latestEndByChannel[sourceId]
                    ?.get(epgChannelId)
                    ?.plus(channel.tvgShiftHours.toOffsetMillis())
            }.maxOrNull()

    private data class ShiftedChannelKey(
        val sourceId: String,
        val channelId: String,
        val offsetMillis: Long,
    )
}

private fun List<Programme>.lowerBoundByStart(value: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (this[middle].start < value) low = middle + 1 else high = middle
    }
    return low
}

private fun List<Programme>.indexOfLastStartAtOrBefore(value: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (this[middle].start <= value) low = middle + 1 else high = middle
    }
    return low - 1
}

private fun String.normalizedEpgId(): String = lowercase()

private fun Double?.toOffsetMillis(): Long = this?.takeIf(Double::isFinite)?.times(MILLIS_PER_HOUR)?.roundToLong() ?: 0L

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
