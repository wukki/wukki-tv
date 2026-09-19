package hu.wukki.tv

object EpgMatcher {
    fun match(
        channels: List<Channel>,
        programmes: List<Programme>,
    ): List<Channel> {
        val ids = programmes.map { it.channelId }.distinct()
        return channels.map { channel ->
            val exact =
                ids.firstOrNull { it.equals(channel.tvgId, true) }
                    ?: ids.firstOrNull { coreNormalize(it) == coreNormalize(channel.tvgName ?: channel.name) }
            val best =
                exact ?: ids
                    .maxByOrNull { similarity(coreNormalize(channel.name), coreNormalize(it)) }
                    ?.takeIf { similarity(coreNormalize(channel.name), coreNormalize(it)) >= .55 }
            channel.copy(epgChannelId = best)
        }
    }

    fun matchFromSources(
        channels: List<Channel>,
        sources: List<EpgSource>,
        programmesBySource: Map<String, List<Programme>>,
    ): List<Channel> =
        channels.map { channel ->
            sources.filter { it.enabled }.sortedBy { it.priority }.firstNotNullOfOrNull { source ->
                val candidate = match(listOf(channel), programmesBySource[source.id].orEmpty()).first()
                candidate.epgChannelId?.let { candidate.copy(epgSourceId = source.id) }
            } ?: channel.copy(epgChannelId = null, epgSourceId = null)
        }

    private fun similarity(
        left: String,
        right: String,
    ): Double {
        if (left == right) return 1.0
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        return if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            0.0
        } else {
            leftTokens.intersect(rightTokens).size.toDouble() / leftTokens.union(rightTokens).size
        }
    }
}

internal expect fun coreNormalize(value: String): String
