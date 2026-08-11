package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
                    model = model,
                    tick = tick,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding),
                    playbackController = playbackController,
                    scale = referenceScale.coerceAtMost(1f)
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
private fun ChannelScreen(
    model: WukkiModel,
    tick: Long,
    modifier: Modifier,
    playbackController: PlaybackController,
    scale: Float
) {
    var searchOpen by remember { mutableStateOf(false) }
    val screenFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus() else screenFocusRequester.requestFocus()
    }
    DisposableEffect(Unit) {
        onDispose { model.query = "" }
    }

    Column(
        modifier = modifier.focusRequester(screenFocusRequester).focusable(),
        verticalArrangement = Arrangement.spacedBy(12.dp * scale)
    ) {
        ChannelHeader(
            model = model,
            searchOpen = searchOpen,
            scale = scale,
            searchFocusRequester = searchFocusRequester,
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                model.query = ""
                searchOpen = false
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale)
        ) {
            ChannelDirectory(model, tick, scale, modifier = Modifier.weight(.62f).fillMaxHeight())
            ProgrammeInformation(
                model = model,
                tick = tick,
                scale = scale,
                modifier = Modifier.weight(.38f).fillMaxHeight(),
                playbackController = playbackController
            )
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
private fun ChannelHeader(
    model: WukkiModel,
    searchOpen: Boolean,
    scale: Float,
    searchFocusRequester: FocusRequester,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            d(model, "CSATORNÁK", "CHANNELS"),
            color = Color.White,
            fontSize = (28f * scale).sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(15.dp * scale))
        if (searchOpen) {
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp * scale)
            ) {
                OutlinedTextField(
                    value = model.query,
                    onValueChange = { model.query = it },
                    singleLine = true,
                    placeholder = { Text(d(model, "Csatorna keresése", "Search channels"), color = DashboardMuted) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = (15f * scale).sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusPurple,
                        unfocusedBorderColor = DashboardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = FocusPurple
                    ),
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(searchFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                onCloseSearch()
                                true
                            } else {
                                false
                            }
                        }
                )
                ChannelHeaderIcon(close = true, scale = scale, onClick = onCloseSearch)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp * scale)
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scale)
                ) {
                    ChannelFilterTab(
                        label = d(model, "Minden", "All"),
                        selected = model.category == null && !model.onlyFavorites,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = false
                        }
                    )
                    ChannelFilterTab(
                        label = d(model, "Kedvencek", "Favorites"),
                        selected = model.onlyFavorites,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = true
                        }
                    )
                    model.categories().forEach { category ->
                        ChannelFilterTab(
                            label = category,
                            selected = model.category == category && !model.onlyFavorites,
                            scale = scale,
                            onClick = {
                                model.onlyFavorites = false
                                model.category = category
                            }
                        )
                    }
                }
                ChannelHeaderIcon(close = false, scale = scale, onClick = onOpenSearch)
            }
        }
    }
}

@Composable
private fun ChannelFilterTab(label: String, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val background = if (selected) {
        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF443671), Color(0xFF2D2450))))
    } else {
        Modifier.background(Color.Transparent)
    }
    Box(
        modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp * scale)).then(background)
            .clickable(onClick = onClick).padding(horizontal = 16.dp * scale),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFC2CAD5),
            fontSize = (15f * scale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ChannelHeaderIcon(close: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp * scale).clip(RoundedCornerShape(9.dp * scale))
            .background(Color(0xFF0D1926)).border(1.dp, DashboardBorder, RoundedCornerShape(9.dp * scale))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp * scale)) {
            val strokeWidth = size.minDimension * .09f
            if (close) {
                drawLine(Color.White, Offset(size.width * .22f, size.height * .22f), Offset(size.width * .78f, size.height * .78f), strokeWidth)
                drawLine(Color.White, Offset(size.width * .78f, size.height * .22f), Offset(size.width * .22f, size.height * .78f), strokeWidth)
            } else {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension * .28f,
                    center = Offset(size.width * .43f, size.height * .42f),
                    style = Stroke(strokeWidth)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .64f, size.height * .64f),
                    end = Offset(size.width * .86f, size.height * .86f),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

@Composable
private fun ChannelDirectory(model: WukkiModel, tick: Long, scale: Float, modifier: Modifier) {
    val channels = model.filteredChannels()
    val listState = rememberLazyListState()
    val rowHeight = (88.dp * scale).coerceAtLeast(66.dp)

    LaunchedEffect(model.selectedChannelId, channels.map { it.id }) {
        val selectedIndex = channels.indexOfFirst { it.id == model.selectedChannelId }
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp * scale)).background(Color(0xD906111B))
            .border(1.dp, DashboardBorder, RoundedCornerShape(8.dp * scale))
    ) {
        if (channels.isEmpty()) {
            Text(
                d(model, "Nincs megjeleníthető csatorna.", "No channels to display."),
                color = DashboardMuted,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                val selected = channel.id == model.selectedChannelId
                val shape = RoundedCornerShape(6.dp * scale)
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeight).clip(shape)
                        .background(if (selected) Color(0xFF211C38) else Color(0xFF07121C))
                        .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFA277FF) else DashboardBorder.copy(alpha = .58f), shape)
                        .clickable { model.selectChannel(channel.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(54.dp * scale).fillMaxHeight()
                            .background(if (selected) Color(0xFF09131E) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            channel.tvgChno?.toString() ?: (index + 1).toString(),
                            color = Color.White,
                            fontSize = (22f * scale).sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Spacer(Modifier.width(10.dp * scale))
                    DashboardLogo(model, channel, Modifier.size(44.dp * scale))
                    Spacer(Modifier.width(13.dp * scale))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            channel.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (18f * scale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (model.settings.display.showChannelProgramme) {
                            Spacer(Modifier.height(3.dp * scale))
                            Text(
                                model.currentProgram(channel, tick)?.title ?: d(model, "EPG nincs", "No EPG"),
                                color = DashboardMuted,
                                fontSize = (13f * scale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    ChannelSignalIcon(scale)
                    Spacer(Modifier.width(12.dp * scale))
                    ChannelFavoriteIcon(channel.favorite, scale) { model.toggleFavorite(channel.id) }
                    Spacer(Modifier.width(12.dp * scale))
                }
            }
        }
        }
    }
}

@Composable
private fun ChannelSignalIcon(scale: Float) {
    Canvas(Modifier.size(38.dp * scale, 30.dp * scale)) {
        val barWidth = size.width * .105f
        val gap = size.width * .105f
        repeat(4) { index ->
            val height = size.height * (.28f + index * .18f)
            drawRoundRect(
                color = FocusPurple,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun ChannelFavoriteIcon(favorite: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp * scale).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(27.dp * scale)) {
            val heart = Path().apply {
                moveTo(size.width * .50f, size.height * .86f)
                cubicTo(size.width * .42f, size.height * .78f, size.width * .10f, size.height * .58f, size.width * .10f, size.height * .32f)
                cubicTo(size.width * .10f, size.height * .12f, size.width * .34f, size.height * .05f, size.width * .50f, size.height * .24f)
                cubicTo(size.width * .66f, size.height * .05f, size.width * .90f, size.height * .12f, size.width * .90f, size.height * .32f)
                cubicTo(size.width * .90f, size.height * .58f, size.width * .58f, size.height * .78f, size.width * .50f, size.height * .86f)
                close()
            }
            if (favorite) {
                drawPath(heart, FocusPurple)
            } else {
                drawPath(heart, Color(0xFFD0D7E1), style = Stroke(size.minDimension * .065f))
            }
        }
    }
}

@Composable
private fun ProgrammeInformation(
    model: WukkiModel,
    tick: Long,
    scale: Float,
    modifier: Modifier,
    playbackController: PlaybackController
) {
    val channel = model.selectedChannel()
    val programme = channel?.let { model.currentProgram(it, tick) }
    DashboardCard(modifier, contentPadding = 0.dp) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(d(model, "Válassz csatornát.", "Select a channel."), color = DashboardMuted)
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                EmbeddedVlcPlayer(playbackController, modifier = Modifier.fillMaxSize())
            }
            HorizontalDivider(color = DashboardBorder)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(18.dp * scale),
                verticalArrangement = Arrangement.spacedBy(7.dp * scale)
            ) {
                Text(channel.name, color = Color.White, fontSize = (24f * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    programme?.title ?: d(model, "EPG nincs", "No EPG"),
                    color = Color.White,
                    fontSize = (17f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(model, "Nincs műsoradat", "No programme data"),
                    color = DashboardMuted,
                    fontSize = (13f * scale).sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
                ) {
                    if (programme != null) {
                        ProgrammeProgress(programme, tick, Modifier.weight(1f))
                    } else {
                        Box(
                            Modifier.weight(1f).height(5.dp * scale).clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFF27364B))
                        )
                    }
                    Text(formatTime(tick), color = DashboardMuted, fontSize = (12f * scale).sp)
                }
                Spacer(Modifier.height(8.dp * scale))
                if (model.settings.display.showMiniGuide) {
                    Text(
                        programme?.description?.takeIf { it.isNotBlank() }
                            ?: d(model, "Ehhez a műsorhoz nincs leírás.", "No description is available for this programme."),
                        color = Color(0xFFC5CDD8),
                        fontSize = (13f * scale).sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { model.toggleFavorite(channel.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp * scale),
                    shape = RoundedCornerShape(8.dp * scale),
                    border = BorderStroke(1.dp, DashboardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(
                        if (channel.favorite) {
                            "♥ ${d(model, "Kedvenc", "Favorite")}"
                        } else {
                            "♡ ${d(model, "Kedvencekhez adom", "Add to favorites")}"
                        },
                        fontSize = (14f * scale).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long, modifier: Modifier = Modifier) {
    val progress =
        ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        color = FocusPurple,
        trackColor = Color(0xFF27364B),
        modifier = modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
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
