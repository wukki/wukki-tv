package hu.wukki.tv

import java.io.StringReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

object EpgParser : XmlTvParser {
    override fun parse(xml: String): List<Programme> {
        val programmes = mutableListOf<Programme>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newSAXParser().apply {
            xmlReader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            parse(InputSource(StringReader(xml)), ProgrammeHandler(programmes))
        }
        return programmes.sortedBy { it.start }
    }

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

    /**
     * A streaming parser avoids building a large in-memory DOM for multi-megabyte XMLTV files.
     * This is particularly important on Android TV devices with constrained application heaps.
     */
    private class ProgrammeHandler(private val programmes: MutableList<Programme>) : DefaultHandler() {
        private var programme: MutableProgramme? = null
        private var activeTextTag: String? = null
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            when (tagName(localName, qName)) {
                "programme" -> programme = MutableProgramme(
                    channelId = attributes.getValue("channel")?.trim().orEmpty(),
                    start = parseTime(attributes.getValue("start").orEmpty()),
                    end = parseTime(attributes.getValue("stop").orEmpty())
                )
                "title", "desc" -> if (programme != null) {
                    activeTextTag = tagName(localName, qName)
                    text.setLength(0)
                }
                "icon", "image" -> programme?.let { current ->
                    activeTextTag = tagName(localName, qName)
                    text.setLength(0)
                    current.imageUrl = current.imageUrl ?: validImageUrl(attributes.getValue("src"))
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (activeTextTag != null) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val tag = tagName(localName, qName)
            val current = programme
            when (tag) {
                "title" -> current?.takeIf { activeTextTag == "title" }?.let {
                    if (it.title.isBlank()) it.title = text.toString().trim()
                    activeTextTag = null
                }
                "desc" -> current?.takeIf { activeTextTag == "desc" }?.let {
                    if (it.description == null) it.description = text.toString().trim().ifBlank { null }
                    activeTextTag = null
                }
                "icon", "image" -> current?.takeIf { activeTextTag == tag }?.let {
                    it.imageUrl = it.imageUrl ?: validImageUrl(text.toString())
                    activeTextTag = null
                }
                "programme" -> {
                    programme = null
                    activeTextTag = null
                    if (current != null && current.channelId.isNotBlank() && current.start != null && current.end != null && current.end > current.start) {
                        programmes += Programme(
                            channelId = current.channelId,
                            title = current.title,
                            start = current.start,
                            end = current.end,
                            description = current.description,
                            imageUrl = current.imageUrl
                        )
                    }
                }
            }
        }

        private fun tagName(localName: String?, qName: String): String = localName?.ifBlank { qName }.orEmpty().lowercase()
    }

    private data class MutableProgramme(
        val channelId: String,
        val start: Long?,
        val end: Long?,
        var title: String = "",
        var description: String? = null,
        var imageUrl: String? = null
    )

    /** XMLTV uses `<icon src>`; `image` remains a provider-specific fallback. */
    private fun validImageUrl(value: String?): String? = value
        ?.trim()
        ?.replace("&amp;", "&")
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}
