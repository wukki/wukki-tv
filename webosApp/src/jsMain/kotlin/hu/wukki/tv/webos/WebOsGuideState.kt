package hu.wukki.tv.webos

import hu.wukki.tv.Programme
import kotlin.math.abs

internal const val WEBOS_GUIDE_ROW_HEIGHT = 94
internal const val WEBOS_GUIDE_ROW_BUFFER = 3
internal const val WEBOS_GUIDE_WINDOW_MILLIS = 4L * 60L * 60L * 1_000L

internal enum class WebOsGuideFocusZone { HEADER, CHANNELS, PROGRAMMES }

internal enum class WebOsGuideHeaderAction(
    val labelKey: String,
) {
    NOW("epg.guide.now"),
    TONIGHT("epg.guide.tonight"),
    PREVIOUS_DAY("epg.guide.previousDay"),
    NEXT_DAY("epg.guide.nextDay"),
    ALL("channels.all"),
    FAVORITES("channels.favorites"),
}

internal data class WebOsGuideTimeline(
    val start: Long,
    val end: Long,
) {
    init {
        require(end > start)
    }
}

internal data class WebOsGuideNavigationState(
    val zone: WebOsGuideFocusZone = WebOsGuideFocusZone.HEADER,
    val headerIndex: Int = 0,
    val favoritesOnly: Boolean = false,
    val focusedChannelId: String? = null,
    val focusedProgrammeKey: String? = null,
    val focusTime: Long,
    val windowStart: Long,
)

internal fun Programme.webOsGuideKey(): String = "$channelId|$start|$end"

internal fun guideProgrammeAt(
    programmes: List<Programme>,
    timestamp: Long,
): Programme? =
    programmes.firstOrNull { timestamp in it.start until it.end }
        ?: programmes.minByOrNull { abs(it.start - timestamp) }

internal fun guideAdjacentProgramme(
    programmes: List<Programme>,
    focusedKey: String?,
    focusTime: Long,
    delta: Int,
): Programme? {
    if (programmes.isEmpty()) return null
    val current =
        programmes.indexOfFirst { it.webOsGuideKey() == focusedKey }.takeIf { it >= 0 }
            ?: programmes.indices.minByOrNull { abs(programmes[it].start - focusTime) }
            ?: 0
    return programmes.getOrNull(current + delta.coerceIn(-1, 1))
}

internal fun guideVisibleProgrammes(
    programmes: List<Programme>,
    from: Long,
    to: Long,
): List<Programme> = programmes.filter { it.end > from && it.start < to }

internal fun guideVisibleRows(
    channelCount: Int,
    scrollTop: Int,
    viewportHeight: Int,
    rowHeight: Int = WEBOS_GUIDE_ROW_HEIGHT,
    buffer: Int = WEBOS_GUIDE_ROW_BUFFER,
): IntRange {
    if (channelCount <= 0) return IntRange.EMPTY
    val first = (scrollTop / rowHeight - buffer).coerceIn(0, channelCount - 1)
    val last = ((scrollTop + viewportHeight) / rowHeight + buffer).coerceIn(first, channelCount - 1)
    return first..last
}

internal fun guideWindowStart(
    requested: Long,
    timeline: WebOsGuideTimeline,
    windowMillis: Long = WEBOS_GUIDE_WINDOW_MILLIS,
): Long = requested.coerceIn(timeline.start, (timeline.end - windowMillis).coerceAtLeast(timeline.start))

internal fun guideWindowContaining(
    timestamp: Long,
    currentStart: Long,
    timeline: WebOsGuideTimeline,
    windowMillis: Long = WEBOS_GUIDE_WINDOW_MILLIS,
): Long {
    val margin = 30L * 60L * 1_000L
    val requested =
        when {
            timestamp < currentStart + margin -> timestamp - margin
            timestamp >= currentStart + windowMillis - margin -> timestamp - windowMillis + margin
            else -> currentStart
        }
    return guideWindowStart(requested, timeline, windowMillis)
}
