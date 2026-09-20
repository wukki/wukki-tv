package hu.wukki.tv

/** Shared IPTV M3U contract used by Android, desktop and webOS. */
object PlaylistParser {
    fun parse(
        text: String,
        playlistId: String,
        sourceUrl: String? = null,
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
                            id = stableChannelId("$playlistId|$line|$name"),
                            playlistId = playlistId,
                            name = name,
                            streamUrl = resolvePlaylistUrl(line, sourceUrl),
                            tvgId = attributes["tvg-id"],
                            tvgName = attributes["tvg-name"],
                            tvgChno = channelNumber(attributes["tvg-chno"]),
                            group = attributes["group-title"].orEmpty().ifBlank { OTHER_CATEGORY_ID },
                            logo = LogoUrl.fromM3u(attributes["tvg-logo"]),
                            tvgShiftHours = tvgShiftHours(attributes["tvg-shift"]),
                        )
                    metadata = null
                }
            }
        }
        return channels.distinctBy { normalize(it.name) + "|" + it.streamUrl }
    }

    /** Returns the first XMLTV URL declared in the M3U header. */
    fun epgUrl(
        text: String,
        sourceUrl: String? = null,
    ): String? {
        val header = text.lineSequence().firstOrNull { it.trim().startsWith("#EXTM3U", true) } ?: return null
        val values =
            attributes(header)["url-tvg"]
                ?: attributes(header)["x-tvg-url"]
                ?: attributes(header)["tvg-url"]
                ?: return null
        return values
            .split(',', ' ', '\t')
            .firstOrNull { value -> isHttpUrl(value) || (sourceUrl != null && value.isNotBlank()) }
            ?.let { value -> resolvePlaylistUrl(value, sourceUrl) }
            ?.takeIf(::isHttpUrl)
    }

    private fun attributes(extinf: String): Map<String, String> =
        Regex("""([\w-]+)=(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            .findAll(extinf)
            .associate { match ->
                val value =
                    match.groupValues
                        .drop(2)
                        .firstOrNull { candidate -> candidate.isNotEmpty() }
                        .orEmpty()
                        .trim()
                match.groupValues[1].lowercase() to value
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

    private fun channelNumber(value: String?): Int? =
        value?.trim()?.let { raw ->
            raw.toIntOrNull() ?: Regex("^\\d+").find(raw)?.value?.toIntOrNull()
        }

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

fun normalize(value: String): String = coreNormalize(value)

fun platformNormalize(value: String): String = coreNormalize(value)

/** Platform implementations produce the migration-compatible UUID v3 channel identifier. */
expect fun stableChannelId(value: String): String

/** Normalizes the `tvg-logo` image URL and ignores invalid values. */
object LogoUrl {
    fun fromM3u(value: String?): String? =
        value
            ?.trim()
            ?.replace("&amp;", "&")
            ?.takeIf(::isHttpUrl)
}

internal fun resolvePlaylistUrl(
    value: String,
    sourceUrl: String?,
): String {
    val reference = value.trim()
    if (isHttpUrl(reference) || sourceUrl == null) return reference

    val schemeEnd = sourceUrl.indexOf("://")
    if (schemeEnd <= 0) return reference
    val scheme = sourceUrl.substring(0, schemeEnd)
    if (reference.startsWith("//")) return "$scheme:$reference"

    val authorityStart = schemeEnd + 3
    val authorityEnd = sourceUrl.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) sourceUrl.length else it }
    val origin = sourceUrl.substring(0, authorityEnd)
    val sourcePath =
        sourceUrl
            .substring(authorityEnd)
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { "/" }
    val referencePath = reference.substringBefore('?').substringBefore('#')
    val suffix = reference.removePrefix(referencePath)
    val combined =
        if (referencePath.startsWith('/')) {
            referencePath
        } else {
            sourcePath.substringBeforeLast('/', missingDelimiterValue = "") + "/" + referencePath
        }
    val normalized = mutableListOf<String>()
    combined.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
            else -> normalized += segment
        }
    }
    return "$origin/${normalized.joinToString("/")}$suffix"
}

private fun isHttpUrl(value: String): Boolean = value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)
