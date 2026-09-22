package hu.wukki.tv

import kotlin.math.roundToLong

/** Immutable now/next lookup shared by every UI implementation. */
class EpgProgrammeIndex(
    programmes: List<Programme>,
) {
    private val byChannel =
        programmes
            .groupBy { it.channelId.lowercase() }
            .mapValues { (_, entries) -> entries.sortedBy(Programme::start) }

    fun programmes(channel: Channel): List<Programme> {
        val id = channel.epgChannelId?.lowercase() ?: return emptyList()
        val entries = byChannel[id].orEmpty()
        val shift = channel.tvgShiftHours.toOffsetMillis()
        return if (shift == 0L) entries else entries.map { it.copy(start = it.start + shift, end = it.end + shift) }
    }

    fun nowAndNext(
        channel: Channel,
        now: Long,
    ): ProgrammePair {
        val entries = programmes(channel)
        val currentIndex = entries.indexOfLast { it.start <= now && now < it.end }
        val current = entries.getOrNull(currentIndex)
        val next =
            if (current != null) {
                entries.firstOrNull { it.start >= current.end }
            } else {
                entries.firstOrNull { it.start > now }
            }
        return ProgrammePair(current, next)
    }

    fun nextBoundary(
        channels: List<Channel>,
        now: Long,
    ): Long? =
        channels
            .asSequence()
            .flatMap { channel -> programmes(channel).asSequence() }
            .flatMap { sequenceOf(it.start, it.end) }
            .filter { it > now }
            .minOrNull()
}

data class ProgrammePair(
    val current: Programme?,
    val next: Programme?,
)

fun programmeProgress(
    programme: Programme?,
    now: Long,
): Double? =
    programme?.takeIf { it.end > it.start }?.let {
        ((now - it.start).toDouble() / (it.end - it.start)).coerceIn(0.0, 1.0)
    }

private fun Double?.toOffsetMillis(): Long = this?.takeIf(Double::isFinite)?.times(MILLIS_PER_HOUR)?.roundToLong() ?: 0L

private const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
