package hu.wukki.tv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus


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
                state, callbacks, scale, remoteListIndex, listOpenRequest,
                modifier = Modifier.weight(.62f).fillMaxHeight()
            )
            ProgrammeInformation(
                state.preview, state.language, state.showMiniGuide, callbacks,
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
        Text(tr(state.language, "channels.title"), fontSize = (28f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp * scale))
        if (searchOpen) {
            Row(Modifier.fillMaxWidth().height(56.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                OutlinedTextField(
                    value = state.query, onValueChange = callbacks.onQueryChange, singleLine = true,
                    placeholder = { Text(tr(state.language, "channels.search")) },
                    textStyle = LocalTextStyle.current.copy(fontSize = (15f * scale).sp),
                    modifier = Modifier.widthIn(min = 0.dp).weight(1f).fillMaxHeight().focusRequester(searchFocusRequester).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onCloseSearch(); true } else false
                    }
                )
                ChannelHeaderIcon(true, scale, onCloseSearch)
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
                ChannelHeaderIcon(false, scale, onOpenSearch)
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
    FilterChip(
        selected = selected || focused,
        onClick = onClick,
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun ChannelHeaderIcon(close: Boolean, scale: Float, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp * scale)) {
        Icon(if (close) Icons.Outlined.Close else Icons.Outlined.Search, null, modifier = Modifier.size(22.dp * scale))
    }
}

@Composable
private fun ChannelDirectory(
    state: ChannelBrowserUiState, callbacks: ChannelBrowserCallbacks, scale: Float,
    remoteListIndex: Int, listOpenRequest: Int, modifier: Modifier
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
    Card(modifier.onSizeChanged { viewportHeightPx = it.height }) {
        if (state.channels.isEmpty()) Text(tr(state.language, "channels.empty"), modifier = Modifier.align(Alignment.CenterHorizontally))
        else LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.channels, key = { _, row -> row.channel.id }) { index, row ->
                ChannelListRow(state, row, rowHeight, scale, callbacks)
                if (index < state.channels.lastIndex) HorizontalDivider()
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
    state: ChannelBrowserUiState, row: ChannelBrowserRowUiState, height: Dp, scale: Float,
    callbacks: ChannelBrowserCallbacks
) {
    val channel = row.channel
    val compact = state.displayMode == ChannelListDisplayMode.COMPACT
    val detailed = state.displayMode == ChannelListDisplayMode.DETAILED
    val logoSize = when (state.displayMode) { ChannelListDisplayMode.COMPACT -> 32.dp * scale; ChannelListDisplayMode.NORMAL -> 44.dp * scale; ChannelListDisplayMode.DETAILED -> 56.dp * scale }
    ListItem(
        modifier = Modifier.fillMaxWidth().height(height).clickable { callbacks.onSelectChannel(channel.id) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                Text(channel.tvgChno?.toString() ?: row.position.toString(), fontSize = ((if (compact) 18f else 22f) * scale).sp, fontWeight = FontWeight.Light)
                if (state.showLogos) ChannelLogo(channel, state.language, Modifier.size(logoSize))
            }
        },
        headlineContent = { Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = ((if (compact) 16f else 18f) * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            when {
                detailed -> DetailedChannelProgrammes(state.language, row.currentProgramme, row.nextProgramme, scale)
                state.showChannelProgramme && !compact -> Text(row.currentProgramme?.displayTitle(state.language) ?: tr(state.language, "epg.none"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!compact) Icon(Icons.Outlined.SignalCellularAlt, null, modifier = Modifier.size(24.dp * scale))
                FavoriteButton(channel.favorite, if (compact) scale * .85f else scale) { callbacks.onToggleFavorite(channel.id) }
            }
        }
    )
}

@Composable
private fun DetailedChannelProgrammes(language: hu.wukki.tv.AppLanguage, current: Programme?, next: Programme?, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp * scale)) {
        if (current == null) {
            Text(
                tr(language, "epg.none"),
                fontSize = (13f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                "${formatTime(current.start)}–${formatTime(current.end)}  ${current.displayTitle(language)}",
                fontSize = (13f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            next?.let { programme ->
                Text(
                    "${tr(language, "epg.next")}: ${formatTime(programme.start)}  ${programme.displayTitle(language)}",
                    fontSize = (12f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, scale: Float, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp * scale)) {
        Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(27.dp * scale))
    }
}

@Composable
private fun ProgrammeInformation(
    preview: ChannelPreviewUiState?, language: hu.wukki.tv.AppLanguage, showMiniGuide: Boolean,
    callbacks: ChannelBrowserCallbacks, scale: Float, modifier: Modifier, videoPreview: @Composable () -> Unit
) {
    SurfaceCard(modifier, contentPadding = 0.dp) {
        if (preview == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(language, "channels.select")) }
        else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(WukkiColors.video)) { videoPreview() }
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp * scale),
                verticalArrangement = Arrangement.spacedBy(7.dp * scale)
            ) {
                Text(preview.channel.name, fontSize = (24f * scale).sp, fontWeight = FontWeight.Bold)
                ProgrammeTitleAndTime(language, preview.currentProgramme, scale)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    if (preview.currentProgramme != null) ProgrammeProgress(preview.currentProgramme, preview.now, Modifier.weight(1f))
                    else LinearProgressIndicator(progress = { 0f }, modifier = Modifier.weight(1f).height(5.dp * scale))
                    Text(formatTime(preview.now), fontSize = (12f * scale).sp)
                }
                Spacer(Modifier.height(8.dp * scale))
                if (showMiniGuide) Text(
                    preview.currentProgramme?.description?.takeIf { it.isNotBlank() } ?: tr(language, "epg.no.description"),
                    fontSize = (13f * scale).sp
                )
                Spacer(Modifier.height(8.dp * scale))
                OutlinedButton(onClick = { callbacks.onToggleFavorite(preview.channel.id) }, modifier = Modifier.fillMaxWidth().height(48.dp * scale)) {
                    Text(if (preview.channel.favorite) "♥ ${tr(language, "favourite.current")}" else "♡ ${tr(language, "favourite.add")}", fontSize = (14f * scale).sp)
                }
            }
        }
    }
}

@Composable
private fun ProgrammeTitleAndTime(language: hu.wukki.tv.AppLanguage, programme: Programme?, scale: Float) {
    Text(programme?.displayTitle(language) ?: tr(language, "epg.none"), fontSize = (17f * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: tr(language, "epg.none.description"), fontSize = (13f * scale).sp)
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long, modifier: Modifier = Modifier) {
    val progress = ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth().height(5.dp))
}

@Composable
private fun SurfaceCard(modifier: Modifier, contentPadding: Dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}
