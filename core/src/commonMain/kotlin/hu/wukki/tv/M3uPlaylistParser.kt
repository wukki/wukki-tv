package hu.wukki.tv

/** Parses IPTV-style extended M3U playlists. HLS media manifests are deliberately ignored. */
object M3uPlaylistParser {
    fun parse(
        text: String,
        playlistId: String,
    ): List<Channel> {
        if (text.lineSequence().any { it.trim().startsWith("#EXT-X-", ignoreCase = true) }) return emptyList()

        val channels = mutableListOf<Channel>()
        var metadata: String? = null
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    metadata = line
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    val extinf = metadata ?: return@forEach
                    val attributes = attributes(extinf)
                    val name =
                        displayName(extinf).ifBlank {
                            attributes["tvg-name"].orEmpty().ifBlank {
                                attributes["tvg-id"].orEmpty().ifBlank { UNKNOWN_CHANNEL_NAME_ID }
                            }
                        }
                    channels +=
                        Channel(
                            id = stableId("$playlistId|$line|$name"),
                            playlistId = playlistId,
                            name = name,
                            streamUrl = line,
                            tvgId = attributes["tvg-id"],
                            tvgName = attributes["tvg-name"],
                            tvgChno = attributes["tvg-chno"]?.toIntOrNull(),
                            group = attributes["group-title"].orEmpty().ifBlank { OTHER_CATEGORY_ID },
                            logo = safeHttpUrl(attributes["tvg-logo"]),
                        )
                    metadata = null
                }
            }
        }
        return channels.distinctBy { coreNormalize(it.name) + "|" + it.streamUrl }
    }

    private fun attributes(extinf: String): Map<String, String> =
        Regex("""([\w-]+)=(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            .findAll(extinf)
            .associate { match ->
                val value =
                    match.groupValues
                        .drop(2)
                        .firstOrNull { value -> value.isNotEmpty() }
                        .orEmpty()
                        .trim()
                match.groupValues[1].lowercase() to
                    value
            }

    private fun displayName(extinf: String): String {
        var quoted = false
        extinf.forEachIndexed { index, character ->
            when (character) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return extinf.substring(index + 1).trim()
            }
        }
        return ""
    }

    private fun safeHttpUrl(value: String?): String? =
        value
            ?.trim()
            ?.replace("&amp;", "&")
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /** FNV-1a keeps identifiers deterministic on JVM, Android and JavaScript without platform APIs. */
    private fun stableId(value: String): String {
        var hash = 0x811c9dc5u
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
        }
        return "m3u-${hash.toString(16).padStart(8, '0')}"
    }
}
