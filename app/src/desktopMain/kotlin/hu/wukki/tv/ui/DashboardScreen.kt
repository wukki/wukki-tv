package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DashboardPanel = AppBackground
private val DashboardMuted = Color(0xFF93A0B5)
private val DashboardBorder = Color(0xFF223047)
private val FocusPurple = Color(0xFF8B5CF6)

@Composable
fun DashboardScreen(
    model: WukkiModel,
    playbackController: PlaybackController,
    scope: CoroutineScope,
    tick: Long,
    activeSection: DashboardSection,
    guideState: EpgGuideState,
    onSectionChange: (DashboardSection) -> Unit,
    settingsSection: SettingsSection,
    onSettingsSectionChange: (SettingsSection) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF02080E), Color(0xFF07131F), Color(0xFF02070C))
            )
        )
    ) {
        val referenceScale = minOf(maxWidth.value / 1470f, maxHeight.value / 920f).coerceIn(.70f, 1.45f)
        val navigationWidth = (326.dp * referenceScale).coerceIn(220.dp, 430.dp)
        val contentPadding = (14.dp * referenceScale).coerceIn(8.dp, 20.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            SideNavigation(
                model = model,
                activeSection = activeSection,
                onSelect = onSectionChange,
                tick = tick,
                scale = referenceScale,
                modifier = Modifier.width(navigationWidth).fillMaxHeight()
            )
            when (activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(
                    model,
                    playbackController,
                    referenceScale,
                    Modifier.weight(1f).fillMaxHeight()
                )

                DashboardSection.GUIDE -> EpgGuideScreen(
                    model,
                    tick,
                    guideState,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding)
                )

                DashboardSection.CHANNELS -> ChannelScreen(
                    model,
                    tick,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding),
                    playbackController
                )

                DashboardSection.SETTINGS -> SettingsScreen(
                    model = model,
                    scope = scope,
                    selectedSection = settingsSection,
                    onSectionChange = onSettingsSectionChange,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding)
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            model.error?.let { DashboardMessage("Hiba: $it", Color(0xFFFFB4AB), Color(0xE65F1D22)) }
            model.status?.let { DashboardMessage(it, Color(0xFFB9F6CA), Color(0xE612352C)) }
        }
    }
}

@Composable
private fun LiveTvScreen(
    model: WukkiModel,
    playbackController: PlaybackController,
    scale: Float,
    modifier: Modifier
) {
    val channel = model.selectedChannel()
    Box(
        modifier = modifier.padding(
            top = (38.dp * scale).coerceAtLeast(20.dp),
            end = (36.dp * scale).coerceAtLeast(18.dp),
            bottom = (120.dp * scale).coerceAtLeast(54.dp)
        ).clip(RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp)))
            .background(Color.Black)
            .border(BorderStroke(1.dp, Color(0xFF172536)), RoundedCornerShape((8.dp * scale).coerceAtLeast(5.dp)))
    ) {
        if (channel == null) {
            Text(
                d(model, "Tölts be egy M3U playlistet a kezdéshez.", "Load an M3U playlist to get started."),
                color = DashboardMuted,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            EmbeddedVlcPlayer(playbackController, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ChannelScreen(model: WukkiModel, tick: Long, modifier: Modifier, playbackController: PlaybackController) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 190.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ChannelSearch(model, modifier = Modifier.fillMaxSize())
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ChannelDirectory(model, modifier = Modifier.weight(.59f).fillMaxHeight())
            ProgrammeInformation(model, tick, modifier = Modifier.weight(.41f).fillMaxHeight(), playbackController)
        }
    }
}

@Composable
private fun SideNavigation(
    model: WukkiModel,
    activeSection: DashboardSection,
    onSelect: (DashboardSection) -> Unit,
    tick: Long,
    scale: Float,
    modifier: Modifier
) {
    val entries = listOf(
        DashboardSection.LIVE to d(model, "Élő adás", "Live TV"),
        DashboardSection.GUIDE to d(model, "Műsorújság", "TV Guide"),
        DashboardSection.CHANNELS to d(model, "Csatornák", "Channels"),
        DashboardSection.SETTINGS to d(model, "Beállítások", "Settings")
    )
    Column(
        modifier = modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFF02080E), Color(0xFF07121C), Color(0xFF06101A)))
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 30.dp * scale, top = 40.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wukki", fontWeight = FontWeight.Black, fontSize = (36 * scale).sp, letterSpacing = (-1.2).sp)
            Spacer(Modifier.width(7.dp * scale))
            Text(
                "TV",
                color = Color.White,
                fontSize = (17 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(5.dp * scale))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7662F4), Color(0xFF4C35B8))))
                    .padding(horizontal = 7.dp * scale, vertical = 4.dp * scale)
            )
        }
        Spacer(Modifier.height(83.dp * scale))
        entries.forEach { (id, title) ->
            val selected = id == activeSection
            Row(
                modifier = Modifier.fillMaxWidth().height((76.dp * scale).coerceIn(54.dp, 94.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(
                            listOf(Color(0xFF5B43B7).copy(alpha = .82f), Color(0xFF2D235C).copy(alpha = .58f), Color.Transparent)
                        ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(id) }.padding(start = 40.dp * scale, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationIcon(
                    section = id,
                    color = if (selected) Color.White else Color(0xFFC7CED8),
                    modifier = Modifier.size((29.dp * scale).coerceIn(22.dp, 38.dp))
                )
                Spacer(Modifier.width(25.dp * scale))
                Text(
                    title,
                    color = if (selected) Color.White else Color(0xFFE6EAF2),
                    fontSize = (19 * scale).sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val date = Instant.ofEpochMilli(tick).atZone(ZoneId.systemDefault()).toLocalDate()
        val locale = if (model.settings.language == AppLanguage.HUNGARIAN) Locale.forLanguageTag("hu") else Locale.ENGLISH
        val datePattern = if (model.settings.language == AppLanguage.HUNGARIAN) "MMMM d., EEEE" else "MMMM d, EEEE"
        Column(modifier = Modifier.padding(start = 30.dp * scale, bottom = 70.dp * scale)) {
            Text(formatTime(tick), fontSize = (34 * scale).sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(5.dp * scale))
            Text(date.format(DateTimeFormatter.ofPattern(datePattern, locale)), color = DashboardMuted, fontSize = (15 * scale).sp)
        }
    }
}

@Composable
private fun NavigationIcon(section: DashboardSection, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * .075f)
        val inset = size.minDimension * .12f
        when (section) {
            DashboardSection.LIVE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(inset, size.height * .24f),
                    size = Size(size.width - inset * 2, size.height * .61f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .08f),
                    style = stroke
                )
                val play = Path().apply {
                    moveTo(size.width * .43f, size.height * .40f)
                    lineTo(size.width * .68f, size.height * .55f)
                    lineTo(size.width * .43f, size.height * .70f)
                    close()
                }
                drawPath(play, color)
                drawLine(color, Offset(size.width * .42f, size.height * .13f), Offset(size.width * .50f, size.height * .24f), stroke.width)
                drawLine(color, Offset(size.width * .58f, size.height * .13f), Offset(size.width * .50f, size.height * .24f), stroke.width)
            }

            DashboardSection.GUIDE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(inset, size.height * .20f),
                    size = Size(size.width - inset * 2, size.height * .68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .08f),
                    style = stroke
                )
                drawLine(color, Offset(inset, size.height * .40f), Offset(size.width - inset, size.height * .40f), stroke.width)
                repeat(2) { column ->
                    repeat(2) { row ->
                        drawCircle(
                            color,
                            radius = size.width * .045f,
                            center = Offset(size.width * (.36f + column * .28f), size.height * (.55f + row * .18f))
                        )
                    }
                }
                drawLine(color, Offset(size.width * .34f, size.height * .11f), Offset(size.width * .34f, size.height * .29f), stroke.width)
                drawLine(color, Offset(size.width * .66f, size.height * .11f), Offset(size.width * .66f, size.height * .29f), stroke.width)
            }

            DashboardSection.CHANNELS -> {
                repeat(3) { row ->
                    val y = size.height * (.27f + row * .24f)
                    drawCircle(color, radius = size.width * .055f, center = Offset(size.width * .20f, y))
                    drawLine(color, Offset(size.width * .34f, y), Offset(size.width * .84f, y), stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }

            DashboardSection.SETTINGS -> {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(color, radius = size.minDimension * .24f, center = center, style = stroke)
                drawCircle(color, radius = size.minDimension * .08f, center = center, style = stroke)
                repeat(8) { index ->
                    val angle = index * PI.toFloat() / 4f
                    val inner = size.minDimension * .31f
                    val outer = size.minDimension * .43f
                    drawLine(
                        color,
                        Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                        Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                        stroke.width,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelDirectory(model: WukkiModel, modifier: Modifier) {
    val channels = model.filteredChannels()
    DashboardCard(modifier, contentPadding = 12.dp) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            items(channels, key = { it.id }) { channel ->
                val selected = channel.id == model.selectedChannelId
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(if (selected) FocusPurple.copy(alpha = .23f) else Color.Transparent)
                        .clickable { model.selectChannel(channel.id) }.padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(channel.tvgChno?.toString() ?: "–", modifier = Modifier.width(25.dp), color = DashboardMuted)
                    DashboardLogo(model, channel, Modifier.size(32.dp).padding(end = 6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (model.settings.display.showChannelProgramme) Text(
                            model.currentProgram(channel)?.title ?: d(
                                model,
                                "EPG nincs",
                                "No EPG"
                            ), color = DashboardMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (channel.favorite) "♥" else "♡",
                        color = if (channel.favorite) FocusPurple else DashboardMuted,
                        modifier = Modifier.clickable { model.toggleFavorite(channel.id) })
                }
            }
        }
    }
}

@Composable
private fun ChannelSearch(model: WukkiModel, modifier: Modifier) {
    DashboardCard(modifier, contentPadding = 12.dp) {
        Text(d(model, "CSATORNÁK", "CHANNELS"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = model.category == null && !model.onlyFavorites,
                onClick = { model.category = null; model.onlyFavorites = false },
                label = { Text(d(model, "Minden", "All"), fontSize = 11.sp) },
            )
            FilterChip(
                selected = model.onlyFavorites,
                onClick = { model.onlyFavorites = !model.onlyFavorites },
                label = { Text(d(model, "Kedvencek", "Favorites"), fontSize = 11.sp) })
            model.categories().forEach { category ->
                FilterChip(
                    selected = model.category == category,
                    onClick = { model.category = if (model.category == category) null else category },
                    label = { Text(category, fontSize = 11.sp) })
            }
        }
        OutlinedTextField(
            value = model.query,
            onValueChange = { model.query = it },
            singleLine = true,
            label = { Text(d(model, "Keresés", "Search")) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProgrammeInformation(
    model: WukkiModel,
    tick: Long,
    modifier: Modifier,
    playbackController: PlaybackController
) {
    val channel = model.selectedChannel()
    val programme = channel?.let { model.currentProgram(it) }
    DashboardCard(modifier) {
        Spacer(Modifier.height(16.dp))
        if (channel == null) {
            Text(d(model, "Válassz csatornát.", "Select a channel."), color = DashboardMuted)
        } else {
            EmbeddedVlcPlayer(playbackController, modifier = Modifier.fillMaxWidth().height(200.dp))
            DashboardLogo(model, channel, Modifier.width(50.dp).height(50.dp))
            Spacer(Modifier.height(10.dp))
            Text(d(model, "Most", "Now"), color = DashboardMuted, fontSize = 11.sp)
            Text(programme?.title ?: channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(
                model,
                "EPG nincs",
                "No EPG"
            ), color = DashboardMuted, fontSize = 12.sp)
            programme?.let { ProgrammeProgress(it, tick) }
            Spacer(Modifier.height(10.dp))
            if (model.settings.display.showMiniGuide) Text(
                programme?.description ?: d(
                    model,
                    "A műsor leírása az XMLTV adataiból jelenik meg itt.",
                    "The XMLTV programme description appears here."
                ), color = DashboardMuted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.toggleFavorite(channel.id) }) {
                Text(
                    if (channel.favorite) "★ ${
                        d(
                            model,
                            "Kedvenc",
                            "Favorite"
                        )
                    }" else "☆ ${d(model, "Kedvencekhez adom", "Add to favorites")}"
                )
            }
        }
    }
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long) {
    val progress =
        ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        color = FocusPurple,
        trackColor = Color(0xFF27364B),
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
    )
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DashboardBorder),
        colors = CardDefaults.cardColors(containerColor = DashboardPanel)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}

@Composable
private fun DashboardMessage(message: String, color: Color, background: Color) {
    Text(
        message,
        color = color,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DashboardLogo(model: WukkiModel, channel: Channel, modifier: Modifier) {
    ChannelLogo(channel, modifier)
}

@Composable
private fun d(model: WukkiModel, hungarian: String, english: String): String =
    if (model.settings.language == AppLanguage.HUNGARIAN) hungarian else english
