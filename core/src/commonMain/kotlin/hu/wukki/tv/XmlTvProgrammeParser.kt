package hu.wukki.tv

/** Shared, deterministic XMLTV parser used by JavaScript and JVM fixture tests. */
object XmlTvProgrammeParser {
    const val MAX_DOCUMENT_BYTES: Int = 32 * 1024 * 1024

    fun parse(xml: String): List<Programme> {
        validateDocument(xml)
        val programmes = mutableListOf<Programme>()
        var offset = 0
        while (true) {
            val batch = parseBatch(xml, offset)
            programmes += batch.programmes
            if (batch.complete) return programmes.sortedBy(Programme::start)
            offset = batch.nextOffset
        }
    }

    fun parseBatch(
        xml: String,
        offset: Int = 0,
        limit: Int = 250,
    ): XmlTvParseBatch {
        require(limit > 0) { "XMLTV batch size must be positive" }
        if (offset == 0) validateDocument(xml)
        val programmes = mutableListOf<Programme>()
        var match = programmePattern.find(xml, offset.coerceAtLeast(0))
        var nextOffset = offset
        var processed = 0
        while (match != null && processed < limit) {
            parseProgramme(match.groupValues[1], match.groupValues[2])?.let(programmes::add)
            nextOffset = match.range.last + 1
            processed++
            match = programmePattern.find(xml, nextOffset)
        }
        return XmlTvParseBatch(
            programmes = programmes,
            nextOffset = nextOffset,
            complete = match == null,
        )
    }

    fun validateDocument(xml: String) {
        require(xml.encodeToByteArray().size <= MAX_DOCUMENT_BYTES) { "XMLTV input exceeds the document limit" }
        require(!unsafeDeclaration.containsMatchIn(xml)) { "XMLTV DTD and entity declarations are disabled" }
    }

    private fun parseProgramme(
        rawAttributes: String,
        body: String,
    ): Programme? {
        val attributes = attributes(rawAttributes)
        val channelId = attributes["channel"].orEmpty().trim()
        val start = parseTimestamp(attributes["start"] ?: return null) ?: return null
        val end = parseTimestamp(attributes["stop"] ?: return null) ?: return null
        if (channelId.isBlank() || end <= start) return null
        return Programme(
            channelId = decodeXml(channelId),
            title = text(body, "title").orEmpty(),
            start = start,
            end = end,
            description = text(body, "desc")?.ifBlank { null },
            imageUrl = imageUrl(body),
        )
    }

    private fun text(
        body: String,
        tag: String,
    ): String? =
        Regex("<$tag(?:\\s[^>]*)?>([\\s\\S]*?)</$tag\\s*>", XML_OPTIONS)
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.let(::plainText)

    private fun imageUrl(body: String): String? {
        val icon =
            Regex("<icon(?:\\s[^>]*)?/?>", XML_OPTIONS)
                .find(body)
                ?.value
                ?.let(::attributes)
                ?.get("src")
        val textImage = text(body, "image")
        return sequenceOf(icon, textImage)
            .mapNotNull { it?.let(::decodeXml)?.trim() }
            .firstOrNull(::isHttpUrl)
    }

    private fun plainText(value: String): String =
        decodeXml(
            value
                .replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>", XML_OPTIONS), "$1")
                .replace(Regex("<[^>]+>"), ""),
        ).trim()

    private fun attributes(value: String): Map<String, String> =
        attributePattern.findAll(value).associate { match ->
            match.groupValues[1].lowercase() to
                match.groupValues.drop(2).first { it.isNotEmpty() }
        }

    private fun parseTimestamp(raw: String): Long? {
        val match = timestampPattern.matchEntire(raw.trim()) ?: return null
        val digits = match.groupValues[1].padEnd(14, '0')
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val minute = digits.substring(10, 12).toIntOrNull() ?: return null
        val second = digits.substring(12, 14).toIntOrNull() ?: return null
        if (!isValidDateTime(year, month, day, hour, minute, second)) return null
        val offset = parseOffset(match.groupValues[2]) ?: return null
        val epochSeconds = daysFromCivil(year, month, day) * SECONDS_PER_DAY + hour * 3_600L + minute * 60L + second - offset
        return epochSeconds * 1_000L
    }

    private fun parseOffset(raw: String): Long? {
        if (raw.isBlank()) return 0L
        val normalized = raw.replace(":", "")
        if (normalized.equals("Z", ignoreCase = true)) return 0L
        val sign =
            when (normalized.firstOrNull()) {
                '+' -> 1
                '-' -> -1
                else -> return null
            }
        val hours = normalized.drop(1).take(2).toIntOrNull() ?: return null
        val minutes = normalized.drop(3).take(2).toIntOrNull() ?: return null
        if (hours > 23 || minutes > 59) return null
        return sign * (hours * 3_600L + minutes * 60L)
    }

    private fun daysFromCivil(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val adjustedYear = year - if (month <= 2) 1 else 0
        val era = floorDiv(adjustedYear, 400)
        val yearOfEra = adjustedYear - era * 400
        val monthPosition = month + if (month > 2) -3 else 9
        val dayOfYear = (153 * monthPosition + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return (era * 146_097L + dayOfEra - 719_468L)
    }

    private fun floorDiv(
        value: Int,
        divisor: Int,
    ): Int = if (value >= 0) value / divisor else (value - divisor + 1) / divisor

    private fun daysInMonth(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
            2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    private fun isValidDateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Boolean {
        if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return false
        if (hour !in 0..23 || minute !in 0..59) return false
        return second in 0..59
    }

    private fun decodeXml(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun isHttpUrl(value: String): Boolean = value.startsWith("https://", true) || value.startsWith("http://", true)

    private val XML_OPTIONS = setOf(RegexOption.IGNORE_CASE)
    private val programmePattern = Regex("<programme(?:\\s([^>]*))?>([\\s\\S]*?)</programme\\s*>", XML_OPTIONS)
    private val attributePattern = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val timestampPattern = Regex("^(\\d{8,14})(?:\\s+([+-]\\d{2}:?\\d{2}|Z))?.*$", RegexOption.IGNORE_CASE)
    private val unsafeDeclaration = Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
    private const val SECONDS_PER_DAY = 86_400L
}

data class XmlTvParseBatch(
    val programmes: List<Programme>,
    val nextOffset: Int,
    val complete: Boolean,
)
