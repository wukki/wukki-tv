package hu.wukki.tv.ui.guide

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

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
        val minuteWidthPx = with(LocalDensity.current) { metrics.minuteWidth.toPx() }
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
        LaunchedEffect(state.guideOpenRequest, state.horizontalScroll.maxValue) {
            if (state.guideOpenRequest == 0 || state.horizontalScroll.maxValue == 0) return@LaunchedEffect
            withFrameNanos { }
            state.applyGuideOpenFocus(data, channels, timeline, tick)
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
                        .pointerInput(state.horizontalScroll, scope) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val change = event.changes.firstOrNull() ?: continue
                                    val delta: Offset = change.scrollDelta
                                    if (event.keyboardModifiers.isShiftPressed || abs(delta.x) > abs(delta.y)) {
                                        val amount = if (abs(delta.x) > abs(delta.y)) delta.x else delta.y
                                        scope.launch { state.horizontalScroll.scrollBy(amount * 64f) }
                                        change.consume()
                                    }
                                }
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
