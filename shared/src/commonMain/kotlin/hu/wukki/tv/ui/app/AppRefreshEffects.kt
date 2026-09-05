package hu.wukki.tv.ui.app

import hu.wukki.tv.WukkiModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun AutomaticRefreshEffects(model: WukkiModel) {
    LaunchedEffect(model.settings.playlistRefresh, model.officialPlaylist.updatedAt) {
        val hours = model.settings.playlistRefresh.hours
        if (hours > 0) {
            while (true) {
                delay(model.nextPlaylistRefreshDelayMillis())
                if (!model.refreshDuePlaylist()) {
                    delay(hours * 60L * 60L * 1000L)
                }
            }
        }
    }

    val epgRefreshSources = model.epgSources.map { Triple(it.id, it.enabled, it.lastUpdatedAt) }
    LaunchedEffect(model.settings.epgRefresh, epgRefreshSources) {
        val interval = model.settings.epgRefresh
        if (interval.hours > 0) {
            while (true) {
                val allDueRefreshesSucceeded = model.refreshDueEpgSources(interval)
                val waitMillis = if (allDueRefreshesSucceeded) {
                    model.nextEpgRefreshDelayMillis(interval)
                } else {
                    interval.hours * 60L * 60L * 1000L
                }
                delay(waitMillis.coerceAtLeast(1_000L))
            }
        }
    }
}
