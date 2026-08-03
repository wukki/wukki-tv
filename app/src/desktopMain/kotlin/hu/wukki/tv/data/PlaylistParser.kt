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
                    result += Channel(id, playlistId, name, line, attributes["tvg-id"], attributes["tvg-name"], attributes["group-title"] ?: "Egyéb", attributes["tvg-logo"])
                    metadata = null
                }
            }
        }
        return result.distinctBy { normalize(it.name) + "|" + it.streamUrl }
    }

    private fun attributes(extinf: String): Map<String, String> =
        Regex("([\\w-]+)=\\\"([^\\\"]*)\\\"").findAll(extinf).associate { it.groupValues[1] to it.groupValues[2] }

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
}

fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
