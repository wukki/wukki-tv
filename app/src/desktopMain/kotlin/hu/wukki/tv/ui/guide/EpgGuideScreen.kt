package hu.wukki.tv.ui.guide

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.Localizer
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MINUTES_PER_DAY = 24 * 60
private const val HALF_HOUR_MINUTES = 30
private val ReferenceMinuteWidth = 6.dp
private val ReferenceChannelColumnWidth = 160.dp
private val ReferenceGuideRowHeight = 112.dp
private const val REFERENCE_GUIDE_WIDTH = 1116f
private const val REFERENCE_GUIDE_HEIGHT = 892f
private val GuidePanel = WukkiColors.background
private val GuideSurface = WukkiColors.surface
private val GuideBorder = WukkiColors.borderSubtle
private val GuideMuted = WukkiColors.textMuted
private val GuideAccent = WukkiColors.primary

private data class GuideLayoutMetrics(
    val scale: Float,
    val minuteWidth: androidx.compose.ui.unit.Dp,
    val channelColumnWidth: androidx.compose.ui.unit.Dp,
    val rowHeight: androidx.compose.ui.unit.Dp,
    val timelineHeight: androidx.compose.ui.unit.Dp
)

class EpgGuideState internal constructor(
    val horizontalScroll: androidx.compose.foundation.ScrollState,
    val verticalList: LazyListState
) {
    var focusedChannelId by mutableStateOf<String?>(null)
        private set
    var focusedProgrammeKey by mutableStateOf<String?>(null)
        private set
    var focusTime by mutableStateOf(System.currentTimeMillis())
        private set
    var pixelsPerMinute: Float = 6f
    var viewportWidthPx: Int = 0
    private var initialisedChannelIds by mutableStateOf<List<String>>(emptyList())
    private var initialisedTimelineStart by mutableStateOf<Long?>(null)
    private var initiallyScrolledTimelineStart by mutableStateOf<Long?>(null)

    fun selectProgramme(channel: Channel, programme: Programme) {
        focusedChannelId = channel.id
        focusedProgrammeKey = programme.key()
        focusTime = programme.middleTime()
    }

    fun selectChannel(channel: Channel) {
        focusedChannelId = channel.id
        focusedProgrammeKey = null
    }

    /** The programme currently targeted by D-pad navigation, if the row has EPG data. */
    fun focusedProgramme(data: GuideDataSource, timeline: GuideTimeline): Pair<Channel, Programme>? {
        val channel = data.channels().firstOrNull { it.id == focusedChannelId } ?: return null
        val programmes = data.programmesFor(channel, timeline.start, timeline.end)
        val programme = programmes.firstOrNull { it.key() == focusedProgrammeKey }
            ?: programmes.minByOrNull { abs(it.start - focusTime) }
            ?: return null
        return channel to programme
    }

    suspend fun initialise(data: GuideDataSource, channels: List<Channel>, timeline: GuideTimeline) {
        if (channels.isEmpty()) return
        val channelIds = channels.map { it.id }
        val shouldRestoreVerticalPosition = initialisedChannelIds != channelIds ||
            initialisedTimelineStart != timeline.start ||
            focusedChannelId !in channelIds
        val channelIndex = channels.indexOfFirst { it.id == focusedChannelId }
            .takeIf { it >= 0 }
            ?: channels.indexOfFirst { it.id == data.selectedChannelId }.takeIf { it >= 0 }
            ?: 0
        val channel = channels[channelIndex]
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, focusTime, timeline)
        if (shouldRestoreVerticalPosition) verticalList.scrollToItem(channelIndex)
        initialisedChannelIds = channelIds
        initialisedTimelineStart = timeline.start
    }

    /** The initial "now" position is applied once per timeline start, never on screen re-entry. */
    fun needsInitialTimelineScroll(timeline: GuideTimeline): Boolean =
        initiallyScrolledTimelineStart != timeline.start

    fun markInitialTimelineScrollApplied(timeline: GuideTimeline) {
        initiallyScrolledTimelineStart = timeline.start
    }

    fun handleKey(key: Key, data: GuideDataSource, scope: CoroutineScope, timeline: GuideTimeline): Boolean {
        val channels = data.channels()
        return when (key) {
            Key.DirectionUp, Key.PageUp -> true.also { scope.launch { moveChannel(data, channels, timeline, -1) } }
            Key.DirectionDown, Key.PageDown -> true.also { scope.launch { moveChannel(data, channels, timeline, 1) } }
            Key.DirectionLeft -> true.also { scope.launch { moveProgramme(data, channels, timeline, -1) } }
            Key.DirectionRight -> true.also { scope.launch { moveProgramme(data, channels, timeline, 1) } }
            Key.Enter, Key.NumPadEnter -> true
            else -> false
        }
    }

    suspend fun scrollToInitialTime(timeline: GuideTimeline, now: Long) {
        scrollToTime((now - HALF_HOUR_MINUTES * 60_000L).coerceIn(timeline.start, timeline.end - 1), timeline, animate = false)
    }

    private suspend fun moveChannel(data: GuideDataSource, channels: List<Channel>, timeline: GuideTimeline, delta: Int) {
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.id == focusedChannelId }.let { if (it < 0) 0 else it }
        val target = (current + delta).coerceIn(0, channels.lastIndex)
        val channel = channels[target]
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, focusTime, timeline)
        verticalList.animateScrollToItem(target)
    }

    private suspend fun moveProgramme(
        data: GuideDataSource,
        channels: List<Channel>,
        timeline: GuideTimeline,
        delta: Int
    ) {
        val channel = channels.firstOrNull { it.id == focusedChannelId } ?: return
        val direction = delta.coerceIn(-1, 1)
        if (direction == 0) return
        val programmes = data.programmesFor(channel, timeline.start, timeline.end)
        if (programmes.isEmpty()) return
        val current = programmes.indexOfFirst { it.key() == focusedProgrammeKey }.let { index ->
            if (index >= 0) index else programmes.indexOfClosest(focusTime)
        }
        val target = current + direction
        if (target !in programmes.indices) return
        val programme = programmes[target]
        selectProgramme(channel, programme)
        ensureVisible(programme, timeline)
    }

    private fun chooseProgrammeAt(data: GuideDataSource, channel: Channel, timestamp: Long, timeline: GuideTimeline) {
        val programmes = data.programmesFor(channel, timeline.start, timeline.end)
        val programme = programmes.firstOrNull { timestamp in it.start until it.end }
            ?: programmes.minByOrNull { abs(it.start - timestamp) }
        focusedProgrammeKey = programme?.key()
        focusTime = programme?.middleTime() ?: timestamp.coerceIn(timeline.start, timeline.end - 1)
    }

    private suspend fun ensureVisible(programme: Programme, timeline: GuideTimeline) {
        if (viewportWidthPx <= 0) return
        val left = ((max(programme.start, timeline.start) - timeline.start) / 60_000f * pixelsPerMinute).roundToInt()
        val right = ((min(programme.end, timeline.end) - timeline.start) / 60_000f * pixelsPerMinute).roundToInt()
        val margin = (HALF_HOUR_MINUTES * pixelsPerMinute).roundToInt()
        val current = horizontalScroll.value
        val target = when {
            left < current + margin -> left - margin
            right > current + viewportWidthPx - margin -> right - viewportWidthPx + margin
            else -> current
        }.coerceIn(0, horizontalScroll.maxValue)
        horizontalScroll.animateScrollTo(target)
    }

    private suspend fun scrollToTime(time: Long, timeline: GuideTimeline, animate: Boolean) {
        val target = ((time - timeline.start) / 60_000f * pixelsPerMinute).roundToInt().coerceIn(0, horizontalScroll.maxValue)
        if (animate) horizontalScroll.animateScrollTo(target) else horizontalScroll.scrollTo(target)
    }
}

@Composable
fun rememberEpgGuideState(): EpgGuideState {
    val horizontal = rememberScrollState()
    val vertical = androidx.compose.foundation.lazy.rememberLazyListState()
    return remember(horizontal, vertical) { EpgGuideState(horizontal, vertical) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpgGuideScreen(
    data: GuideDataSource,
    tick: Long,
    state: EpgGuideState,
    onProgrammeClick: (Channel, Programme) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val layoutScale = min(
            maxWidth.value / REFERENCE_GUIDE_WIDTH,
            maxHeight.value / REFERENCE_GUIDE_HEIGHT
        ).coerceIn(.70f, 1f)
        val metrics = GuideLayoutMetrics(
            scale = layoutScale,
            minuteWidth = ReferenceMinuteWidth * layoutScale,
            channelColumnWidth = ReferenceChannelColumnWidth * layoutScale,
            rowHeight = ReferenceGuideRowHeight * layoutScale,
            timelineHeight = 70.dp * layoutScale
        )
        val channels = data.channels()
        val density = LocalDensity.current
        val minuteWidthPx = with(density) { metrics.minuteWidth.toPx() }
        val timeline = guideTimeline(tick, data.latestProgrammeEnd())
        val timelineWidth = metrics.minuteWidth * timeline.minutes
        val scope = rememberCoroutineScope()
        state.pixelsPerMinute = minuteWidthPx
        LaunchedEffect(channels.map { it.id }, timeline) { state.initialise(data, channels, timeline) }
        LaunchedEffect(state.horizontalScroll.maxValue, timeline) {
            if (!state.needsInitialTimelineScroll(timeline) || state.horizontalScroll.maxValue == 0) return@LaunchedEffect
            withFrameNanos { }
            state.scrollToInitialTime(timeline, tick)
            state.markInitialTimelineScrollApplied(timeline)
        }

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp * layoutScale),
            border = BorderStroke(1.dp, GuideBorder),
            colors = CardDefaults.cardColors(containerColor = GuidePanel)
        ) {
            Column(Modifier.fillMaxSize()) {
                GuideTitle(data.language, layoutScale)
                TimelineHeader(data.language, state, timeline, timelineWidth, tick, metrics)
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val change = event.changes.firstOrNull() ?: return@onPointerEvent
                            val delta: Offset = change.scrollDelta
                            if (event.keyboardModifiers.isShiftPressed || abs(delta.x) > abs(delta.y)) {
                                val amount = if (abs(delta.x) > abs(delta.y)) delta.x else delta.y
                                scope.launch { state.horizontalScroll.scrollBy(amount * 64f) }
                                change.consume()
                            }
                        }
                ) {
                    if (channels.isEmpty()) {
                        Text(
                            tr(data.language, "channels.empty"),
                            color = GuideMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(state = state.verticalList, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(channels, key = { _, channel -> channel.id }) { _, channel ->
                                GuideChannelRow(data, channel, timeline, state, timelineWidth, metrics, onProgrammeClick)
                            }
                        }
                        CurrentTimeBodyLine(tick, timeline, state, metrics)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTitle(language: AppLanguage, scale: Float) {
    Text(
        tr(language, "epg.guide.title"),
        color = WukkiColors.textPrimary,
        fontSize = (28f * scale).sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 28.dp * scale, top = 25.dp * scale, bottom = 17.dp * scale)
    )
}

@Composable
private fun TimelineHeader(
    language: AppLanguage,
    state: EpgGuideState,
    timeline: GuideTimeline,
    timelineWidth: androidx.compose.ui.unit.Dp,
    tick: Long,
    metrics: GuideLayoutMetrics
) {
    Row(
        Modifier.fillMaxWidth().height(metrics.timelineHeight)
            .background(WukkiColors.backgroundRaised).border(BorderStroke(1.dp, GuideBorder))
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
        val instant = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val offset = metrics.minuteWidth * (index * HALF_HOUR_MINUTES)
        val isDayStart = instant.hour == 0 && instant.minute == 0
        Box(
            Modifier.offset(x = offset).width(if (isDayStart) 2.dp * metrics.scale else 1.dp)
                .fillMaxHeight().background(if (isDayStart) WukkiColors.focus else GuideBorder)
        )
        Text(
            if (isDayStart) instant.toLocalDate().dateLabel(language) else formatTime(timestamp),
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
private fun GuideChannelRow(
    data: GuideDataSource,
    channel: Channel,
    timeline: GuideTimeline,
    state: EpgGuideState,
    timelineWidth: androidx.compose.ui.unit.Dp,
    metrics: GuideLayoutMetrics,
    onProgrammeClick: (Channel, Programme) -> Unit
) {
    val programmes = data.programmesFor(channel, timeline.start, timeline.end)
    val rowFocused = state.focusedChannelId == channel.id
    Row(Modifier.fillMaxWidth().height(metrics.rowHeight).background(WukkiColors.backgroundRaised)) {
        Row(
            modifier = Modifier.width(metrics.channelColumnWidth).fillMaxHeight()
                .background(WukkiColors.navigationBackground).border(BorderStroke(1.dp, GuideBorder))
                .clickable { state.selectChannel(channel) }.padding(horizontal = 15.dp * metrics.scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                channel.tvgChno?.toString() ?: "–",
                color = WukkiColors.textPrimary,
                fontSize = (24f * metrics.scale).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(48.dp * metrics.scale)
            )
            Text(
                channel.name,
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (18f * metrics.scale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))
                .horizontalScroll(state.horizontalScroll, enabled = false)
        ) {
            Box(Modifier.requiredWidth(timelineWidth).fillMaxHeight()) {
                repeat(timeline.halfHourTickCount) { index ->
                    Box(
                        Modifier.offset(x = metrics.minuteWidth * index * HALF_HOUR_MINUTES)
                            .width(1.dp).fillMaxHeight().background(GuideBorder.copy(alpha = .7f))
                    )
                }
                programmes.forEach { programme ->
                    val clippedStart = max(programme.start, timeline.start)
                    val clippedEnd = min(programme.end, timeline.end)
                    val startMinute = (clippedStart - timeline.start) / 60_000f
                    val durationMinutes = ((clippedEnd - clippedStart) / 60_000f).coerceAtLeast(0.16f)
                    ProgrammeCell(
                        programme = programme,
                        language = data.language,
                        focused = rowFocused && state.focusedProgrammeKey == programme.key(),
                        scale = metrics.scale,
                        modifier = Modifier.offset(x = metrics.minuteWidth * startMinute)
                            .width(metrics.minuteWidth * durationMinutes)
                            .fillMaxHeight(),
                        onClick = {
                            state.selectProgramme(channel, programme)
                            onProgrammeClick(channel, programme)
                        }
                    )
                }
            }
            if (programmes.isEmpty()) Text(
                tr(data.language, "epg.none"),
                color = GuideMuted,
                fontSize = (15f * metrics.scale).sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp * metrics.scale)
            )
        }
    }
}

@Composable
private fun ProgrammeCell(
    programme: Programme,
    language: AppLanguage,
    focused: Boolean,
    scale: Float,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp * scale)
    val background = if (focused) {
        Modifier.background(WukkiBrushes.selectedSurface())
    } else {
        Modifier.background(GuideSurface)
    }
    Column(
        modifier = modifier.padding(1.dp).clip(shape).then(background)
            .border(if (focused) 2.dp else 1.dp, if (focused) WukkiColors.focus else GuideBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp * scale, vertical = 14.dp * scale)
    ) {
        Text(
            programme.displayTitle(language),
            color = WukkiColors.textPrimary,
            fontSize = (18f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(7.dp * scale))
        Text(
            "${formatTime(programme.start)} – ${formatTime(programme.end)}",
            color = GuideMuted,
            fontSize = (15f * scale).sp,
            maxLines = 1,
            overflow = TextOverflow.Clip
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
private fun CurrentTimeBodyLine(
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

private fun Programme.key(): String = "$channelId|$start|$end"
private fun Programme.middleTime(): Long = (start + (end - start) / 2).coerceAtLeast(start)
private fun List<Programme>.indexOfClosest(time: Long): Int =
    indices.minByOrNull { abs(this[it].start - time) } ?: 0

internal fun guideTimeline(now: Long, latestProgrammeEnd: Long?): GuideTimeline {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val lastProgrammeDay = latestProgrammeEnd?.takeIf { it > start }?.let { end ->
        Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
    }
    val end = (lastProgrammeDay ?: today).coerceAtLeast(today).plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    return GuideTimeline(start, end)
}

private val GuideTimeline.minutes: Float get() = (end - start) / 60_000f
private val GuideTimeline.halfHourTickCount: Int get() = (minutes / HALF_HOUR_MINUTES).toInt() + 1
private fun GuideTimeline.minutesFromStart(timestamp: Long): Float = (timestamp - start) / 60_000f

private fun LocalDate.dateLabel(language: AppLanguage): String = format(
    DateTimeFormatter.ofPattern(
        tr(language, "date.guide.pattern"),
        Localizer.locale(language)
    )
)
