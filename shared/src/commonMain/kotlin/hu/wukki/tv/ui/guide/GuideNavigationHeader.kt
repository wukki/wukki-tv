package hu.wukki.tv.ui.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.platformGuideDateLabel
import hu.wukki.tv.ui.components.tr

@Composable
internal fun GuideNavigationHeader(
    language: AppLanguage,
    state: EpgGuideState,
    scale: Float,
    onAction: (GuideHeaderAction) -> Unit,
) {
    Text(
        platformGuideDateLabel(language, state.focusTime, "EEE, MMM d"),
        color = WukkiColors.textPrimary,
        modifier = Modifier.padding(horizontal = 28.dp * scale),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GuideHeaderAction.entries.forEach { action ->
            GuideNavigationButton(language, state, action, onAction)
        }
    }
}

@Composable
private fun GuideNavigationButton(
    language: AppLanguage,
    state: EpgGuideState,
    action: GuideHeaderAction,
    onAction: (GuideHeaderAction) -> Unit,
) {
    val focused = state.navigation.zone == GuideFocusZone.HEADER && state.navigation.action == action
    val selected =
        when (action) {
            GuideHeaderAction.ALL -> !state.navigation.favoritesOnly
            GuideHeaderAction.FAVORITES -> state.navigation.favoritesOnly
            else -> false
        }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    OutlinedButton(
        onClick = {
            state.navigation.zone = GuideFocusZone.HEADER
            onAction(action)
        },
        modifier = Modifier.bringIntoViewRequester(bringIntoView),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) WukkiColors.focus else GuideBorder),
    ) {
        Text(tr(language, action.labelKey), color = if (selected) WukkiColors.focus else WukkiColors.textPrimary)
    }
}
