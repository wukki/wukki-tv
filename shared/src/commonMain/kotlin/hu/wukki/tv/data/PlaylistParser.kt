package hu.wukki.tv

object PlaylistParser {
    fun parse(text: String, playlistId: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var metadata: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> metadata = line
                line.isNotBlank() && !line.startsWith("#") -> {
                    val extinf = metadata ?: return@forEach
                    val attributes = attributes(extinf)
                    val name = displayName(extinf).ifBlank {
                        attributes["tvg-name"]?.trim().orEmpty().ifBlank {
                            attributes["tvg-id"]?.trim().orEmpty().ifBlank { UNKNOWN_CHANNEL_NAME_ID }
                        }
                    }
                    val id = stableChannelId("$playlistId|$line|$name")
                    result += Channel(
                        id = id,
                        playlistId = playlistId,
                        name = name,
                        streamUrl = line,
                        tvgId = attributes["tvg-id"],
                        tvgName = attributes["tvg-name"],
                        tvgChno = channelNumber(attributes["tvg-chno"]),
                        group = attributes["group-title"]?.trim().orEmpty().ifBlank { OTHER_CATEGORY_ID },
                        logo = LogoUrl.fromM3u(attributes["tvg-logo"]),
                        tvgShiftHours = tvgShiftHours(attributes["tvg-shift"])
                    )
                    metadata = null
                }
            }
        }
        return result.distinctBy { normalize(it.name) + "|" + it.streamUrl }
    }

    private fun attributes(extinf: String): Map<String, String> =
        Regex("([\\w-]+)=(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s]+))").findAll(extinf).associate {
            it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull { value -> value.isNotEmpty() }.orEmpty()
        }

    /** Returns the first XMLTV URL declared in the M3U header, if the provider supplied one. */
    fun epgUrl(text: String): String? {
        val header = text.lineSequence().firstOrNull { it.trim().startsWith("#EXTM3U", true) } ?: return null
        val rawValue = attributes(header)["url-tvg"]
            ?: attributes(header)["x-tvg-url"]
            ?: attributes(header)["tvg-url"]
            ?: return null
        return rawValue.split(',', ' ', '\t').firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    /** The display name follows the first comma that is not inside a quoted M3U attribute. */
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

    private fun channelNumber(value: String?): Int? = value?.trim()?.let { raw ->
        raw.toIntOrNull() ?: Regex("^\\d+").find(raw)?.value?.toIntOrNull()
    }

    /**
     * `tvg-shift` is normally a signed hour value, but some providers use an
     * `±HH:mm` form. Both forms are accepted so an invalid value never shifts
     * programme times accidentally.
     */
    private fun tvgShiftHours(value: String?): Double? {
        val raw = value?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() } ?: return null
        raw.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return it }
        val match = Regex("^([+-]?)(\\d{1,2}):(\\d{2})$").matchEntire(raw) ?: return null
        val hours = match.groupValues[2].toIntOrNull() ?: return null
        val minutes = match.groupValues[3].toIntOrNull()?.takeIf { it < 60 } ?: return null
        val magnitude = hours + minutes / 60.0
        return if (match.groupValues[1] == "-") -magnitude else magnitude
    }
}

fun normalize(value: String): String = platformNormalize(value)

/** Platform implementations supply Unicode normalisation and stable ID generation. */
expect fun platformNormalize(value: String): String
expect fun stableChannelId(value: String): String

/** Normalizes the `tvg-logo` image URL and ignores invalid values. */
object LogoUrl {
    fun fromM3u(value: String?): String? = value
        ?.trim()
        ?.replace("&amp;", "&")
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}
