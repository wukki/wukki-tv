package hu.wukki.tv

import kotlin.math.roundToLong

/**
 * Immutable lookup index for XMLTV programmes. Building it once avoids scanning and sorting the
 * complete EPG cache for every channel row and every Guide recomposition.
 */
internal class ProgrammeIndex(sourceProgrammes: Map<String, List<Programme>>) {
    private val programmesByChannel = sourceProgrammes.mapValues { (_, programmes) ->
        programmes.groupBy { it.channelId.normalizedEpgId() }
            .mapValues { (_, channelProgrammes) -> channelProgrammes.sortedBy(Programme::start) }
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

    fun latestEnd(channels: List<Channel>): Long? = channels.asSequence()
        .flatMap { channel -> programmes(channel).asSequence() }
        .maxOfOrNull(Programme::end)

    private data class ShiftedChannelKey(
        val sourceId: String,
        val channelId: String,
        val offsetMillis: Long
    )
}

private fun String.normalizedEpgId(): String = lowercase()
private fun Double?.toOffsetMillis(): Long =
    this?.takeIf(Double::isFinite)?.times(MILLIS_PER_HOUR)?.roundToLong() ?: 0L

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
