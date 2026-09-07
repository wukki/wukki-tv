package hu.wukki.tv.ui.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import hu.wukki.tv.*
import hu.wukki.tv.ui.guide.EpgGuideState
import hu.wukki.tv.ui.navigation.*
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSessionControllerTest {
    private fun controller(session: AppSessionState, scope: CoroutineScope, model: WukkiModel) =
        AppSessionController(session, model, scope, EpgGuideState(ScrollState(0), LazyListState()),
            model.guideDataSource(), true, false, {})

    private fun model() = WukkiModel(AppState(), RemoteTextLoader { error("No network expected") },
        XmlTvParser { emptyList() }, {})

    @Test
    fun `dashboard callbacks and remote keys update the same session`() = runBlocking {
        val session = AppSessionState(false)
        val model = model()
        val actions = controller(session, this, model)
        actions.callbacks.onSectionChange(DashboardSection.SETTINGS)
        actions.callbacks.onSettingsSectionChange(SettingsSection.PLAYBACK)
        actions.callbacks.onSettingsOptionFocus(PlaybackSettingsOption.VOLUME.ordinal)
        assertTrue(actions.dispatchRemote(AppRemoteKey(remote = RemoteKey.LEFT)))
        assertEquals(95, model.settings.playback.volume)
        assertEquals(PlaybackSettingsOption.VOLUME, session.settingsNavigation.option)
        assertTrue(actions.handleBackNavigation())
        assertEquals(null, session.settingsNavigation.section)
        assertEquals(DashboardSection.SETTINGS, session.activeSection)
        assertTrue(actions.handleBackNavigation())
        assertEquals(DashboardSection.LIVE, session.activeSection)
    }

    @Test
    fun `controller recreation keeps session and touch overlay shares remote back state`() =
        runBlocking {
            val session = AppSessionState(true)
            val model = model()
            val first = controller(session, this, model)
            first.dispatchRemote(AppRemoteKey(digit = "1"))
            val recreated = controller(session, this, model)
            recreated.dispatchRemote(AppRemoteKey(digit = "2"))
            assertEquals("12", session.channelNumberInput)
            session.programmeOverlayVisible = true
            recreated.liveVideoGestures.onTap()
            assertFalse(session.programmeOverlayVisible)
            recreated.liveVideoGestures.onTap()
            assertEquals(1, session.overlayRequest)
            session.programmeOverlayVisible = true
            assertTrue(recreated.handleBackNavigation())
            assertFalse(session.programmeOverlayVisible)
            assertFalse(recreated.handleBackNavigation())
            assertEquals("", AppSessionState(true).channelNumberInput)
        }
}
