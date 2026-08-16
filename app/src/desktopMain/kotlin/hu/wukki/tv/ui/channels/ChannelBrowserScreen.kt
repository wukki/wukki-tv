package hu.wukki.tv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ChannelListDisplayMode
import hu.wukki.tv.Programme
import hu.wukki.tv.OTHER_CATEGORY_ID
import hu.wukki.tv.ui.components.ChannelLogo
import hu.wukki.tv.ui.components.ProgrammeArtwork
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus

private val panelBorder = WukkiColors.border
private val muted = WukkiColors.textMuted
private val accent = WukkiColors.primary

/** Channels feature. The optional video slot is supplied by the app composition. */
@Composable
fun ChannelBrowserScreen(
    state: ChannelBrowserUiState,
    callbacks: ChannelBrowserCallbacks,
    tick: Long,
    modifier: Modifier,
    scale: Float,
    remoteFocus: ChannelRemoteFocus,
    remoteFilterIndex: Int,
    remoteListIndex: Int,
    listOpenRequest: Int,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    videoPreview: @Composable () -> Unit
) {
    val screenFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus() else screenFocusRequester.requestFocus()
    }
    LaunchedEffect(remoteFocus) {
        if (remoteFocus == ChannelRemoteFocus.SEARCH) onSearchOpenChange(true)
        else if (searchOpen) {
            callbacks.onQueryChange("")
            onSearchOpenChange(false)
        }
    }
    DisposableEffect(Unit) { onDispose { callbacks.onQueryChange("") } }

    Column(
        modifier = modifier.focusRequester(screenFocusRequester).focusable(),
        verticalArrangement = Arrangement.spacedBy(12.dp * scale)
    ) {
        ChannelHeader(
            state, callbacks, searchOpen, scale, searchFocusRequester, remoteFocus, remoteFilterIndex,
            onOpenSearch = { onSearchOpenChange(true) },
            onCloseSearch = { callbacks.onQueryChange(""); onSearchOpenChange(false) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale)
        ) {
            ChannelDirectory(
                state, callbacks, scale, remoteFocus == ChannelRemoteFocus.LIST,
                remoteFocus == ChannelRemoteFocus.FAVORITE, remoteListIndex, listOpenRequest,
                modifier = Modifier.weight(.62f).fillMaxHeight()
            )
            ProgrammeInformation(
                state.preview, state.language, state.showMiniGuide, state.showProgrammeImages, callbacks,
                scale, Modifier.weight(.38f).fillMaxHeight(), videoPreview
            )
        }
    }
}

@Composable
private fun ChannelHeader(
    state: ChannelBrowserUiState,
    callbacks: ChannelBrowserCallbacks,
    searchOpen: Boolean,
    scale: Float,
    searchFocusRequester: FocusRequester,
    remoteFocus: ChannelRemoteFocus,
    remoteFilterIndex: Int,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(tr(state.language, "channels.title"), color = WukkiColors.textPrimary, fontSize = (28f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp * scale))
        if (searchOpen) {
            Row(Modifier.fillMaxWidth().height(56.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                OutlinedTextField(
                    value = state.query, onValueChange = callbacks.onQueryChange, singleLine = true,
                    placeholder = { Text(tr(state.language, "channels.search"), color = muted) },
                    textStyle = LocalTextStyle.current.copy(color = WukkiColors.textPrimary, fontSize = (15f * scale).sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = panelBorder, focusedTextColor = WukkiColors.textPrimary, unfocusedTextColor = WukkiColors.textPrimary, cursorColor = accent),
                    modifier = Modifier.widthIn(min = 0.dp).weight(1f).fillMaxHeight().focusRequester(searchFocusRequester).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onCloseSearch(); true } else false
                    }
                )
                ChannelHeaderIcon(true, false, scale, onCloseSearch)
            }
        } else {
            Row(Modifier.fillMaxWidth().height(50.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                ChannelFilters(
                    state = state,
                    callbacks = callbacks,
                    remoteFocus = remoteFocus,
                    remoteFilterIndex = remoteFilterIndex,
                    scale = scale,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ChannelHeaderIcon(false, remoteFocus == ChannelRemoteFocus.SEARCH, scale, onOpenSearch)
            }
        }
    }
}

@Composable
private fun ChannelFilters(
    state: ChannelBrowserUiState,
    callbacks: ChannelBrowserCallbacks,
    remoteFocus: ChannelRemoteFocus,
    remoteFilterIndex: Int,
    scale: Float,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val filterCount = state.categories.size + 2
    LaunchedEffect(remoteFocus, remoteFilterIndex, filterCount) {
        if (remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex in 0 until filterCount) {
            listState.animateScrollToItem(remoteFilterIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale)
    ) {
        item(key = "all") {
            ChannelFilterTab(
                tr(state.language, "channels.all"),
                state.selectedCategory == null && !state.onlyFavorites,
                remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == 0,
                scale,
                callbacks.onSelectAll
            )
        }
        item(key = "favorites") {
            ChannelFilterTab(
                tr(state.language, "channels.favorites"),
                state.onlyFavorites,
                remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == 1,
                scale,
                callbacks.onSelectFavorites
            )
        }
        itemsIndexed(state.categories, key = { _, category -> category }) { index, category ->
            ChannelFilterTab(
                if (category == OTHER_CATEGORY_ID) tr(state.language, "channels.other") else category,
                state.selectedCategory == category && !state.onlyFavorites,
                remoteFocus == ChannelRemoteFocus.FILTERS && remoteFilterIndex == index + 2,
                scale
            ) { callbacks.onSelectCategory(category) }
        }
    }
}

@Composable
private fun ChannelFilterTab(label: String, selected: Boolean, focused: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp * scale)
    val selectedBackground = if (selected) Modifier.background(WukkiBrushes.selectedSurface()) else Modifier.background(Color.Transparent)
    Box(
        Modifier.fillMaxHeight().clip(shape).then(selectedBackground)
            .border(if (focused) 2.dp else 0.dp, if (focused) accent else Color.Transparent, shape).clickable(onClick = onClick).padding(horizontal = 16.dp * scale),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) WukkiColors.textPrimary else WukkiColors.textSecondary, fontSize = (15f * scale).sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
}

@Composable
private fun ChannelHeaderIcon(close: Boolean, focused: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp * scale)
    Box(Modifier.size(46.dp * scale).clip(shape).background(WukkiColors.backgroundRaised).border(if (focused) 2.dp else 1.dp, if (focused) accent else panelBorder, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (close) Icons.Outlined.Close else Icons.Outlined.Search, null, tint = WukkiColors.textPrimary, modifier = Modifier.size(22.dp * scale))
    }
}

@Composable
private fun ChannelDirectory(
    state: ChannelBrowserUiState, callbacks: ChannelBrowserCallbacks, scale: Float, listFocused: Boolean,
    favoriteFocused: Boolean, remoteListIndex: Int, listOpenRequest: Int, modifier: Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var centredOpenRequest by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val rowHeight = rowHeight(state.displayMode, scale)
    LaunchedEffect(remoteListIndex, state.channels.map { it.channel.id }, listOpenRequest, viewportHeightPx) {
        if (state.channels.isEmpty() || viewportHeightPx <= 0) return@LaunchedEffect
        val target = remoteListIndex.coerceIn(0, state.channels.lastIndex)
        if (centredOpenRequest != listOpenRequest) {
            withFrameNanos { }
            val offset = -((viewportHeightPx - with(density) { rowHeight.roundToPx() }).coerceAtLeast(0) / 2)
            listState.animateScrollToItem(target, offset)
            centredOpenRequest = listOpenRequest
        } else listState.animateScrollToItem(target)
    }
    Box(modifier.onSizeChanged { viewportHeightPx = it.height }.clip(RoundedCornerShape(8.dp * scale)).background(WukkiColors.surfaceOverlay).border(1.dp, panelBorder, RoundedCornerShape(8.dp * scale))) {
        if (state.channels.isEmpty()) Text(tr(state.language, "channels.empty"), color = muted, modifier = Modifier.align(Alignment.Center))
        else LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.channels, key = { _, row -> row.channel.id }) { index, row ->
                ChannelListRow(state, row, rowHeight, scale, row.channel.id == state.selectedChannelId, index == remoteListIndex, listFocused, favoriteFocused, callbacks)
            }
        }
    }
}

private fun rowHeight(mode: ChannelListDisplayMode, scale: Float): Dp = when (mode) {
    ChannelListDisplayMode.COMPACT -> (64.dp * scale).coerceAtLeast(52.dp)
    ChannelListDisplayMode.NORMAL -> (88.dp * scale).coerceAtLeast(66.dp)
    ChannelListDisplayMode.DETAILED -> (120.dp * scale).coerceAtLeast(92.dp)
}

@Composable
private fun ChannelListRow(
    state: ChannelBrowserUiState, row: ChannelBrowserRowUiState, height: Dp, scale: Float, selected: Boolean,
    remoteSelected: Boolean, listFocused: Boolean, favoriteFocused: Boolean, callbacks: ChannelBrowserCallbacks
) {
    val channel = row.channel
    val compact = state.displayMode == ChannelListDisplayMode.COMPACT
    val detailed = state.displayMode == ChannelListDisplayMode.DETAILED
    val shape = RoundedCornerShape(6.dp * scale)
    val logoSize = when (state.displayMode) { ChannelListDisplayMode.COMPACT -> 32.dp * scale; ChannelListDisplayMode.NORMAL -> 44.dp * scale; ChannelListDisplayMode.DETAILED -> 56.dp * scale }
    Row(Modifier.fillMaxWidth().height(height).clip(shape).background(if (selected) WukkiColors.surfaceSelected else WukkiColors.navigationBackground).border(if (remoteSelected && listFocused) 2.dp else 1.dp, if (remoteSelected && listFocused) WukkiColors.focus else panelBorder.copy(alpha = .58f), shape).clickable { callbacks.onSelectChannel(channel.id) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (compact) 44.dp * scale else 54.dp * scale).fillMaxHeight().background(if (selected) WukkiColors.backgroundRaised else Color.Transparent), contentAlignment = Alignment.Center) {
            Text(channel.tvgChno?.toString() ?: row.position.toString(), color = WukkiColors.textPrimary, fontSize = ((if (compact) 18f else 22f) * scale).sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.width(if (compact) 8.dp * scale else 10.dp * scale))
        ChannelLogo(channel, state.language, Modifier.size(logoSize))
        Spacer(Modifier.width(if (compact) 10.dp * scale else 13.dp * scale))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(channel.name, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = ((if (compact) 16f else 18f) * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                detailed -> DetailedChannelProgrammes(state.language, row.currentProgramme, row.nextProgramme, scale)
                state.showChannelProgramme && !compact -> { Spacer(Modifier.height(3.dp * scale)); Text(row.currentProgramme?.displayTitle(state.language) ?: tr(state.language, "epg.none"), color = muted, fontSize = (13f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (!compact) { Icon(Icons.Outlined.SignalCellularAlt, null, tint = accent, modifier = Modifier.size(30.dp * scale)); Spacer(Modifier.width(12.dp * scale)) }
        FavoriteButton(channel.favorite, remoteSelected && favoriteFocused, if (compact) scale * .85f else scale) { callbacks.onToggleFavorite(channel.id) }
        Spacer(Modifier.width(if (compact) 8.dp * scale else 12.dp * scale))
    }
}

@Composable
private fun DetailedChannelProgrammes(language: hu.wukki.tv.AppLanguage, current: Programme?, next: Programme?, scale: Float) {
    Spacer(Modifier.height(3.dp * scale))
    Text(current?.let { "${formatTime(it.start)}–${formatTime(it.end)}  ${it.displayTitle(language)}" } ?: tr(language, "epg.none"), color = muted, fontSize = (13f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(next?.let { "${tr(language, "epg.next")}: ${formatTime(it.start)}  ${it.displayTitle(language)}" } ?: tr(language, "epg.none"), color = WukkiColors.textSecondary, fontSize = (12f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FavoriteButton(favorite: Boolean, focused: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp * scale)
    Box(Modifier.size(38.dp * scale).clip(shape).border(if (focused) 2.dp else 0.dp, if (focused) accent else Color.Transparent, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (favorite) accent else WukkiColors.textSecondary, modifier = Modifier.size(27.dp * scale))
    }
}

@Composable
private fun ProgrammeInformation(
    preview: ChannelPreviewUiState?, language: hu.wukki.tv.AppLanguage, showMiniGuide: Boolean, showProgrammeImages: Boolean,
    callbacks: ChannelBrowserCallbacks, scale: Float, modifier: Modifier, videoPreview: @Composable () -> Unit
) {
    SurfaceCard(modifier, contentPadding = 0.dp) {
        if (preview == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(language, "channels.select"), color = muted) }
        else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(WukkiColors.video)) { videoPreview() }
            HorizontalDivider(color = panelBorder)
            Column(Modifier.fillMaxWidth().weight(1f).padding(18.dp * scale), verticalArrangement = Arrangement.spacedBy(7.dp * scale)) {
                Text(preview.channel.name, color = WukkiColors.textPrimary, fontSize = (24f * scale).sp, fontWeight = FontWeight.Bold)
                if (preview.currentProgramme?.imageUrl != null && showProgrammeImages) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) { ProgrammeTitleAndTime(language, preview.currentProgramme, scale) }
                        ProgrammeArtwork(preview.currentProgramme, language, Modifier.width(128.dp * scale).aspectRatio(16f / 9f))
                    }
                } else ProgrammeTitleAndTime(language, preview.currentProgramme, scale)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    if (preview.currentProgramme != null) ProgrammeProgress(preview.currentProgramme, preview.now, Modifier.weight(1f))
                    else Box(Modifier.weight(1f).height(5.dp * scale).clip(RoundedCornerShape(99.dp)).background(WukkiColors.overlayDivider))
                    Text(formatTime(preview.now), color = muted, fontSize = (12f * scale).sp)
                }
                Spacer(Modifier.height(8.dp * scale))
                if (showMiniGuide) Text(preview.currentProgramme?.description?.takeIf { it.isNotBlank() } ?: tr(language, "epg.no.description"), color = WukkiColors.textSecondary, fontSize = (13f * scale).sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { callbacks.onToggleFavorite(preview.channel.id) }, modifier = Modifier.fillMaxWidth().height(48.dp * scale), shape = RoundedCornerShape(8.dp * scale), border = BorderStroke(1.dp, panelBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = WukkiColors.textPrimary)) {
                    Text(if (preview.channel.favorite) "♥ ${tr(language, "favourite.current")}" else "♡ ${tr(language, "favourite.add")}", fontSize = (14f * scale).sp)
                }
            }
        }
    }
}

@Composable
private fun ProgrammeTitleAndTime(language: hu.wukki.tv.AppLanguage, programme: Programme?, scale: Float) {
    Text(programme?.displayTitle(language) ?: tr(language, "epg.none"), color = WukkiColors.textPrimary, fontSize = (17f * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: tr(language, "epg.none.description"), color = muted, fontSize = (13f * scale).sp)
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long, modifier: Modifier = Modifier) {
    val progress = ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { progress }, color = accent, trackColor = WukkiColors.overlayDivider, modifier = modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)))
}

@Composable
private fun SurfaceCard(modifier: Modifier, contentPadding: Dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, panelBorder), colors = CardDefaults.cardColors(containerColor = WukkiColors.background)) {
        Column(Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}
