package hu.wukki.tv.ui.guide

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.ChannelLogo
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun GuideChannelRow(
    data: GuideDataSource,
    channel: Channel,
    timeline: GuideTimeline,
    viewport: GuideViewport,
    state: EpgGuideState,
    metrics: GuideLayoutMetrics,
    onProgrammeClick: (Channel, Programme) -> Unit
) {
    val allProgrammes = data.programmesFor(channel, timeline.start, timeline.end)
    val programmes = allProgrammes.filter { programme -> programme.end > viewport.from && programme.start < viewport.to }
    val rowFocused = state.focusedChannelId == channel.id
    val density = LocalDensity.current
    val scrollPx = state.horizontalScroll.value
    Row(Modifier.fillMaxWidth().height(metrics.rowHeight).background(WukkiColors.backgroundRaised)) {
        Row(
            modifier = Modifier.width(metrics.channelColumnWidth).fillMaxHeight()
                .background(WukkiColors.navigationBackground).border(1.dp, GuideBorder)
                .clickable { state.selectChannel(channel) }.padding(horizontal = 15.dp * metrics.scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                channel.tvgChno?.toString() ?: "–",
                color = WukkiColors.textPrimary,
                fontSize = (24f * metrics.scale).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(36.dp * metrics.scale)
            )
            if (data.showLogos) {
                ChannelLogo(
                    channel = channel,
                    language = data.language,
                    modifier = Modifier.padding(end = 8.dp * metrics.scale).size(38.dp * metrics.scale)
                )
            }
            else {
                Text(
                    channel.name,
                    color = WukkiColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (18f * metrics.scale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(0.dp)).clipToBounds()
        ) {
            Canvas(Modifier.matchParentSize()) {
                viewport.tickIndices.forEach { index ->
                    val x = index * HALF_HOUR_MINUTES * state.pixelsPerMinute - scrollPx
                    if (x in 0f..size.width) {
                        drawLine(
                            color = GuideBorder.copy(alpha = .7f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
            programmes.forEach { programme ->
                val clippedStart = max(programme.start, timeline.start)
                val clippedEnd = min(programme.end, timeline.end)
                val startMinute = (clippedStart - timeline.start) / 60_000f
                val durationMinutes = ((clippedEnd - clippedStart) / 60_000f).coerceAtLeast(0.16f)
                val startPx = startMinute * state.pixelsPerMinute - scrollPx
                ProgrammeCell(
                    programme = programme,
                    language = data.language,
                    focused = rowFocused && state.focusedProgrammeKey == programme.guideKey(),
                    scale = metrics.scale,
                    modifier = Modifier.offset(x = with(density) { startPx.toDp() })
                        .width(metrics.minuteWidth * durationMinutes)
                        .fillMaxHeight(),
                    onClick = {
                        state.selectProgramme(channel, programme)
                        onProgrammeClick(channel, programme)
                    }
                )
            }
            if (allProgrammes.isEmpty()) Text(
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
    val background = if (focused) Modifier.background(WukkiBrushes.selectedSurface()) else Modifier.background(GuideSurface)
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
