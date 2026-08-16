package hu.wukki.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors

data class NavigationEntryUiState(val section: DashboardSection, val label: String)

data class SideNavigationUiState(
    val entries: List<NavigationEntryUiState>,
    val activeSection: DashboardSection,
    val focusedSection: DashboardSection? = null,
    val timeLabel: String,
    val dateLabel: String
)

/** Pure main navigation: it has no knowledge of the model or the playback runtime. */
@Composable
fun SideNavigation(
    state: SideNavigationUiState,
    scale: Float,
    onSelect: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(WukkiBrushes.navigationBackground())
    ) {
        Row(
            modifier = Modifier.padding(start = 30.dp * scale, top = 40.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text("Wukki", color = WukkiColors.textPrimary, fontWeight = FontWeight.Black, fontSize = (36 * scale).sp, letterSpacing = (-1.2).sp)
            Spacer(Modifier.width(7.dp * scale))
            androidx.compose.material3.Text(
                "TV", color = WukkiColors.textPrimary, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(5.dp * scale))
                    .background(WukkiBrushes.accent())
                    .padding(horizontal = 7.dp * scale, vertical = 4.dp * scale)
            )
        }
        Spacer(Modifier.height(83.dp * scale))
        state.entries.forEach { entry ->
            val selected = entry.section == state.activeSection
            val focused = entry.section == state.focusedSection
            Row(
                modifier = Modifier.fillMaxWidth().height((76.dp * scale).coerceIn(54.dp, 94.dp))
                    .background(
                        if (selected) WukkiBrushes.navigationSelected()
                        else Brush.horizontalGradient(listOf(WukkiColors.transparent, WukkiColors.transparent))
                    )
                    .border(if (focused) 2.dp else 0.dp, if (focused) WukkiColors.focus else WukkiColors.transparent)
                    .clickable { onSelect(entry.section) }.padding(start = 40.dp * scale, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationIcon(entry.section, if (selected) WukkiColors.textPrimary else WukkiColors.textSecondary, Modifier.size((29.dp * scale).coerceIn(22.dp, 38.dp)))
                Spacer(Modifier.width(25.dp * scale))
                androidx.compose.material3.Text(entry.label, color = if (selected) WukkiColors.textPrimary else WukkiColors.textSecondary, fontSize = (19 * scale).sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(start = 30.dp * scale, bottom = 70.dp * scale)) {
            androidx.compose.material3.Text(state.timeLabel, color = WukkiColors.textPrimary, fontSize = (34 * scale).sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(5.dp * scale))
            androidx.compose.material3.Text(state.dateLabel, color = WukkiColors.textMuted, fontSize = (15 * scale).sp)
        }
    }
}

@Composable
private fun NavigationIcon(section: DashboardSection, color: Color, modifier: Modifier) {
    val icon = when (section) {
        DashboardSection.LIVE -> Icons.Outlined.LiveTv
        DashboardSection.GUIDE -> Icons.Outlined.CalendarMonth
        DashboardSection.CHANNELS -> Icons.AutoMirrored.Outlined.FormatListBulleted
        DashboardSection.SETTINGS -> Icons.Outlined.Settings
    }
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = modifier)
}
