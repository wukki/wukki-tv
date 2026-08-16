package hu.wukki.tv

import org.w3c.dom.Element
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object EpgParser {
    fun parse(xml: String): List<Programme> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        return (0 until document.getElementsByTagName("programme").length).mapNotNull { index ->
            val element = document.getElementsByTagName("programme").item(index) as? Element ?: return@mapNotNull null
            val start = parseTime(element.getAttribute("start")) ?: return@mapNotNull null
            val end = parseTime(element.getAttribute("stop")) ?: return@mapNotNull null
            val channelId = element.getAttribute("channel").trim()
            if (channelId.isBlank() || end <= start) return@mapNotNull null
            Programme(
                channelId = channelId,
                title = element.textOf("title"),
                start = start,
                end = end,
                description = element.textOf("desc").ifBlank { null },
                imageUrl = element.imageUrl()
            )
        }.sortedBy { it.start }
    }

    private fun Element.textOf(tag: String): String = getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()

    /** XMLTV uses `<icon src>`; `image` is accepted as a provider-specific fallback. */
    private fun Element.imageUrl(): String? = sequenceOf("icon", "image")
        .mapNotNull { tag ->
            val image = getElementsByTagName(tag).item(0) as? Element ?: return@mapNotNull null
            image.getAttribute("src").ifBlank { image.textContent.orEmpty() }
                .trim()
                .replace("&amp;", "&")
                .takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
        }
        .firstOrNull()

    private fun parseTime(raw: String): Long? = try {
        val base = raw.trim().take(14)
        val offset = raw.trim().drop(14).trim().ifBlank { "+0000" }
        OffsetDateTime.parse("$base $offset", DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(raw.trim().take(14), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

object EpgMatcher {
    fun match(channels: List<Channel>, programmes: List<Programme>): List<Channel> {
        val ids = programmes.map { it.channelId }.distinct()
        return channels.map { channel ->
            val exact = ids.firstOrNull { it.equals(channel.tvgId, true) }
                ?: ids.firstOrNull { normalize(it) == normalize(channel.tvgName ?: channel.name) }
            val best = exact ?: ids.maxByOrNull { similarity(normalize(channel.name), normalize(it)) }
                ?.takeIf { similarity(normalize(channel.name), normalize(it)) >= .55 }
            channel.copy(epgChannelId = best)
        }
    }

    fun matchFromSources(
        channels: List<Channel>,
        sources: List<EpgSource>,
        programmesBySource: Map<String, List<Programme>>
    ): List<Channel> {
        return channels.map { channel ->
            val matchingSource = sources.filter { it.enabled }.sortedBy { it.priority }.firstNotNullOfOrNull { source ->
                val candidate = match(listOf(channel), programmesBySource[source.id].orEmpty()).first()
                candidate.epgChannelId?.let { candidate.copy(epgSourceId = source.id) }
            }
            matchingSource ?: channel.copy(epgChannelId = null, epgSourceId = null)
        }
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        return if (leftTokens.isEmpty() || rightTokens.isEmpty()) 0.0 else leftTokens.intersect(rightTokens).size.toDouble() / leftTokens.union(rightTokens).size
    }
}
