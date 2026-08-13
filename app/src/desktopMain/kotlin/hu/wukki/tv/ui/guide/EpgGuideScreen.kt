package hu.wukki.tv.ui.guide

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.Localizer
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

enum class DashboardSection { LIVE, GUIDE, CHANNELS, SETTINGS }

private const val MINUTES_PER_DAY = 24 * 60
private const val HALF_HOUR_MINUTES = 30
private val ReferenceMinuteWidth = 6.dp
private val ReferenceChannelColumnWidth = 160.dp
private val ReferenceGuideRowHeight = 112.dp
private const val REFERENCE_GUIDE_WIDTH = 1116f
private const val REFERENCE_GUIDE_HEIGHT = 892f
private val GuidePanel = Color(0xFF050D16)
private val GuideSurface = Color(0xFF101D2B)
private val GuideBorder = Color(0xFF1E2D3B)
private val GuideMuted = Color(0xFFB1BBC9)
private val GuideAccent = Color(0xFF8B5CF6)

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
    var selectedDay by mutableStateOf(LocalDate.now())
        private set
    var focusedChannelId by mutableStateOf<String?>(null)
        private set
    var focusedProgrammeKey by mutableStateOf<String?>(null)
        private set
    var focusMinuteOfDay by mutableIntStateOf(currentMinuteOfDay())
        private set
    var pixelsPerMinute: Float = 6f
    var viewportWidthPx: Int = 0

    fun selectDay(day: LocalDate) {
        selectedDay = day
        focusedProgrammeKey = null
    }

    fun selectProgramme(channel: Channel, programme: Programme) {
        focusedChannelId = channel.id
        focusedProgrammeKey = programme.key()
        focusMinuteOfDay = programme.middleMinute(selectedDay)
    }

    fun selectChannel(channel: Channel) {
        focusedChannelId = channel.id
        focusedProgrammeKey = null
    }

    suspend fun initialise(data: GuideDataSource, channels: List<Channel>) {
        if (channels.isEmpty()) return
        val channelIndex = channels.indexOfFirst { it.id == focusedChannelId }
            .takeIf { it >= 0 }
            ?: channels.indexOfFirst { it.id == data.selectedChannelId }.takeIf { it >= 0 }
            ?: 0
        val channel = channels[channelIndex]
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, focusMinuteOfDay)
        verticalList.scrollToItem(channelIndex)
    }

    fun handleKey(key: Key, data: GuideDataSource, scope: CoroutineScope, days: List<LocalDate>): Boolean {
        val channels = data.channels()
        return when (key) {
            Key.DirectionUp, Key.PageUp -> true.also { scope.launch { moveChannel(data, channels, -1) } }
            Key.DirectionDown, Key.PageDown -> true.also { scope.launch { moveChannel(data, channels, 1) } }
            Key.DirectionLeft -> true.also { scope.launch { moveProgramme(data, channels, days, -1) } }
            Key.DirectionRight -> true.also { scope.launch { moveProgramme(data, channels, days, 1) } }
            Key.Enter, Key.NumPadEnter -> true
            else -> false
        }
    }

    suspend fun scrollToInitialTime() {
        scrollToMinute((currentMinuteOfDay() - HALF_HOUR_MINUTES).coerceAtLeast(0), animate = false)
    }

    suspend fun changeDay(day: LocalDate, data: GuideDataSource, channels: List<Channel>) {
        selectDay(day)
        channels.firstOrNull { it.id == focusedChannelId }?.let { chooseProgrammeAt(data, it, focusMinuteOfDay) }
    }

    private suspend fun moveChannel(data: GuideDataSource, channels: List<Channel>, delta: Int) {
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.id == focusedChannelId }.let { if (it < 0) 0 else it }
        val target = (current + delta).coerceIn(0, channels.lastIndex)
        val channel = channels[target]
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, focusMinuteOfDay)
        verticalList.animateScrollToItem(target)
    }

    private suspend fun moveProgramme(
        data: GuideDataSource,
        channels: List<Channel>,
        days: List<LocalDate>,
        delta: Int
    ) {
        val channel = channels.firstOrNull { it.id == focusedChannelId } ?: return
        val direction = delta.coerceIn(-1, 1)
        if (direction == 0) return
        val (dayStart, dayEnd) = selectedDay.bounds()
        val programmes = data.programmesFor(channel, dayStart, dayEnd)
        if (programmes.isEmpty()) {
            moveToAdjacentDay(data, channel, days, direction)
            return
        }
        val current = programmes.indexOfFirst { it.key() == focusedProgrammeKey }.let { index ->
            if (index >= 0) index else programmes.indexOfClosest(focusMinuteOfDay, selectedDay)
        }
        val target = current + direction
        if (target !in programmes.indices) {
            moveToAdjacentDay(data, channel, days, direction)
            return
        }
        val programme = programmes[target]
        selectProgramme(channel, programme)
        ensureVisible(programme, dayStart)
    }

    private suspend fun moveToAdjacentDay(
        data: GuideDataSource,
        channel: Channel,
        days: List<LocalDate>,
        direction: Int
    ) {
        val currentDayIndex = days.indexOf(selectedDay)
        if (currentDayIndex < 0) return
        val targetDay = days.getOrNull(currentDayIndex + direction) ?: return
        selectDay(targetDay)
        val (dayStart, dayEnd) = targetDay.bounds()
        val programmes = data.programmesFor(channel, dayStart, dayEnd)
        val programme = if (direction > 0) programmes.firstOrNull() else programmes.lastOrNull()
        if (programme != null) {
            selectProgramme(channel, programme)
            ensureVisible(programme, dayStart)
        } else {
            focusMinuteOfDay = if (direction > 0) 0 else MINUTES_PER_DAY - 1
            scrollToMinute(if (direction > 0) 0 else MINUTES_PER_DAY, animate = true)
        }
    }

    private fun chooseProgrammeAt(data: GuideDataSource, channel: Channel, minute: Int) {
        val (dayStart, dayEnd) = selectedDay.bounds()
        val programmes = data.programmesFor(channel, dayStart, dayEnd)
        val timestamp = selectedDay.timestampAtMinute(minute)
        val programme = programmes.firstOrNull { timestamp in it.start until it.end }
            ?: programmes.minByOrNull { abs(it.start - timestamp) }
        focusedProgrammeKey = programme?.key()
    }

    private suspend fun ensureVisible(programme: Programme, dayStart: Long) {
        if (viewportWidthPx <= 0) return
        val left = ((max(programme.start, dayStart) - dayStart) / 60_000f * pixelsPerMinute).roundToInt()
        val right = ((programme.end - dayStart) / 60_000f * pixelsPerMinute).roundToInt()
        val margin = (HALF_HOUR_MINUTES * pixelsPerMinute).roundToInt()
        val current = horizontalScroll.value
        val target = when {
            left < current + margin -> left - margin
            right > current + viewportWidthPx - margin -> right - viewportWidthPx + margin
            else -> current
        }.coerceIn(0, horizontalScroll.maxValue)
        horizontalScroll.animateScrollTo(target)
    }

    private suspend fun scrollToMinute(minute: Int, animate: Boolean) {
        val target = (minute * pixelsPerMinute).roundToInt().coerceIn(0, horizontalScroll.maxValue)
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
fun EpgGuideScreen(data: GuideDataSource, tick: Long, state: EpgGuideState, modifier: Modifier = Modifier) {
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
        val dayWidth = metrics.minuteWidth * MINUTES_PER_DAY
        val today = Instant.ofEpochMilli(tick).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = remember(today) { guideDays(tick) }
        val (dayStart, dayEnd) = state.selectedDay.bounds()
        val scope = rememberCoroutineScope()
        var initialScrollApplied by remember(state) { mutableStateOf(false) }

        state.pixelsPerMinute = minuteWidthPx
        LaunchedEffect(channels.map { it.id }) { state.initialise(data, channels) }
        LaunchedEffect(today) {
            if (state.selectedDay !in days) state.changeDay(today, data, channels)
        }
        LaunchedEffect(state.horizontalScroll.maxValue) {
            if (initialScrollApplied || state.horizontalScroll.maxValue == 0) return@LaunchedEffect
            withFrameNanos { }
            state.scrollToInitialTime()
            initialScrollApplied = true
        }

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp * layoutScale),
            border = BorderStroke(1.dp, GuideBorder),
            colors = CardDefaults.cardColors(containerColor = GuidePanel)
        ) {
            Column(Modifier.fillMaxSize()) {
                GuideDateHeader(
                    language = data.language,
                    days = days,
                    state = state,
                    scale = layoutScale,
                    onSelect = { day -> scope.launch { state.changeDay(day, data, channels) } }
                )
                TimelineHeader(state, dayWidth, tick, metrics)
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
                                GuideChannelRow(data, channel, dayStart, dayEnd, state, dayWidth, metrics)
                            }
                        }
                        CurrentTimeBodyLine(tick, state.selectedDay, state, metrics)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDateHeader(
    language: AppLanguage,
    days: List<LocalDate>,
    state: EpgGuideState,
    scale: Float,
    onSelect: (LocalDate) -> Unit
) {
    val selectedIndex = days.indexOf(state.selectedDay).coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color(0xFF050E17), Color(0xFF06111B)))
        )
    ) {
        Text(
            tr(language, "epg.guide.title"),
            color = Color.White,
            fontSize = (28f * scale).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 28.dp * scale, top = 25.dp * scale, bottom = 17.dp * scale)
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp * scale).padding(horizontal = 18.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuideDayArrow(
                symbol = "‹",
                enabled = selectedIndex > 0,
                scale = scale,
                onClick = { days.getOrNull(selectedIndex - 1)?.let(onSelect) }
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEachIndexed { index, day ->
                    GuideDayTab(
                        language = language,
                        day = day,
                        index = index,
                        selected = day == state.selectedDay,
                        scale = scale,
                        onClick = { onSelect(day) }
                    )
                }
            }
            GuideDayArrow(
                symbol = "›",
                enabled = selectedIndex < days.lastIndex,
                scale = scale,
                onClick = { days.getOrNull(selectedIndex + 1)?.let(onSelect) }
            )
        }
        Spacer(Modifier.height(20.dp * scale))
    }
}

@Composable
private fun GuideDayTab(
    language: AppLanguage,
    day: LocalDate,
    index: Int,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    val background = if (selected) {
        Modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFF3D3268), Color(0xFF2A214D)))
        )
    } else {
        Modifier.background(Color.Transparent)
    }
    Column(
        modifier = Modifier.width(184.dp * scale).fillMaxHeight()
            .clip(RoundedCornerShape(10.dp * scale)).then(background)
            .clickable(onClick = onClick).padding(vertical = 15.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            day.dayLabel(language, index),
            color = Color.White,
            fontSize = (20f * scale).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp * scale))
        Text(day.dateLabel(language), color = GuideMuted, fontSize = (14f * scale).sp, maxLines = 1)
    }
}

@Composable
private fun GuideDayArrow(symbol: String, enabled: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(52.dp * scale).fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = if (enabled) Color.White else GuideMuted.copy(alpha = .28f),
            fontSize = (42f * scale).sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun TimelineHeader(
    state: EpgGuideState,
    dayWidth: androidx.compose.ui.unit.Dp,
    tick: Long,
    metrics: GuideLayoutMetrics
) {
    Row(
        Modifier.fillMaxWidth().height(metrics.timelineHeight)
            .background(Color(0xFF08131E)).border(BorderStroke(1.dp, GuideBorder))
    ) {
        Box(
            Modifier.width(metrics.channelColumnWidth).fillMaxHeight().background(Color(0xFF07111B)),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("·", color = GuideMuted, fontSize = (18f * metrics.scale).sp, modifier = Modifier.padding(start = 28.dp * metrics.scale))
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))
                .onSizeChanged { state.viewportWidthPx = it.width }
                .horizontalScroll(state.horizontalScroll)
        ) {
            Box(Modifier.requiredWidth(dayWidth).fillMaxHeight()) {
                repeat(49) { index ->
                    val minute = index * HALF_HOUR_MINUTES
                    Box(Modifier.offset(x = metrics.minuteWidth * minute).width(1.dp).fillMaxHeight().background(GuideBorder))
                    if (index < 48) {
                        Text(
                            "%02d:%02d".format(minute / 60, minute % 60),
                            color = GuideMuted,
                            fontSize = (15f * metrics.scale).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.offset(
                                x = metrics.minuteWidth * minute - 40.dp * metrics.scale,
                                y = 29.dp * metrics.scale
                            ).width(80.dp * metrics.scale)
                        )
                    }
                }
                CurrentTimeHeaderIndicator(tick, state.selectedDay, metrics)
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    data: GuideDataSource,
    channel: Channel,
    dayStart: Long,
    dayEnd: Long,
    state: EpgGuideState,
    dayWidth: androidx.compose.ui.unit.Dp,
    metrics: GuideLayoutMetrics
) {
    val programmes = data.programmesFor(channel, dayStart, dayEnd)
    val rowFocused = state.focusedChannelId == channel.id
    Row(Modifier.fillMaxWidth().height(metrics.rowHeight).background(Color(0xFF08131E))) {
        Row(
            modifier = Modifier.width(metrics.channelColumnWidth).fillMaxHeight()
                .background(Color(0xFF07111B)).border(BorderStroke(1.dp, GuideBorder))
                .clickable { state.selectChannel(channel) }.padding(horizontal = 15.dp * metrics.scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                channel.tvgChno?.toString() ?: "–",
                color = Color.White,
                fontSize = (24f * metrics.scale).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(48.dp * metrics.scale)
            )
            Text(
                channel.name,
                color = Color.White,
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
            Box(Modifier.requiredWidth(dayWidth).fillMaxHeight()) {
                repeat(49) { index ->
                    Box(
                        Modifier.offset(x = metrics.minuteWidth * index * HALF_HOUR_MINUTES)
                            .width(1.dp).fillMaxHeight().background(GuideBorder.copy(alpha = .7f))
                    )
                }
                programmes.forEach { programme ->
                    val clippedStart = max(programme.start, dayStart)
                    val clippedEnd = min(programme.end, dayEnd)
                    val startMinute = (clippedStart - dayStart) / 60_000f
                    val durationMinutes = ((clippedEnd - clippedStart) / 60_000f).coerceAtLeast(0.16f)
                    ProgrammeCell(
                        programme = programme,
                        language = data.language,
                        focused = rowFocused && state.focusedProgrammeKey == programme.key(),
                        scale = metrics.scale,
                        modifier = Modifier.offset(x = metrics.minuteWidth * startMinute)
                            .width(metrics.minuteWidth * durationMinutes)
                            .fillMaxHeight(),
                        onClick = { state.selectProgramme(channel, programme) }
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
        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF41366F), Color(0xFF292044))))
    } else {
        Modifier.background(GuideSurface)
    }
    Column(
        modifier = modifier.padding(1.dp).clip(shape).then(background)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFA277FF) else GuideBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp * scale, vertical = 14.dp * scale)
    ) {
        Text(
            programme.displayTitle(language),
            color = Color.White,
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
private fun CurrentTimeHeaderIndicator(now: Long, day: LocalDate, metrics: GuideLayoutMetrics) {
    if (day != now.localDate()) return
    val minute = now.minuteOfDay()
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
                .clip(RoundedCornerShape(9.dp * metrics.scale)).background(Color(0xFF3A2D68)),
            contentAlignment = Alignment.Center
        ) {
            Text(formatTime(now), color = Color.White, fontSize = (18f * metrics.scale).sp, fontWeight = FontWeight.SemiBold)
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
    day: LocalDate,
    state: EpgGuideState,
    metrics: GuideLayoutMetrics
) {
    if (day != now.localDate()) return
    val density = LocalDensity.current
    val channelWidthPx = with(density) { metrics.channelColumnWidth.toPx() }
    val x = channelWidthPx + now.minuteOfDay() * state.pixelsPerMinute - state.horizontalScroll.value
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
private fun Programme.middleMinute(day: LocalDate): Int {
    val middle = ((start + end) / 2).coerceIn(day.bounds().first, day.bounds().second - 1)
    return Instant.ofEpochMilli(middle).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
}
private fun List<Programme>.indexOfClosest(minute: Int, day: LocalDate): Int {
    val timestamp = day.timestampAtMinute(minute)
    return indices.minByOrNull { abs(this[it].start - timestamp) } ?: 0
}
private fun LocalDate.bounds(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return atStartOfDay(zone).toInstant().toEpochMilli() to plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
private fun LocalDate.timestampAtMinute(minute: Int): Long = atStartOfDay(ZoneId.systemDefault()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
internal fun guideDays(now: Long): List<LocalDate> {
    val today = now.localDate()
    return List(3) { today.plusDays(it.toLong()) }
}
private fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
private fun Long.minuteOfDay(): Float = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).let {
    it.hour * 60f + it.minute + it.second / 60f
}
private fun currentMinuteOfDay(): Int = java.time.ZonedDateTime.now().let { it.hour * 60 + it.minute }
private fun LocalDate.dayLabel(language: AppLanguage, index: Int): String = if (index == 0) {
    tr(language, "epg.today")
} else {
    format(DateTimeFormatter.ofPattern(tr(language, "date.guide.weekday.pattern"), Localizer.locale(language)))
}

private fun LocalDate.dateLabel(language: AppLanguage): String = format(
    DateTimeFormatter.ofPattern(
        tr(language, "date.guide.pattern"),
        Localizer.locale(language)
    )
)
