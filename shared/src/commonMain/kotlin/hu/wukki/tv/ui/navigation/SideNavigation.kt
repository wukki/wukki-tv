package hu.wukki.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors

data class NavigationEntryUiState(val section: DashboardSection, val label: String)

data class SideNavigationUiState(
    val entries: List<NavigationEntryUiState>,
    val activeSection: DashboardSection,
    val focusedSection: DashboardSection? = null
)

/** Horizontal dashboard navigation shared by desktop and Android. */
@Composable
fun TopNavigation(
    state: SideNavigationUiState,
    scale: Float,
    onSelect: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth().heightIn(min = 60.dp, max = 90.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height((72.dp * scale).coerceIn(60.dp, 90.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WukkiTvBrand(
                scale = scale,
                modifier = Modifier.weight(1f).height(48.dp)
            )
            state.entries.forEach { entry ->
                TopNavigationItem(
                    entry = entry,
                    selected = entry.section == state.activeSection || entry.section == state.focusedSection,
                    scale = scale,
                    onClick = { onSelect(entry.section) },
                    modifier = Modifier.weight(1f).height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun WukkiTvBrand(scale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Wukki", fontWeight = FontWeight.Black, fontSize = (36f * scale).sp, letterSpacing = (-1.2).sp, maxLines = 1)
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
}

@Composable
private fun TopNavigationItem(
    entry: NavigationEntryUiState,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.padding(horizontal = 3.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp * scale),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIcon(entry.section, Modifier.size((23.dp * scale).coerceIn(18.dp, 30.dp)))
            Spacer(Modifier.width((7.dp * scale).coerceIn(4.dp, 10.dp)))
            Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = (14f * scale).sp)
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
