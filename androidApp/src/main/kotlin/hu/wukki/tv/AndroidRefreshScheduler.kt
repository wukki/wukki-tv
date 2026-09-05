package hu.wukki.tv

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Schedules only the fixed Wukki playlist and header-managed EPG refreshes. */
object AndroidRefreshScheduler {
    private var playlistSchedule: Pair<RefreshInterval, Long>? = null

    @Synchronized
    fun sync(context: Context, state: AppState) {
        val settings = state.normalized().settings ?: AppSettings()
        val updatedAt = state.playlists.firstOrNull { it.location == OfficialWukkiSource.PLAYLIST_URL }?.updatedAt ?: 0L
        val scheduleKey = settings.playlistRefresh to updatedAt
        if (playlistSchedule != scheduleKey) {
            schedule(context, PLAYLIST_WORK, settings.playlistRefresh, RefreshType.PLAYLIST,
                playlistRefreshDelayMillis(updatedAt, settings.playlistRefresh, System.currentTimeMillis()))
            playlistSchedule = scheduleKey
        }
        schedule(context, EPG_WORK, settings.epgRefresh, RefreshType.EPG)
    }

    private fun schedule(context: Context, name: String, interval: RefreshInterval, type: RefreshType, delayMillis: Long = 0L) {
        val manager = WorkManager.getInstance(context)
        if (interval.hours <= 0) {
            manager.cancelUniqueWork(name)
            return
        }
        val request = PeriodicWorkRequestBuilder<WukkiRefreshWorker>(interval.hours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_TYPE to type.name))
            .build()
        manager.enqueueUniquePeriodicWork(name, if (type == RefreshType.PLAYLIST) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private const val PLAYLIST_WORK = "wukki_official_playlist_refresh"
    private const val EPG_WORK = "wukki_official_epg_refresh"
    internal const val KEY_TYPE = "refresh_type"
}

class WukkiRefreshWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val model = AndroidAppGraph.install(applicationContext).createModel()
        val refreshed = when (inputData.getString(AndroidRefreshScheduler.KEY_TYPE)) {
            RefreshType.PLAYLIST.name -> model.refreshDuePlaylist()
            RefreshType.EPG.name -> model.refreshOfficialEpg(showFeedback = false)
            else -> false
        }
        return if (refreshed) Result.success() else Result.retry()
    }
}

private enum class RefreshType { PLAYLIST, EPG }
