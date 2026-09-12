package hu.wukki.tv.ui.app

import hu.wukki.tv.ui.navigation.DashboardSection

internal data class UiLifecyclePolicy(
    val runClock: Boolean,
    val runTimeouts: Boolean,
    val runAutomaticRefreshes: Boolean,
)

internal fun uiLifecyclePolicy(
    section: DashboardSection,
    uiActive: Boolean,
    foregroundRefreshesEnabled: Boolean,
): UiLifecyclePolicy =
    UiLifecyclePolicy(
        runClock = uiActive && section != DashboardSection.SETTINGS,
        runTimeouts = uiActive,
        runAutomaticRefreshes = uiActive && foregroundRefreshesEnabled,
    )
