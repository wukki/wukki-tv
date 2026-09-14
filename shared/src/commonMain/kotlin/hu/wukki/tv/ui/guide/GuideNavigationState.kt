package hu.wukki.tv.ui.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.wukki.tv.ui.components.platformGuideTime
import hu.wukki.tv.ui.components.platformStartOfDay
import hu.wukki.tv.ui.navigation.RemoteKey

enum class GuideFocusZone { HEADER, CHANNELS, PROGRAMMES }

enum class GuideHeaderAction(
    val labelKey: String,
) {
    NOW("epg.guide.now"),
    TONIGHT("epg.guide.tonight"),
    PREVIOUS_DAY("epg.guide.previousDay"),
    NEXT_DAY("epg.guide.nextDay"),
    ALL("channels.all"),
    FAVORITES("channels.favorites"),
}

class GuideNavigationState {
    var zone by mutableStateOf(GuideFocusZone.HEADER)
    var headerIndex by mutableIntStateOf(0)
    var favoritesOnly by mutableStateOf(false)
    val action: GuideHeaderAction get() = GuideHeaderAction.entries[headerIndex]

    fun moveFocus(
        key: RemoteKey,
        channelIndex: Int,
    ): Boolean =
        when (zone) {
            GuideFocusZone.HEADER -> {
                when (key) {
                    RemoteKey.LEFT -> headerIndex = (headerIndex - 1).coerceAtLeast(0)
                    RemoteKey.RIGHT -> headerIndex = (headerIndex + 1).coerceAtMost(GuideHeaderAction.entries.lastIndex)
                    RemoteKey.DOWN -> if (channelIndex >= 0) zone = GuideFocusZone.CHANNELS
                    else -> Unit
                }
                true
            }

            GuideFocusZone.CHANNELS -> {
                when (key) {
                    RemoteKey.LEFT -> true.also { zone = GuideFocusZone.HEADER }
                    RemoteKey.RIGHT, RemoteKey.CONFIRM -> true.also { zone = GuideFocusZone.PROGRAMMES }
                    RemoteKey.UP -> (channelIndex <= 0).also { if (it) zone = GuideFocusZone.HEADER }
                    else -> false
                }
            }

            GuideFocusZone.PROGRAMMES -> {
                (key == RemoteKey.UP && channelIndex <= 0).also { if (it) zone = GuideFocusZone.CHANNELS }
            }
        }

    fun targetTime(
        action: GuideHeaderAction,
        timeline: GuideTimeline,
        now: Long,
        selectedTime: Long,
    ): Long {
        val target =
            when (action) {
                GuideHeaderAction.NOW -> now
                GuideHeaderAction.TONIGHT -> platformGuideTime(now, 0, 20)
                GuideHeaderAction.PREVIOUS_DAY -> platformGuideTime(selectedTime, -1)
                GuideHeaderAction.NEXT_DAY -> platformGuideTime(selectedTime, 1)
                else -> selectedTime
            }
        return if (platformStartOfDay(target) !in timeline.start until timeline.end) selectedTime else target
    }
}
