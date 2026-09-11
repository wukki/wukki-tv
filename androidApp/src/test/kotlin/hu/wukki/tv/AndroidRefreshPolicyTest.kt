package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidRefreshPolicyTest {
    @Test
    fun `manual schedules no background work`() {
        assertTrue(refreshSchedulePlans(state(), NOW).isEmpty())
    }

    @Test
    fun `plans align first run to persisted due times`() {
        val state =
            state(
                settings = AppSettings(playlistRefresh = RefreshInterval.SIX_HOURS, epgRefresh = RefreshInterval.TWELVE_HOURS),
                playlistUpdatedAt = NOW - HOUR,
                epgUpdatedAt = null,
            )

        val plans = refreshSchedulePlans(state, NOW)

        assertEquals(5 * HOUR, plans.getValue(BackgroundRefreshType.PLAYLIST).initialDelayMillis)
        assertEquals(0L, plans.getValue(BackgroundRefreshType.EPG).initialDelayMillis)
    }

    @Test
    fun `unrelated state edits keep fingerprints stable while refresh timestamps change only their job`() {
        val original =
            state(
                settings = AppSettings(playlistRefresh = RefreshInterval.SIX_HOURS, epgRefresh = RefreshInterval.SIX_HOURS),
                playlistUpdatedAt = NOW,
                epgUpdatedAt = NOW,
            )
        val edited = original.copy(settings = original.settings.copy(display = DisplaySettings(uiScale = 1.2f)))
        val refreshedEpg = original.copy(epgSources = original.epgSources.map { it.copy(lastUpdatedAt = NOW + HOUR) })

        val originalPlans = refreshSchedulePlans(original, NOW)
        val editedPlans = refreshSchedulePlans(edited, NOW + 10_000)
        val refreshedPlans = refreshSchedulePlans(refreshedEpg, NOW + HOUR)

        assertEquals(
            originalPlans.mapValues { it.value.fingerprint },
            editedPlans.mapValues { it.value.fingerprint },
        )
        assertEquals(
            originalPlans.getValue(BackgroundRefreshType.PLAYLIST).fingerprint,
            refreshedPlans.getValue(BackgroundRefreshType.PLAYLIST).fingerprint,
        )
        assertNotEquals(
            originalPlans.getValue(BackgroundRefreshType.EPG).fingerprint,
            refreshedPlans.getValue(BackgroundRefreshType.EPG).fingerprint,
        )
    }

    @Test
    fun `worker retries only transient failures and stops at attempt limit`() {
        assertEquals(WorkerDecision.SUCCESS, workerDecision(BackgroundRefreshResult(true), 0, 5))
        assertEquals(
            WorkerDecision.RETRY,
            workerDecision(BackgroundRefreshResult(false, AppFailure.NetworkUnavailable), 0, 5),
        )
        assertEquals(
            WorkerDecision.RETRY,
            workerDecision(BackgroundRefreshResult(false, AppFailure.HttpError(503)), 1, 5),
        )
        assertEquals(
            WorkerDecision.FAILURE,
            workerDecision(BackgroundRefreshResult(false, AppFailure.HttpError(404)), 0, 5),
        )
        assertEquals(
            WorkerDecision.FAILURE,
            workerDecision(BackgroundRefreshResult(false, AppFailure.InvalidXmlTv), 0, 5),
        )
        assertEquals(
            WorkerDecision.FAILURE,
            workerDecision(BackgroundRefreshResult(false, AppFailure.NetworkUnavailable), 4, 5),
        )
    }

    @Test
    fun `disabled or missing EPG source cancels only EPG work`() {
        val configured =
            state(
                settings = AppSettings(playlistRefresh = RefreshInterval.SIX_HOURS, epgRefresh = RefreshInterval.SIX_HOURS),
                epgEnabled = false,
            )
        val plans = refreshSchedulePlans(configured, NOW)

        assertFalse(BackgroundRefreshType.EPG in plans)
        assertNull(plans[BackgroundRefreshType.EPG])
        assertTrue(BackgroundRefreshType.PLAYLIST in plans)
    }

    private fun state(
        settings: AppSettings = AppSettings(),
        playlistUpdatedAt: Long = 0,
        epgUpdatedAt: Long? = null,
        epgEnabled: Boolean = true,
    ) = AppState(
        playlists =
            listOf(
                PlaylistDefinition(
                    OfficialWukkiSource.PLAYLIST_ID,
                    OfficialWukkiSource.PLAYLIST_NAME,
                    OfficialWukkiSource.PLAYLIST_URL,
                    PlaylistSource.URL,
                    playlistUpdatedAt,
                ),
            ),
        epgSources =
            listOf(
                EpgSource(
                    "epg",
                    "EPG",
                    "https://example.test/epg.xml",
                    enabled = epgEnabled,
                    lastUpdatedAt = epgUpdatedAt,
                ),
            ),
        settings = settings,
    )

    private companion object {
        const val HOUR = 60L * 60L * 1_000L
        const val NOW = 1_800_000_000_000L
    }
}
