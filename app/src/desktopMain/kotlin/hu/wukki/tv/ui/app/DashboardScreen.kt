package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.live.LiveTvScreen
import hu.wukki.tv.ui.live.LiveTvUiState
import hu.wukki.tv.ui.navigation.NavigationEntryUiState
import hu.wukki.tv.ui.navigation.SideNavigation
import hu.wukki.tv.ui.navigation.SideNavigationUiState
import hu.wukki.tv.ui.settings.*

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

private fun navigationState(model: WukkiModel, activeSection: DashboardSection, tick: Long): SideNavigationUiState {
    val language = model.settings.language
    val date = Instant.ofEpochMilli(tick).atZone(ZoneId.systemDefault()).toLocalDate()
    val locale = Localizer.locale(language)
    return SideNavigationUiState(
        entries = listOf(
            NavigationEntryUiState(DashboardSection.LIVE, tr(language, "nav.live")),
            NavigationEntryUiState(DashboardSection.GUIDE, tr(language, "nav.guide")),
            NavigationEntryUiState(DashboardSection.CHANNELS, tr(language, "nav.channels")),
            NavigationEntryUiState(DashboardSection.SETTINGS, tr(language, "nav.settings"))
        ),
        activeSection = activeSection,
        timeLabel = formatTime(tick),
        dateLabel = date.format(DateTimeFormatter.ofPattern(tr(language, "date.sidebar.pattern"), locale))
    )
}

@Composable
fun DashboardScreen(
    model: WukkiModel,
    playbackController: PlaybackController,
    scope: CoroutineScope,
    tick: Long,
    activeSection: DashboardSection,
    guideState: EpgGuideState,
    onSectionChange: (DashboardSection) -> Unit,
    settingsSection: SettingsSection?,
    onSettingsSectionChange: (SettingsSection?) -> Unit
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
                state = navigationState(model, activeSection, tick),
                onSelect = onSectionChange,
                scale = referenceScale,
                modifier = Modifier.width(navigationWidth).fillMaxHeight()
            )
            when (activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(
                    state = LiveTvUiState(
                        hasChannel = model.selectedChannel() != null,
                        emptyMessage = tr(model.settings.language, "live.empty")
                    ),
                    scale = referenceScale,
                    video = { EmbeddedVlcPlayer(playbackController, Modifier.fillMaxSize()) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                DashboardSection.GUIDE -> EpgGuideScreen(
                    model.guideDataSource(),
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
            model.error?.let { DashboardMessage(tr(model.settings.language, "app.error.prefix", it.text(model.settings.language)), Color(0xFFFFB4AB), Color(0xE65F1D22)) }
            model.status?.let { DashboardMessage(it.text(model.settings.language), Color(0xFFB9F6CA), Color(0xE612352C)) }
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
            tr(model.settings.language, "channels.title"),
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
                    placeholder = { Text(tr(model.settings.language, "channels.search"), color = DashboardMuted) },
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
                        label = tr(model.settings.language, "channels.all"),
                        selected = model.category == null && !model.onlyFavorites,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = false
                        }
                    )
                    ChannelFilterTab(
                        label = tr(model.settings.language, "channels.favorites"),
                        selected = model.onlyFavorites,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = true
                        }
                    )
                    model.categories().forEach { category ->
                        ChannelFilterTab(
                        label = if (category == OTHER_CATEGORY_ID) tr(model.settings.language, "channels.other") else category,
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
                tr(model.settings.language, "channels.empty"),
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
                                model.currentProgram(channel, tick)?.displayTitle(model.settings.language) ?: tr(model.settings.language, "epg.none"),
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
                Text(tr(model.settings.language, "channels.select"), color = DashboardMuted)
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
                    programme?.displayTitle(model.settings.language) ?: tr(model.settings.language, "epg.none"),
                    color = Color.White,
                    fontSize = (17f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: tr(model.settings.language, "epg.none.description"),
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
                            ?: tr(model.settings.language, "epg.no.description"),
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
                            "♥ ${tr(model.settings.language, "favourite.current")}"
                        } else {
                            "♡ ${tr(model.settings.language, "favourite.add")}"
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
    ChannelLogo(channel, model.settings.language, modifier)
}
