package hu.wukki.tv.ui.guide

import kotlin.math.ceil
import kotlin.math.floor

/** The small portion of a potentially multi-day timeline that is worth composing. */
internal data class GuideViewport(
    val from: Long,
    val to: Long,
    val firstTickIndex: Int,
    val lastTickIndex: Int
) {
    val tickIndices: IntRange
        get() = if (lastTickIndex < firstTickIndex) IntRange.EMPTY else firstTickIndex..lastTickIndex
}

internal fun guideViewport(
    timeline: GuideTimeline,
    scrollPx: Int,
    viewportWidthPx: Int,
    pixelsPerMinute: Float,
    overscanMinutes: Int = 60
): GuideViewport {
    val totalMinutes = ((timeline.end - timeline.start) / 60_000.0).coerceAtLeast(0.0)
    val safePixelsPerMinute = pixelsPerMinute.takeIf { it.isFinite() && it > 0f } ?: 1f
    val visibleMinutes = viewportWidthPx.coerceAtLeast(0) / safePixelsPerMinute.toDouble()
    val scrollMinute = scrollPx.coerceAtLeast(0) / safePixelsPerMinute.toDouble()
    val overscan = overscanMinutes.coerceAtLeast(0).toDouble()
    val fromMinute = floor(scrollMinute - overscan).coerceIn(0.0, totalMinutes)
    val toMinute = ceil(scrollMinute + visibleMinutes + overscan).coerceIn(fromMinute, totalMinutes)
    val lastTimelineTick = floor(totalMinutes / HALF_HOUR_MINUTES).toInt()

    return GuideViewport(
        from = timeline.start + (fromMinute * 60_000.0).toLong(),
        to = (timeline.start + (toMinute * 60_000.0).toLong()).coerceAtMost(timeline.end),
        firstTickIndex = floor(fromMinute / HALF_HOUR_MINUTES).toInt(),
        lastTickIndex = ceil(toMinute / HALF_HOUR_MINUTES).toInt().coerceAtMost(lastTimelineTick)
    )
}
