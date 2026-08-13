package hu.wukki.tv.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ui.guide.DashboardSection
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class NavigationEntryUiState(val section: DashboardSection, val label: String)

data class SideNavigationUiState(
    val entries: List<NavigationEntryUiState>,
    val activeSection: DashboardSection,
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
            Row(
                modifier = Modifier.fillMaxWidth().height((76.dp * scale).coerceIn(54.dp, 94.dp))
                    .background(
                        if (selected) WukkiBrushes.navigationSelected()
                        else Brush.horizontalGradient(listOf(WukkiColors.transparent, WukkiColors.transparent))
                    )
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
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * .075f)
        val inset = size.minDimension * .12f
        when (section) {
            DashboardSection.LIVE -> {
                drawRoundRect(color, Offset(inset, size.height * .24f), Size(size.width - inset * 2, size.height * .61f), androidx.compose.ui.geometry.CornerRadius(size.width * .08f), stroke)
                val play = Path().apply { moveTo(size.width * .43f, size.height * .40f); lineTo(size.width * .68f, size.height * .55f); lineTo(size.width * .43f, size.height * .70f); close() }
                drawPath(play, color)
                drawLine(color, Offset(size.width * .42f, size.height * .13f), Offset(size.width * .50f, size.height * .24f), stroke.width)
                drawLine(color, Offset(size.width * .58f, size.height * .13f), Offset(size.width * .50f, size.height * .24f), stroke.width)
            }
            DashboardSection.GUIDE -> {
                drawRoundRect(color, Offset(inset, size.height * .20f), Size(size.width - inset * 2, size.height * .68f), androidx.compose.ui.geometry.CornerRadius(size.width * .08f), stroke)
                drawLine(color, Offset(inset, size.height * .40f), Offset(size.width - inset, size.height * .40f), stroke.width)
                repeat(2) { column -> repeat(2) { row -> drawCircle(color, size.width * .045f, Offset(size.width * (.36f + column * .28f), size.height * (.55f + row * .18f))) } }
                drawLine(color, Offset(size.width * .34f, size.height * .11f), Offset(size.width * .34f, size.height * .29f), stroke.width)
                drawLine(color, Offset(size.width * .66f, size.height * .11f), Offset(size.width * .66f, size.height * .29f), stroke.width)
            }
            DashboardSection.CHANNELS -> repeat(3) { row ->
                val y = size.height * (.27f + row * .24f)
                drawCircle(color, size.width * .055f, Offset(size.width * .20f, y))
                drawLine(color, Offset(size.width * .34f, y), Offset(size.width * .84f, y), stroke.width, cap = StrokeCap.Round)
            }
            DashboardSection.SETTINGS -> {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(color = color, radius = size.minDimension * .24f, center = center, style = stroke)
                drawCircle(color = color, radius = size.minDimension * .08f, center = center, style = stroke)
                repeat(8) { index ->
                    val angle = index * PI.toFloat() / 4f
                    drawLine(color, Offset(center.x + cos(angle) * size.minDimension * .31f, center.y + sin(angle) * size.minDimension * .31f), Offset(center.x + cos(angle) * size.minDimension * .43f, center.y + sin(angle) * size.minDimension * .43f), stroke.width, cap = StrokeCap.Round)
                }
            }
        }
    }
}
