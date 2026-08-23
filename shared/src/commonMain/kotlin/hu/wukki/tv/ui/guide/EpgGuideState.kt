package hu.wukki.tv.ui.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.platformStartOfDay
import hu.wukki.tv.ui.components.platformStartOfNextDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val HALF_HOUR_MINUTES = 30

class EpgGuideState internal constructor(
    val horizontalScroll: ScrollState,
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
    var guideOpenRequest by mutableIntStateOf(0)
        private set
    private var initialisedChannelIds by mutableStateOf<List<String>>(emptyList())
    private var initialisedTimelineStart by mutableStateOf<Long?>(null)
    private var initiallyScrolledTimelineStart by mutableStateOf<Long?>(null)

    fun selectProgramme(channel: Channel, programme: Programme) {
        focusedChannelId = channel.id
        focusedProgrammeKey = programme.guideKey()
        focusTime = programme.middleTime()
    }

    fun selectChannel(channel: Channel) {
        focusedChannelId = channel.id
        focusedProgrammeKey = null
    }

    fun focusCurrentProgramme(data: GuideDataSource, timeline: GuideTimeline, now: Long) {
        val channel = data.channels().firstOrNull { it.id == data.selectedChannelId }
            ?: data.channels().firstOrNull()
            ?: return
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, now, timeline)
        guideOpenRequest++
    }

    suspend fun applyGuideOpenFocus(data: GuideDataSource, channels: List<Channel>, timeline: GuideTimeline, now: Long) {
        if (guideOpenRequest == 0 || channels.isEmpty()) return
        val channelIndex = channels.indexOfFirst { it.id == focusedChannelId }.takeIf { it >= 0 } ?: return
        verticalList.scrollToItem(channelIndex)
        scrollToInitialTime(timeline, now)
        guideOpenRequest = 0
    }

    fun focusedProgramme(data: GuideDataSource, timeline: GuideTimeline): Pair<Channel, Programme>? {
        val channel = data.channels().firstOrNull { it.id == focusedChannelId } ?: return null
        val programmes = data.programmesFor(channel, timeline.start, timeline.end)
        val programme = programmes.firstOrNull { it.guideKey() == focusedProgrammeKey }
            ?: programmes.minByOrNull { abs(it.start - focusTime) }
            ?: return null
        return channel to programme
    }

    suspend fun initialise(data: GuideDataSource, channels: List<Channel>, timeline: GuideTimeline) {
        if (channels.isEmpty()) return
        val channelIds = channels.map { it.id }
        val shouldRestoreVerticalPosition = initialisedChannelIds != channelIds ||
            initialisedTimelineStart != timeline.start || focusedChannelId !in channelIds
        val channelIndex = channels.indexOfFirst { it.id == focusedChannelId }.takeIf { it >= 0 }
            ?: channels.indexOfFirst { it.id == data.selectedChannelId }.takeIf { it >= 0 }
            ?: 0
        val channel = channels[channelIndex]
        focusedChannelId = channel.id
        chooseProgrammeAt(data, channel, focusTime, timeline)
        if (shouldRestoreVerticalPosition) verticalList.scrollToItem(channelIndex)
        initialisedChannelIds = channelIds
        initialisedTimelineStart = timeline.start
    }

    fun needsInitialTimelineScroll(timeline: GuideTimeline): Boolean = initiallyScrolledTimelineStart != timeline.start

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

    private suspend fun moveProgramme(data: GuideDataSource, channels: List<Channel>, timeline: GuideTimeline, delta: Int) {
        val channel = channels.firstOrNull { it.id == focusedChannelId } ?: return
        val direction = delta.coerceIn(-1, 1)
        if (direction == 0) return
        val programmes = data.programmesFor(channel, timeline.start, timeline.end)
        if (programmes.isEmpty()) return
        val current = programmes.indexOfFirst { it.guideKey() == focusedProgrammeKey }.let { index ->
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
        focusedProgrammeKey = programme?.guideKey()
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
    val vertical = rememberLazyListState()
    return remember(horizontal, vertical) { EpgGuideState(horizontal, vertical) }
}

internal fun Programme.guideKey(): String = "$channelId|$start|$end"
private fun Programme.middleTime(): Long = (start + (end - start) / 2).coerceAtLeast(start)
private fun List<Programme>.indexOfClosest(time: Long): Int = indices.minByOrNull { index -> abs(this[index].start - time) } ?: 0

internal fun guideTimeline(now: Long, latestProgrammeEnd: Long?): GuideTimeline {
    val start = platformStartOfDay(now)
    val lastProgrammeInstant = latestProgrammeEnd?.takeIf { it > start }?.minus(1)
    val end = platformStartOfNextDay(lastProgrammeInstant ?: now)
    return GuideTimeline(start, end)
}
