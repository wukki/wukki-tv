package hu.wukki.tv

import android.content.Context
import androidx.work.BackoffPolicy
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
    private var scheduledFingerprints = emptyMap<BackgroundRefreshType, RefreshScheduleFingerprint>()

    @Synchronized
    fun sync(
        context: Context,
        state: AppState,
        now: Long = System.currentTimeMillis(),
    ) {
        val plans = refreshSchedulePlans(state, now)
        BackgroundRefreshType.entries.forEach { type ->
            val plan = plans[type]
            val fingerprint = plan?.fingerprint ?: RefreshScheduleFingerprint.disabled(type)
            val previous = scheduledFingerprints[type]
            if (previous != fingerprint) {
                schedule(context, type, plan, replaceExisting = previous != null)
                scheduledFingerprints = scheduledFingerprints + (type to fingerprint)
            }
        }
    }

    private fun schedule(
        context: Context,
        type: BackgroundRefreshType,
        plan: RefreshSchedulePlan?,
        replaceExisting: Boolean,
    ) {
        val manager = WorkManager.getInstance(context)
        val name = type.uniqueWorkName
        if (plan == null) {
            manager.cancelUniqueWork(name)
            return
        }
        val request =
            PeriodicWorkRequestBuilder<WukkiRefreshWorker>(plan.intervalHours, TimeUnit.HOURS)
                .setInitialDelay(plan.initialDelayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_TYPE to type.name))
                .build()
        val policy = if (replaceExisting) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.UPDATE
        manager.enqueueUniquePeriodicWork(name, policy, request)
    }

    internal const val KEY_TYPE = "refresh_type"
    internal const val MAX_ATTEMPTS = 5
    private const val PLAYLIST_WORK = "wukki_official_playlist_refresh"
    private const val EPG_WORK = "wukki_official_epg_refresh"
    private const val RETRY_BACKOFF_SECONDS = 30L

    private val BackgroundRefreshType.uniqueWorkName: String
        get() = if (this == BackgroundRefreshType.PLAYLIST) PLAYLIST_WORK else EPG_WORK
}

class WukkiRefreshWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val type = inputData.getString(AndroidRefreshScheduler.KEY_TYPE)?.let(::parseRefreshType) ?: return Result.failure()
        val report =
            try {
                AndroidAppGraph.runHeadlessRefresh(applicationContext, type)
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (_: Exception) {
                BackgroundRefreshResult(succeeded = false, failure = AppFailure.Unknown)
            }
        return when (workerDecision(report, runAttemptCount, AndroidRefreshScheduler.MAX_ATTEMPTS)) {
            WorkerDecision.SUCCESS -> Result.success()
            WorkerDecision.RETRY -> Result.retry()
            WorkerDecision.FAILURE -> Result.failure()
        }
    }
}

internal data class RefreshSchedulePlan(
    val intervalHours: Long,
    val initialDelayMillis: Long,
    val fingerprint: RefreshScheduleFingerprint,
)

internal data class RefreshScheduleFingerprint(
    val type: BackgroundRefreshType,
    val interval: RefreshInterval,
    val sourceVersions: List<Pair<String, Long?>>,
) {
    companion object {
        fun disabled(type: BackgroundRefreshType) = RefreshScheduleFingerprint(type, RefreshInterval.MANUAL, emptyList())
    }
}

internal fun refreshSchedulePlans(
    state: AppState,
    now: Long,
): Map<BackgroundRefreshType, RefreshSchedulePlan> {
    val normalized = state.normalized()
    val settings = normalized.settings
    val playlist = normalized.playlists.firstOrNull { it.location == OfficialWukkiSource.PLAYLIST_URL }
    val playlistPlan =
        settings.playlistRefresh.takeUnless { it == RefreshInterval.MANUAL }?.let { interval ->
            RefreshSchedulePlan(
                interval.hours.toLong(),
                playlistRefreshDelayMillis(playlist?.updatedAt ?: 0L, interval, now),
                RefreshScheduleFingerprint(
                    BackgroundRefreshType.PLAYLIST,
                    interval,
                    listOf(OfficialWukkiSource.PLAYLIST_ID to playlist?.updatedAt),
                ),
            )
        }
    val enabledSources = normalized.epgSources.filter { it.enabled }
    val epgPlan =
        settings.epgRefresh.takeUnless { it == RefreshInterval.MANUAL || enabledSources.isEmpty() }?.let { interval ->
            RefreshSchedulePlan(
                interval.hours.toLong(),
                epgRefreshDelayMillis(enabledSources, interval, now),
                RefreshScheduleFingerprint(
                    BackgroundRefreshType.EPG,
                    interval,
                    enabledSources.map { it.id to it.lastUpdatedAt },
                ),
            )
        }
    return buildMap {
        playlistPlan?.let { put(BackgroundRefreshType.PLAYLIST, it) }
        epgPlan?.let { put(BackgroundRefreshType.EPG, it) }
    }
}

internal enum class WorkerDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal fun workerDecision(
    result: BackgroundRefreshResult,
    runAttemptCount: Int,
    maxAttempts: Int,
): WorkerDecision {
    if (result.succeeded) return WorkerDecision.SUCCESS
    val retryable =
        when (val failure = result.failure) {
            AppFailure.NetworkUnavailable,
            AppFailure.Unknown,
            null,
            -> true

            is AppFailure.HttpError -> failure.status == 408 || failure.status == 429 || failure.status >= 500

            AppFailure.InvalidPlaylist,
            AppFailure.InvalidXmlTv,
            AppFailure.MissingEpgSource,
            AppFailure.InvalidRemoteUrl,
            AppFailure.ResponseTooLarge,
            -> false
        }
    return if (retryable && runAttemptCount + 1 < maxAttempts) WorkerDecision.RETRY else WorkerDecision.FAILURE
}

private fun parseRefreshType(value: String): BackgroundRefreshType? = BackgroundRefreshType.entries.firstOrNull { it.name == value }
