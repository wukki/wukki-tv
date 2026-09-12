package hu.wukki.tv.ui.app

import hu.wukki.tv.ui.navigation.DashboardSection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiLifecyclePolicyTest {
    @Test
    fun `background UI suspends clocks timeouts and Compose refreshes`() {
        DashboardSection.entries.forEach { section ->
            val policy = uiLifecyclePolicy(section, uiActive = false, foregroundRefreshesEnabled = true)

            assertFalse(policy.runClock)
            assertFalse(policy.runTimeouts)
            assertFalse(policy.runAutomaticRefreshes)
        }
    }

    @Test
    fun `settings avoids programme clock while interactive timeouts remain active`() {
        val policy = uiLifecyclePolicy(DashboardSection.SETTINGS, uiActive = true, foregroundRefreshesEnabled = true)

        assertFalse(policy.runClock)
        assertTrue(policy.runTimeouts)
        assertTrue(policy.runAutomaticRefreshes)
    }

    @Test
    fun `Android can delegate refresh scheduling while foreground screens keep their clock`() {
        val policy = uiLifecyclePolicy(DashboardSection.LIVE, uiActive = true, foregroundRefreshesEnabled = false)

        assertTrue(policy.runClock)
        assertTrue(policy.runTimeouts)
        assertFalse(policy.runAutomaticRefreshes)
    }
}
