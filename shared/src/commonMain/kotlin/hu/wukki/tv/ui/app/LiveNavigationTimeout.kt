package hu.wukki.tv.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.LiveNavigationVisibilityState
import kotlinx.coroutines.delay

@Composable
fun LiveNavigationTimeout(
    section: DashboardSection,
    state: LiveNavigationVisibilityState,
    enabled: Boolean,
    onTimeout: () -> Unit,
) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(section, state.visible, state.interactionSequence, enabled) {
        if (enabled && section == DashboardSection.LIVE && state.visible) {
            delay(5_000L)
            currentOnTimeout()
        }
    }
}
