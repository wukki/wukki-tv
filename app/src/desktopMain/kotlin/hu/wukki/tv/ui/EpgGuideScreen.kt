package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollbarAdapter
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DashboardSection { LIVE, GUIDE, CHANNELS }

private const val MINUTES_PER_DAY = 24 * 60
private const val HALF_HOUR_MINUTES = 30
private val MinuteWidth = 6.dp
private val ChannelColumnWidth = 132.dp
private val GuideRowHeight = 72.dp
private val GuidePanel = Color(0xFF050D16)
private val GuideSurface = Color(0xFF101D2B)
private val GuideBorder = Color(0xFF223047)
private val GuideMuted = Color(0xFF9BA7BA)
private val GuideAccent = Color(0xFF8B5CF6)

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

    suspend fun initialise(model: WukkiModel, channels: List<Channel>) {
        if (channels.isEmpty()) return
        val channelIndex = channels.indexOfFirst { it.id == focusedChannelId }
            .takeIf { it >= 0 }
            ?: channels.indexOfFirst { it.id == model.selectedChannelId }.takeIf { it >= 0 }
            ?: 0
        val channel = channels[channelIndex]
        focusedChannelId = channel.id
        chooseProgrammeAt(model, channel, focusMinuteOfDay)
        verticalList.scrollToItem(channelIndex)
    }

    fun handleKey(key: Key, model: WukkiModel, scope: CoroutineScope): Boolean {
        val channels = model.guideChannels()
        return when (key) {
            Key.DirectionUp, Key.PageUp -> true.also { scope.launch { moveChannel(model, channels, -1) } }
            Key.DirectionDown, Key.PageDown -> true.also { scope.launch { moveChannel(model, channels, 1) } }
            Key.DirectionLeft -> true.also { scope.launch { moveProgramme(model, channels, -1) } }
            Key.DirectionRight -> true.also { scope.launch { moveProgramme(model, channels, 1) } }
            Key.Enter -> true
            else -> false
        }
    }

    suspend fun scrollToInitialTime() {
        scrollToMinute((currentMinuteOfDay() - HALF_HOUR_MINUTES).coerceAtLeast(0), animate = false)
    }

    suspend fun changeDay(day: LocalDate, model: WukkiModel, channels: List<Channel>) {
        selectDay(day)
        channels.firstOrNull { it.id == focusedChannelId }?.let { chooseProgrammeAt(model, it, focusMinuteOfDay) }
    }

    private suspend fun moveChannel(model: WukkiModel, channels: List<Channel>, delta: Int) {
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.id == focusedChannelId }.let { if (it < 0) 0 else it }
        val target = (current + delta).coerceIn(0, channels.lastIndex)
        val channel = channels[target]
        focusedChannelId = channel.id
        chooseProgrammeAt(model, channel, focusMinuteOfDay)
        verticalList.animateScrollToItem(target)
    }

    private suspend fun moveProgramme(model: WukkiModel, channels: List<Channel>, delta: Int) {
        val channel = channels.firstOrNull { it.id == focusedChannelId } ?: return
        val (dayStart, dayEnd) = selectedDay.bounds()
        val programmes = model.programmesFor(channel, dayStart, dayEnd)
        if (programmes.isEmpty()) return
        val current = programmes.indexOfFirst { it.key() == focusedProgrammeKey }.let { index ->
            if (index >= 0) index else programmes.indexOfClosest(focusMinuteOfDay, selectedDay)
        }
        val target = (current + delta).coerceIn(0, programmes.lastIndex)
        val programme = programmes[target]
        selectProgramme(channel, programme)
        ensureVisible(programme, dayStart)
    }

    private fun chooseProgrammeAt(model: WukkiModel, channel: Channel, minute: Int) {
        val (dayStart, dayEnd) = selectedDay.bounds()
        val programmes = model.programmesFor(channel, dayStart, dayEnd)
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
fun EpgGuideScreen(model: WukkiModel, tick: Long, state: EpgGuideState, modifier: Modifier = Modifier) {
    val channels = model.guideChannels()
    val density = LocalDensity.current
    val minuteWidthPx = with(density) { MinuteWidth.toPx() }
    val dayWidth = MinuteWidth * MINUTES_PER_DAY
    val today = Instant.ofEpochMilli(tick).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = remember(today) { List(3) { today.plusDays(it.toLong()) } }
    val (dayStart, dayEnd) = state.selectedDay.bounds()
    val scope = rememberCoroutineScope()
    var initialScrollApplied by remember(state) { mutableStateOf(false) }

    state.pixelsPerMinute = minuteWidthPx
    LaunchedEffect(channels.map { it.id }) { state.initialise(model, channels) }
    LaunchedEffect(today) {
        if (state.selectedDay !in days) state.changeDay(today, model, channels)
    }
    LaunchedEffect(state.horizontalScroll.maxValue) {
        if (initialScrollApplied || state.horizontalScroll.maxValue == 0) return@LaunchedEffect
        withFrameNanos { }
        state.scrollToInitialTime()
        initialScrollApplied = true
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, GuideBorder),
        colors = CardDefaults.cardColors(containerColor = GuidePanel)
    ) {
        Column(Modifier.fillMaxSize()) {
            GuideDateHeader(model, days, state, onSelect = { day -> scope.launch { state.changeDay(day, model, channels) } })
            TimelineHeader(state, dayWidth, tick)
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
                    Text(guideText(model, "Nincs megjeleníthető csatorna.", "No channels to display."), color = GuideMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(state = state.verticalList, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { _, channel ->
                            GuideChannelRow(model, channel, dayStart, dayEnd, tick, state, dayWidth)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(state.verticalList),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(ChannelColumnWidth))
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(state.horizontalScroll),
                    modifier = Modifier.weight(1f).height(10.dp)
                )
            }
            GuideFooter(model, state, days) { day -> scope.launch { state.changeDay(day, model, channels) } }
        }
    }
}

@Composable
private fun GuideDateHeader(model: WukkiModel, days: List<LocalDate>, state: EpgGuideState, onSelect: (LocalDate) -> Unit) {
    val selectedIndex = days.indexOf(state.selectedDay).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            guideText(model, "MŰSORÚJSÁG", "TV GUIDE"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(ChannelColumnWidth - 18.dp)
        )
        Text("‹", color = GuideMuted, fontSize = 30.sp, modifier = Modifier.clickable { days.getOrNull(selectedIndex - 1)?.let(onSelect) }.padding(8.dp))
        days.forEachIndexed { index, day ->
            val selected = day == state.selectedDay
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GuideAccent.copy(alpha = .38f) else Color.Transparent)
                    .clickable { onSelect(day) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(day.dayLabel(model, index), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                Text(day.dateLabel(model), color = GuideMuted, fontSize = 11.sp)
            }
        }
        Text("›", color = GuideMuted, fontSize = 30.sp, modifier = Modifier.clickable { days.getOrNull(selectedIndex + 1)?.let(onSelect) }.padding(8.dp))
    }
}

@Composable
private fun TimelineHeader(state: EpgGuideState, dayWidth: androidx.compose.ui.unit.Dp, tick: Long) {
    Row(Modifier.fillMaxWidth().height(46.dp).background(Color(0xFF0B1622))) {
        Box(Modifier.width(ChannelColumnWidth).fillMaxHeight().background(Color(0xFF07111B)))
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))
                .onSizeChanged { state.viewportWidthPx = it.width }
                .horizontalScroll(state.horizontalScroll)
        ) {
            Box(Modifier.requiredWidth(dayWidth).fillMaxHeight()) {
                repeat(48) { index ->
                    val minute = index * HALF_HOUR_MINUTES
                    Box(Modifier.offset(x = MinuteWidth * minute).width(1.dp).fillMaxHeight().background(GuideBorder))
                    Text(
                        "%02d:%02d".format(minute / 60, minute % 60),
                        color = GuideMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(x = MinuteWidth * minute - 27.dp, y = 14.dp).width(54.dp)
                    )
                }
                CurrentTimeLine(tick, state.selectedDay, header = true)
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    model: WukkiModel,
    channel: Channel,
    dayStart: Long,
    dayEnd: Long,
    tick: Long,
    state: EpgGuideState,
    dayWidth: androidx.compose.ui.unit.Dp
) {
    val programmes = model.programmesFor(channel, dayStart, dayEnd)
    val rowFocused = state.focusedChannelId == channel.id
    Row(Modifier.fillMaxWidth().height(GuideRowHeight).background(Color(0xFF08131E))) {
        Row(
            modifier = Modifier.width(ChannelColumnWidth).fillMaxHeight()
                .background(if (rowFocused) GuideAccent.copy(alpha = .16f) else Color(0xFF07111B))
                .clickable { state.selectChannel(channel) }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(channel.tvgChno?.toString() ?: "–", color = GuideMuted, modifier = Modifier.width(30.dp))
            Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp))
                .horizontalScroll(state.horizontalScroll, enabled = false)
        ) {
            Box(Modifier.requiredWidth(dayWidth).fillMaxHeight()) {
                repeat(48) { index ->
                    Box(Modifier.offset(x = MinuteWidth * index * HALF_HOUR_MINUTES).width(1.dp).fillMaxHeight().background(GuideBorder.copy(alpha = .55f)))
                }
                programmes.forEach { programme ->
                    val clippedStart = max(programme.start, dayStart)
                    val clippedEnd = min(programme.end, dayEnd)
                    val startMinute = (clippedStart - dayStart) / 60_000f
                    val durationMinutes = ((clippedEnd - clippedStart) / 60_000f).coerceAtLeast(0.16f)
                    ProgrammeCell(
                        programme = programme,
                        focused = rowFocused && state.focusedProgrammeKey == programme.key(),
                        modifier = Modifier.offset(x = MinuteWidth * startMinute).width(MinuteWidth * durationMinutes)
                            .fillMaxHeight(),
                        onClick = { state.selectProgramme(channel, programme) }
                    )
                }
                CurrentTimeLine(tick, state.selectedDay, header = false)
            }
            if (programmes.isEmpty()) Text(
                guideText(model, "EPG nincs", "No EPG"), color = GuideMuted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun ProgrammeCell(programme: Programme, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.padding(1.dp).clip(RoundedCornerShape(5.dp))
            .background(if (focused) GuideAccent.copy(alpha = .45f) else GuideSurface)
            .then(if (focused) Modifier.border(2.dp, GuideAccent, RoundedCornerShape(5.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(programme.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${formatTime(programme.start)} – ${formatTime(programme.end)}", color = GuideMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CurrentTimeLine(now: Long, day: LocalDate, header: Boolean) {
    if (day != LocalDate.now()) return
    val minute = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute + it.second / 60f }
    Box(
        Modifier.offset(x = MinuteWidth * minute).width(if (header) 3.dp else 2.dp).fillMaxHeight()
            .background(Color(0xFFFFB800))
    )
}

@Composable
private fun GuideFooter(model: WukkiModel, state: EpgGuideState, days: List<LocalDate>, onSelectDay: (LocalDate) -> Unit) {
    val selectedIndex = days.indexOf(state.selectedDay).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF07111B)).padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text("● -24 ${guideText(model, "óra", "hours")}", color = Color(0xFFFF5C50), fontSize = 12.sp, modifier = Modifier.clickable { days.getOrNull(selectedIndex - 1)?.let(onSelectDay) })
        Text("● +24 ${guideText(model, "óra", "hours")}", color = Color(0xFF55D967), fontSize = 12.sp, modifier = Modifier.clickable { days.getOrNull(selectedIndex + 1)?.let(onSelectDay) })
        Text("● ${guideText(model, "Most", "Now")}", color = Color(0xFFFFB800), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("↑↓ ${guideText(model, "Csatorna", "Channel")}   ←→ ${guideText(model, "Műsor", "Programme")}", color = GuideMuted, fontSize = 11.sp)
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
private fun currentMinuteOfDay(): Int = java.time.ZonedDateTime.now().let { it.hour * 60 + it.minute }
@Composable private fun guideText(model: WukkiModel, hu: String, en: String): String = if (model.settings.language == AppLanguage.HUNGARIAN) hu else en
@Composable
private fun LocalDate.dayLabel(model: WukkiModel, index: Int): String = if (index == 0) {
    guideText(model, "Ma", "Today")
} else {
    format(DateTimeFormatter.ofPattern("EEEE", model.guideLocale()))
}

@Composable
private fun LocalDate.dateLabel(model: WukkiModel): String = format(
    DateTimeFormatter.ofPattern(
        if (model.settings.language == AppLanguage.HUNGARIAN) "MMMM d." else "MMM d",
        model.guideLocale()
    )
)

private fun WukkiModel.guideLocale(): Locale =
    if (settings.language == AppLanguage.HUNGARIAN) Locale.forLanguageTag("hu") else Locale.ENGLISH
