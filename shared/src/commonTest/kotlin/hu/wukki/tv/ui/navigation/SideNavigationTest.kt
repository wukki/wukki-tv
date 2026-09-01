package hu.wukki.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SideNavigationTest {
    private val entries = DashboardSection.entries.map { section ->
        NavigationEntryUiState(section, section.name)
    }

    @Test
    fun `active section is highlighted while navigation has no focus`() {
        val state = SideNavigationUiState(
            entries = entries,
            activeSection = DashboardSection.LIVE
        )

        assertEquals(DashboardSection.LIVE, state.highlightedSection)
    }

    @Test
    fun `focused section is the only highlight while choosing a destination`() {
        val state = SideNavigationUiState(
            entries = entries,
            activeSection = DashboardSection.LIVE,
            focusedSection = DashboardSection.SETTINGS
        )

        assertEquals(DashboardSection.SETTINGS, state.highlightedSection)
    }
}
