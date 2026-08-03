package hu.wukki.tv

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.UUID

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
                            attributes["tvg-id"]?.trim().orEmpty().ifBlank { "Ismeretlen csatorna" }
                        }
                    }
                    val id = UUID.nameUUIDFromBytes("$playlistId|$line|$name".toByteArray(StandardCharsets.UTF_8)).toString()
                    result += Channel(
                        id = id,
                        playlistId = playlistId,
                        name = name,
                        streamUrl = line,
                        tvgId = attributes["tvg-id"],
                        tvgName = attributes["tvg-name"],
                        tvgChno = channelNumber(attributes["tvg-chno"]),
                        group = attributes["group-title"] ?: "Egyéb",
                        logo = LogoUrl.fromM3u(attributes["tvg-logo"])
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
}

fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

/** Normalizes the `tvg-logo` image URL and ignores invalid values. */
object LogoUrl {
    fun fromM3u(value: String?): String? = value
        ?.trim()
        ?.replace("&amp;", "&")
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}
