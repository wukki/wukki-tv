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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SignalCellularAlt
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

private val DashboardPanel = WukkiColors.background
private val DashboardMuted = WukkiColors.textMuted
private val DashboardBorder = WukkiColors.border
private val FocusPurple = WukkiColors.primary

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
        modifier = Modifier.fillMaxSize().background(WukkiBrushes.appBackground())
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
            model.error?.let { DashboardMessage(tr(model.settings.language, "app.error.prefix", it.text(model.settings.language)), WukkiColors.error, WukkiColors.errorContainer) }
            model.status?.let { DashboardMessage(it.text(model.settings.language), WukkiColors.success, WukkiColors.successContainer) }
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
            color = WukkiColors.textPrimary,
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
                    textStyle = LocalTextStyle.current.copy(color = WukkiColors.textPrimary, fontSize = (15f * scale).sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusPurple,
                        unfocusedBorderColor = DashboardBorder,
                        focusedTextColor = WukkiColors.textPrimary,
                        unfocusedTextColor = WukkiColors.textPrimary,
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
        Modifier.background(WukkiBrushes.selectedSurface())
    } else {
        Modifier.background(WukkiColors.transparent)
    }
    Box(
        modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp * scale)).then(background)
            .clickable(onClick = onClick).padding(horizontal = 16.dp * scale),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) WukkiColors.textPrimary else WukkiColors.textSecondary,
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
            .background(WukkiColors.backgroundRaised).border(1.dp, DashboardBorder, RoundedCornerShape(9.dp * scale))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (close) Icons.Outlined.Close else Icons.Outlined.Search,
            contentDescription = null,
            tint = WukkiColors.textPrimary,
            modifier = Modifier.size(22.dp * scale)
        )
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
        modifier = modifier.clip(RoundedCornerShape(8.dp * scale)).background(WukkiColors.surfaceOverlay)
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
                        .background(if (selected) WukkiColors.surfaceSelected else WukkiColors.navigationBackground)
                        .border(if (selected) 2.dp else 1.dp, if (selected) WukkiColors.focus else DashboardBorder.copy(alpha = .58f), shape)
                        .clickable { model.selectChannel(channel.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(54.dp * scale).fillMaxHeight()
                            .background(if (selected) WukkiColors.backgroundRaised else WukkiColors.transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            channel.tvgChno?.toString() ?: (index + 1).toString(),
                            color = WukkiColors.textPrimary,
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
                            color = WukkiColors.textPrimary,
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
    Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null, tint = FocusPurple, modifier = Modifier.size(30.dp * scale))
}

@Composable
private fun ChannelFavoriteIcon(favorite: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp * scale).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (favorite) FocusPurple else WukkiColors.textSecondary,
            modifier = Modifier.size(27.dp * scale)
        )
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
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(WukkiColors.video)) {
                EmbeddedVlcPlayer(playbackController, modifier = Modifier.fillMaxSize())
            }
            HorizontalDivider(color = DashboardBorder)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(18.dp * scale),
                verticalArrangement = Arrangement.spacedBy(7.dp * scale)
            ) {
                Text(channel.name, color = WukkiColors.textPrimary, fontSize = (24f * scale).sp, fontWeight = FontWeight.Bold)
                Text(
                    programme?.displayTitle(model.settings.language) ?: tr(model.settings.language, "epg.none"),
                    color = WukkiColors.textPrimary,
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
                                .background(WukkiColors.overlayDivider)
                        )
                    }
                    Text(formatTime(tick), color = DashboardMuted, fontSize = (12f * scale).sp)
                }
                Spacer(Modifier.height(8.dp * scale))
                if (model.settings.display.showMiniGuide) {
                    Text(
                        programme?.description?.takeIf { it.isNotBlank() }
                            ?: tr(model.settings.language, "epg.no.description"),
                        color = WukkiColors.textSecondary,
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WukkiColors.textPrimary)
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
        trackColor = WukkiColors.overlayDivider,
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
