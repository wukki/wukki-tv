package hu.wukki.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Material navigation controls with the product's desktop brand lockup. */
@Composable
fun SideNavigation(
    state: SideNavigationUiState,
    scale: Float,
    onSelect: (DashboardSection) -> Unit,
    expandedDesktop: Boolean = false,
    showCompactBrand: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (expandedDesktop) {
        ExpandedDesktopNavigation(state, scale, onSelect, modifier)
    } else {
        CompactNavigation(state, scale, onSelect, showCompactBrand, modifier)
    }
}

@Composable
private fun ExpandedDesktopNavigation(
    state: SideNavigationUiState,
    scale: Float,
    onSelect: (DashboardSection) -> Unit,
    modifier: Modifier
) {
    Surface(modifier = modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxHeight()) {
            WukkiTvBrand(scale)
            Spacer(Modifier.height(83.dp * scale))
            state.entries.forEach { entry ->
                NavigationDrawerItem(
                    label = { Text(entry.label, maxLines = 1, fontSize = (19f * scale).sp) },
                    selected = entry.section == state.activeSection || entry.section == state.focusedSection,
                    onClick = { onSelect(entry.section) },
                    icon = { NavigationIcon(entry.section, Modifier.size((29.dp * scale).coerceIn(22.dp, 38.dp))) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp * scale, vertical = 2.dp * scale)
                )
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(start = 30.dp * scale, bottom = 70.dp * scale)) {
                Text(state.timeLabel, fontSize = (34f * scale).sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(5.dp * scale))
                Text(state.dateLabel, fontSize = (15f * scale).sp)
            }
        }
    }
}

@Composable
private fun WukkiTvBrand(scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 30.dp * scale, top = 40.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Wukki", fontWeight = FontWeight.Black, fontSize = (36f * scale).sp, letterSpacing = (-1.2).sp)
        Spacer(Modifier.width(7.dp * scale))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(5.dp * scale)).background(WukkiBrushes.brandAccent())
                .padding(horizontal = 7.dp * scale, vertical = 4.dp * scale),
            contentAlignment = Alignment.Center
        ) {
            Text("TV", color = WukkiColors.textPrimary, fontSize = (17f * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactNavigation(
    state: SideNavigationUiState,
    scale: Float,
    onSelect: (DashboardSection) -> Unit,
    showBrand: Boolean,
    modifier: Modifier
) {
    NavigationRail(modifier = modifier.fillMaxHeight()) {
        if (showBrand) {
            Text("W", modifier = Modifier.padding(top = 14.dp), fontWeight = FontWeight.Black, fontSize = 26.sp)
            Spacer(Modifier.size(18.dp))
        }
        state.entries.forEach { entry ->
            NavigationRailItem(
                selected = entry.section == state.activeSection || entry.section == state.focusedSection,
                onClick = { onSelect(entry.section) },
                icon = { NavigationIcon(entry.section, Modifier.size((26.dp * scale).coerceIn(20.dp, 32.dp))) },
                label = { Text(entry.label, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary
                ),
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun NavigationIcon(section: DashboardSection, modifier: Modifier) {
    val icon = when (section) {
        DashboardSection.LIVE -> Icons.Outlined.LiveTv
        DashboardSection.GUIDE -> Icons.Outlined.CalendarMonth
        DashboardSection.CHANNELS -> Icons.AutoMirrored.Outlined.FormatListBulleted
        DashboardSection.SETTINGS -> Icons.Outlined.Settings
    }
    Icon(imageVector = icon, contentDescription = null, modifier = modifier)
}
