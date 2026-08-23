package hu.wukki.tv.ui.guide

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.platformGuideDateLabel
import hu.wukki.tv.ui.components.platformIsStartOfDay
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ReferenceMinuteWidth = 6.dp
internal val ReferenceChannelColumnWidth = 160.dp
internal val ReferenceGuideRowHeight = 112.dp
internal const val REFERENCE_GUIDE_WIDTH = 1116f
internal const val REFERENCE_GUIDE_HEIGHT = 892f
internal val GuidePanel = WukkiColors.background
internal val GuideSurface = WukkiColors.surface
internal val GuideBorder = WukkiColors.borderSubtle
internal val GuideMuted = WukkiColors.textMuted
internal val GuideAccent = WukkiColors.primary

internal data class GuideLayoutMetrics(
    val scale: Float,
    val minuteWidth: Dp,
    val channelColumnWidth: Dp,
    val rowHeight: Dp,
    val timelineHeight: Dp
)

@Composable
internal fun TimelineHeader(
    language: AppLanguage,
    state: EpgGuideState,
    timeline: GuideTimeline,
    timelineWidth: Dp,
    tick: Long,
    metrics: GuideLayoutMetrics
) {
    Row(
        Modifier.fillMaxWidth().height(metrics.timelineHeight)
            .background(WukkiColors.backgroundRaised).border(1.dp, GuideBorder)
    ) {
        Box(
            Modifier.width(metrics.channelColumnWidth).fillMaxHeight().background(WukkiColors.navigationBackground),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("·", color = GuideMuted, fontSize = (18f * metrics.scale).sp, modifier = Modifier.padding(start = 28.dp * metrics.scale))
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))
                .onSizeChanged { state.viewportWidthPx = it.width }
                .horizontalScroll(state.horizontalScroll)
        ) {
            Box(Modifier.requiredWidth(timelineWidth).fillMaxHeight()) {
                TimelineTicks(language, timeline, metrics)
                CurrentTimeHeaderIndicator(tick, timeline, metrics)
            }
        }
    }
}

@Composable
private fun TimelineTicks(language: AppLanguage, timeline: GuideTimeline, metrics: GuideLayoutMetrics) {
    repeat(timeline.halfHourTickCount) { index ->
        val timestamp = timeline.start + index * HALF_HOUR_MINUTES * 60_000L
        val offset = metrics.minuteWidth * (index * HALF_HOUR_MINUTES)
        val isDayStart = platformIsStartOfDay(timestamp)
        Box(
            Modifier.offset(x = offset).width(if (isDayStart) 2.dp * metrics.scale else 1.dp)
                .fillMaxHeight().background(if (isDayStart) WukkiColors.focus else GuideBorder)
        )
        Text(
            if (isDayStart) platformGuideDateLabel(language, timestamp, tr(language, "date.guide.pattern")) else formatTime(timestamp),
            color = if (isDayStart) WukkiColors.textSecondary else GuideMuted,
            fontSize = (if (isDayStart) 13f else 15f * metrics.scale).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                x = offset - 48.dp * metrics.scale,
                y = if (isDayStart) 6.dp * metrics.scale else 34.dp * metrics.scale
            ).width(96.dp * metrics.scale)
        )
    }
}

@Composable
private fun CurrentTimeHeaderIndicator(now: Long, timeline: GuideTimeline, metrics: GuideLayoutMetrics) {
    if (now !in timeline.start until timeline.end) return
    val minute = timeline.minutesFromStart(now)
    val bubbleWidth = 88.dp * metrics.scale
    val bubbleHeight = 43.dp * metrics.scale
    val pointerHeight = 10.dp * metrics.scale
    Box(
        Modifier.offset(x = metrics.minuteWidth * minute - 1.dp * metrics.scale)
            .width(2.dp * metrics.scale).fillMaxHeight().background(GuideAccent)
    )
    Column(
        modifier = Modifier.offset(x = metrics.minuteWidth * minute - bubbleWidth / 2).width(bubbleWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(bubbleHeight)
                .clip(RoundedCornerShape(9.dp * metrics.scale)).background(WukkiColors.primaryMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(formatTime(now), color = WukkiColors.textPrimary, fontSize = (18f * metrics.scale).sp, fontWeight = FontWeight.SemiBold)
        }
        Canvas(Modifier.size(18.dp * metrics.scale, pointerHeight)) {
            val triangle = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(triangle, GuideAccent)
        }
    }
}

@Composable
internal fun CurrentTimeBodyLine(
    now: Long,
    timeline: GuideTimeline,
    state: EpgGuideState,
    metrics: GuideLayoutMetrics
) {
    if (now !in timeline.start until timeline.end) return
    val density = LocalDensity.current
    val channelWidthPx = with(density) { metrics.channelColumnWidth.toPx() }
    val x = channelWidthPx + timeline.minutesFromStart(now) * state.pixelsPerMinute - state.horizontalScroll.value
    Canvas(Modifier.fillMaxSize()) {
        if (x in channelWidthPx..size.width) {
            drawLine(
                color = GuideAccent,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = with(density) { (2.dp * metrics.scale).toPx() }
            )
        }
    }
}

internal val GuideTimeline.minutes: Float get() = (end - start) / 60_000f
internal val GuideTimeline.halfHourTickCount: Int get() = (minutes / HALF_HOUR_MINUTES).toInt() + 1
internal fun GuideTimeline.minutesFromStart(timestamp: Long): Float = (timestamp - start) / 60_000f
