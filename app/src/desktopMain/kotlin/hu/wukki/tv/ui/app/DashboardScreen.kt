package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.live.LiveTvScreen
import hu.wukki.tv.ui.live.LiveTvUiState
import hu.wukki.tv.ui.navigation.NavigationEntryUiState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.SideNavigation
import hu.wukki.tv.ui.navigation.SideNavigationUiState
import hu.wukki.tv.ui.navigation.isBackKey
import hu.wukki.tv.ui.navigation.isConfirmKey
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

private fun navigationState(
    model: WukkiModel,
    activeSection: DashboardSection,
    focusedSection: DashboardSection?,
    tick: Long
): SideNavigationUiState {
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
        focusedSection = focusedSection,
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
    onSettingsSectionChange: (SettingsSection?) -> Unit,
    mainNavigationFocused: Boolean,
    mainNavigationSection: DashboardSection,
    channelRemoteFocus: ChannelRemoteFocus,
    channelFilterIndex: Int,
    channelListIndex: Int,
    channelListOpenRequest: Int,
    channelSearchOpen: Boolean,
    onChannelSearchOpenChange: (Boolean) -> Unit,
    settingsCategoryIndex: Int,
    settingsOptionIndex: Int,
    guideProgrammeDetailsVisible: Boolean,
    guideProgrammeDialogFocusedAction: GuideProgrammeDialogAction,
    onShowGuideProgrammeDetails: () -> Unit,
    onDismissGuideProgrammeDetails: () -> Unit,
    onOpenGuideProgrammeChannel: (String) -> Unit,
    onGuideProgrammeDialogEvent: (GuideProgrammeDialogEvent) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(WukkiBrushes.appBackground())
    ) {
        val referenceScale = minOf(maxWidth.value / 1470f, maxHeight.value / 920f).coerceIn(.70f, 1.45f)
        val navigationWidth = (326.dp * referenceScale).coerceIn(220.dp, 430.dp)
        val contentPadding = (14.dp * referenceScale).coerceIn(8.dp, 20.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            SideNavigation(
                state = navigationState(model, activeSection, mainNavigationSection.takeIf { mainNavigationFocused }, tick),
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
                    onProgrammeClick = { _, _ -> onShowGuideProgrammeDetails() },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding)
                )

                DashboardSection.CHANNELS -> ChannelScreen(
                    model = model,
                    tick = tick,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(contentPadding),
                    playbackController = playbackController,
                    scale = referenceScale.coerceAtMost(1f),
                    remoteFocus = channelRemoteFocus,
                    remoteFilterIndex = channelFilterIndex,
                    remoteListIndex = channelListIndex,
                    listOpenRequest = channelListOpenRequest,
                    searchOpen = channelSearchOpen,
                    onSearchOpenChange = onChannelSearchOpenChange
                )

                DashboardSection.SETTINGS -> SettingsScreen(
                    model = model,
                    scope = scope,
                    selectedSection = settingsSection,
                    onSectionChange = onSettingsSectionChange,
                    remoteCategoryIndex = settingsCategoryIndex,
                    remoteNavigationActive = !mainNavigationFocused,
                    remoteOptionIndex = settingsOptionIndex,
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
        if (guideProgrammeDetailsVisible) {
            GuideProgrammeDetails(
                model,
                guideState,
                guideProgrammeDialogFocusedAction,
                onDismissGuideProgrammeDetails,
                onOpenGuideProgrammeChannel,
                onGuideProgrammeDialogEvent
            )
        }
    }
}

@Composable
private fun ChannelScreen(
    model: WukkiModel,
    tick: Long,
    modifier: Modifier,
    playbackController: PlaybackController,
    scale: Float,
    remoteFocus: ChannelRemoteFocus,
    remoteFilterIndex: Int,
    remoteListIndex: Int,
    listOpenRequest: Int,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit
) {
    val screenFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus() else screenFocusRequester.requestFocus()
    }
    LaunchedEffect(remoteFocus) {
        if (remoteFocus == ChannelRemoteFocus.SEARCH) {
            onSearchOpenChange(true)
        } else if (searchOpen) {
            model.query = ""
            onSearchOpenChange(false)
        }
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
            remoteFocus = remoteFocus,
            remoteFilterIndex = remoteFilterIndex,
            onOpenSearch = { onSearchOpenChange(true) },
            onCloseSearch = {
                model.query = ""
                onSearchOpenChange(false)
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale)
        ) {
            ChannelDirectory(
                model,
                tick,
                scale,
                remoteFocus == ChannelRemoteFocus.LIST,
                remoteFocus == ChannelRemoteFocus.FAVORITE,
                remoteListIndex,
                listOpenRequest,
                modifier = Modifier.weight(.62f).fillMaxHeight()
            )
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
    remoteFocus: ChannelRemoteFocus,
    remoteFilterIndex: Int,
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
                        focused = remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == 0,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = false
                        }
                    )
                    ChannelFilterTab(
                        label = tr(model.settings.language, "channels.favorites"),
                        selected = model.onlyFavorites,
                        focused = remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == 1,
                        scale = scale,
                        onClick = {
                            model.category = null
                            model.onlyFavorites = true
                        }
                    )
                    model.categories().forEachIndexed { index, category ->
                        ChannelFilterTab(
                        label = if (category == OTHER_CATEGORY_ID) tr(model.settings.language, "channels.other") else category,
                            selected = model.category == category && !model.onlyFavorites,
                            focused = remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == index + 2,
                            scale = scale,
                            onClick = {
                                model.onlyFavorites = false
                                model.category = category
                            }
                        )
                    }
                }
                ChannelHeaderIcon(close = false, focused = remoteFocus == ChannelRemoteFocus.SEARCH, scale = scale, onClick = onOpenSearch)
            }
        }
    }
}

@Composable
private fun ChannelFilterTab(label: String, selected: Boolean, focused: Boolean, scale: Float, onClick: () -> Unit) {
    val background = if (selected) {
        Modifier.background(WukkiBrushes.selectedSurface())
    } else {
        Modifier.background(WukkiColors.transparent)
    }
    Box(
        modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp * scale)).then(background)
            .border(if (focused) 2.dp else 0.dp, if (focused) FocusPurple else WukkiColors.transparent, RoundedCornerShape(9.dp * scale))
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
private fun ChannelHeaderIcon(close: Boolean, focused: Boolean = false, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp * scale).clip(RoundedCornerShape(9.dp * scale))
            .background(WukkiColors.backgroundRaised).border(if (focused) 2.dp else 1.dp, if (focused) FocusPurple else DashboardBorder, RoundedCornerShape(9.dp * scale))
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
private fun ChannelDirectory(
    model: WukkiModel,
    tick: Long,
    scale: Float,
    listFocused: Boolean,
    favoriteFocused: Boolean,
    remoteListIndex: Int,
    listOpenRequest: Int,
    modifier: Modifier
) {
    val channels = model.filteredChannels()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var centredOpenRequest by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val listMode = model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL
    val rowHeight = when (listMode) {
        ChannelListDisplayMode.COMPACT -> (64.dp * scale).coerceAtLeast(52.dp)
        ChannelListDisplayMode.NORMAL -> (88.dp * scale).coerceAtLeast(66.dp)
        ChannelListDisplayMode.DETAILED -> (120.dp * scale).coerceAtLeast(92.dp)
    }

    LaunchedEffect(remoteListIndex, channels.map { it.id }, listOpenRequest, viewportHeightPx) {
        val target = remoteListIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        if (channels.isEmpty()) return@LaunchedEffect
        if (centredOpenRequest != listOpenRequest) {
            if (viewportHeightPx <= 0) return@LaunchedEffect
            withFrameNanos { }
            val rowHeightPx = with(density) { rowHeight.roundToPx() }
            val centerOffset = -((viewportHeightPx - rowHeightPx).coerceAtLeast(0) / 2)
            listState.animateScrollToItem(target, centerOffset)
            centredOpenRequest = listOpenRequest
        } else {
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier.onSizeChanged { viewportHeightPx = it.height }
            .clip(RoundedCornerShape(8.dp * scale)).background(WukkiColors.surfaceOverlay)
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
                    ChannelListRow(
                        model = model,
                        channel = channel,
                        position = index + 1,
                        tick = tick,
                        mode = listMode,
                        height = rowHeight,
                        scale = scale,
                        selected = channel.id == model.selectedChannelId,
                        remoteSelected = index == remoteListIndex,
                        listFocused = listFocused,
                        favoriteFocused = favoriteFocused
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelListRow(
    model: WukkiModel,
    channel: Channel,
    position: Int,
    tick: Long,
    mode: ChannelListDisplayMode,
    height: androidx.compose.ui.unit.Dp,
    scale: Float,
    selected: Boolean,
    remoteSelected: Boolean,
    listFocused: Boolean,
    favoriteFocused: Boolean
) {
    val shape = RoundedCornerShape(6.dp * scale)
    val current = model.currentProgram(channel, tick)
    val next = current?.let { model.nextProgram(channel, it) }
    val compact = mode == ChannelListDisplayMode.COMPACT
    val detailed = mode == ChannelListDisplayMode.DETAILED
    val logoSize = when (mode) {
        ChannelListDisplayMode.COMPACT -> 32.dp * scale
        ChannelListDisplayMode.NORMAL -> 44.dp * scale
        ChannelListDisplayMode.DETAILED -> 56.dp * scale
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(height).clip(shape)
            .background(if (selected) WukkiColors.surfaceSelected else WukkiColors.navigationBackground)
            .border(if (remoteSelected && listFocused) 2.dp else 1.dp, if (remoteSelected && listFocused) WukkiColors.focus else DashboardBorder.copy(alpha = .58f), shape)
            .clickable { model.selectChannel(channel.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(if (compact) 44.dp * scale else 54.dp * scale).fillMaxHeight()
                .background(if (selected) WukkiColors.backgroundRaised else WukkiColors.transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channel.tvgChno?.toString() ?: position.toString(),
                color = WukkiColors.textPrimary,
                fontSize = ((if (compact) 18f else 22f) * scale).sp,
                fontWeight = FontWeight.Light
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp * scale else 10.dp * scale))
        DashboardLogo(model, channel, Modifier.size(logoSize))
        Spacer(Modifier.width(if (compact) 10.dp * scale else 13.dp * scale))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                channel.name,
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = ((if (compact) 16f else 18f) * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                detailed -> DetailedChannelProgrammes(model, current, next, scale)
                model.settings.display.showChannelProgramme && !compact -> {
                    Spacer(Modifier.height(3.dp * scale))
                    Text(
                        current?.displayTitle(model.settings.language) ?: tr(model.settings.language, "epg.none"),
                        color = DashboardMuted,
                        fontSize = (13f * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (!compact) {
            ChannelSignalIcon(scale)
            Spacer(Modifier.width(12.dp * scale))
        }
        ChannelFavoriteIcon(channel.favorite, remoteSelected && favoriteFocused, if (compact) scale * .85f else scale) { model.toggleFavorite(channel.id) }
        Spacer(Modifier.width(if (compact) 8.dp * scale else 12.dp * scale))
    }
}

@Composable
private fun DetailedChannelProgrammes(
    model: WukkiModel,
    current: Programme?,
    next: Programme?,
    scale: Float
) {
    Spacer(Modifier.height(3.dp * scale))
    Text(
        current?.let { "${formatTime(it.start)}–${formatTime(it.end)}  ${it.displayTitle(model.settings.language)}" }
            ?: tr(model.settings.language, "epg.none"),
        color = DashboardMuted,
        fontSize = (13f * scale).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        next?.let { "${tr(model.settings.language, "epg.next")}: ${formatTime(it.start)}  ${it.displayTitle(model.settings.language)}" }
            ?: tr(model.settings.language, "epg.none"),
        color = WukkiColors.textSecondary,
        fontSize = (12f * scale).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChannelSignalIcon(scale: Float) {
    Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null, tint = FocusPurple, modifier = Modifier.size(30.dp * scale))
}

@Composable
private fun ChannelFavoriteIcon(favorite: Boolean, focused: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp * scale).clip(RoundedCornerShape(6.dp * scale))
            .border(if (focused) 2.dp else 0.dp, if (focused) FocusPurple else WukkiColors.transparent, RoundedCornerShape(6.dp * scale))
            .clickable(onClick = onClick),
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
                if (programme?.imageUrl != null && model.settings.display.showProgrammeImages != false) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp * scale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                            ProgrammeTitleAndTime(model, programme, scale)
                        }
                        ProgrammeArtwork(
                            programme = programme,
                            language = model.settings.language,
                            modifier = Modifier.width(128.dp * scale).aspectRatio(16f / 9f)
                        )
                    }
                } else {
                    ProgrammeTitleAndTime(model, programme, scale)
                }
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
private fun ProgrammeTitleAndTime(model: WukkiModel, programme: Programme?, scale: Float) {
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
private fun GuideProgrammeDetails(
    model: WukkiModel,
    state: EpgGuideState,
    focusedAction: GuideProgrammeDialogAction,
    onDismiss: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onRemoteEvent: (GuideProgrammeDialogEvent) -> Unit
) {
    val language = model.settings.language
    val dialogFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { dialogFocusRequester.requestFocus() }
    val focused = state.focusedProgramme(
        model.guideDataSource(),
        guideTimeline(System.currentTimeMillis(), model.guideLatestProgrammeEnd())
    ) ?: run {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val (channel, programme) = focused
    val next = model.programmesFor(channel, programme.end, programme.end + 24 * 60 * 60 * 1000L).firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRequester(dialogFocusRequester).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val dialogEvent = when {
                event.key.isBackKey() -> GuideProgrammeDialogEvent.BACK
                event.key == Key.DirectionLeft -> GuideProgrammeDialogEvent.LEFT
                event.key == Key.DirectionRight -> GuideProgrammeDialogEvent.RIGHT
                event.key.isConfirmKey() -> GuideProgrammeDialogEvent.CONFIRM
                else -> null
            }
            dialogEvent?.let(onRemoteEvent) != null
        },
        containerColor = WukkiColors.surfaceOverlay,
        titleContentColor = WukkiColors.textPrimary,
        textContentColor = WukkiColors.textSecondary,
        title = { Text(programme.displayTitle(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(channel.name, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("${formatTime(programme.start)} – ${formatTime(programme.end)}")
                if (programme.imageUrl != null && model.settings.display.showProgrammeImages != false) {
                    ProgrammeArtwork(
                        programme = programme,
                        language = language,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }
                Text(programme.description?.takeIf { it.isNotBlank() } ?: tr(language, "epg.no.description"))
                next?.let {
                    Text("${tr(language, "epg.next")}: ${it.displayTitle(language)} · ${formatTime(it.start)}", color = WukkiColors.textMuted)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenChannel(channel.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedAction == GuideProgrammeDialogAction.OPEN) WukkiColors.primary else WukkiColors.surfaceInput,
                    contentColor = if (focusedAction == GuideProgrammeDialogAction.OPEN) WukkiColors.textPrimary else WukkiColors.textSecondary
                )
            ) { Text(tr(language, "action.open")) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedAction == GuideProgrammeDialogAction.CANCEL) WukkiColors.primary else WukkiColors.surfaceInput,
                    contentColor = if (focusedAction == GuideProgrammeDialogAction.CANCEL) WukkiColors.textPrimary else WukkiColors.textSecondary
                )
            ) { Text(tr(language, "action.cancel")) }
        }
    )
}

@Composable
private fun DashboardLogo(model: WukkiModel, channel: Channel, modifier: Modifier) {
    ChannelLogo(channel, model.settings.language, modifier)
}
