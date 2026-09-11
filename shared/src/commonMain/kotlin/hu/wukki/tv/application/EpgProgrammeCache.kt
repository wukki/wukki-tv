package hu.wukki.tv

/** Owns the immutable EPG snapshot index independently from repository persistence mutations. */
internal class EpgProgrammeCache {
    private var indexedSources: Map<String, List<Programme>>? = null
    private var index = ProgrammeIndex(emptyMap())
    private var latestChannels: List<Channel>? = null
    private var latestSources: Map<String, List<Programme>>? = null
    private var latestEnd: Long? = null

    fun programmes(
        sources: Map<String, List<Programme>>,
        channel: Channel,
    ): List<Programme> = index(sources).programmes(channel)

    fun currentProgramme(
        sources: Map<String, List<Programme>>,
        channel: Channel,
        now: Long,
    ): Programme? = index(sources).currentProgramme(channel, now)

    fun nextProgramme(
        sources: Map<String, List<Programme>>,
        channel: Channel,
        current: Programme,
    ): Programme? = index(sources).nextProgramme(channel, current)

    fun programmesFor(
        sources: Map<String, List<Programme>>,
        channel: Channel,
        from: Long,
        to: Long,
    ): List<Programme> = index(sources).programmesFor(channel, from, to)

    fun latestEnd(
        sources: Map<String, List<Programme>>,
        channels: List<Channel>,
    ): Long? {
        if (channels !== latestChannels || sources !== latestSources) {
            latestEnd = index(sources).latestEnd(channels)
            latestChannels = channels
            latestSources = sources
        }
        return latestEnd
    }

    private fun index(sources: Map<String, List<Programme>>): ProgrammeIndex {
        if (sources !== indexedSources) {
            index = ProgrammeIndex(sources)
            indexedSources = sources
            latestChannels = null
            latestSources = null
            latestEnd = null
        }
        return index
    }
}
