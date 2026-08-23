package hu.wukki.tv.ui.settings

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.Localizer
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ui.components.WukkiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSection { PLAYBACK, EPG, DISPLAY, PARENTAL, PLAYLISTS, LANGUAGE, ABOUT }
private const val SETTINGS_REFERENCE_WIDTH = 1116f
private const val SETTINGS_REFERENCE_HEIGHT = 892f
private enum class PlaybackOption { AUTOPLAY, VOLUME, BUFFER, ASPECT_RATIO, RECONNECT, RETRIES }

@Composable
fun SettingsScreen(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int = 0,
    remoteNavigationActive: Boolean = false,
    remoteOptionIndex: Int = 0,
    settingsDropdownOpenRequest: Int = 0,
    settingsDropdownOptionIndex: Int = -1,
    androidFullScreenSubmenus: Boolean = false,
    onCategoryFocus: (Int) -> Unit = {},
    onOptionFocus: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    playbackEngineLabel: String = "VLC / libVLC"
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(
            maxWidth.value / SETTINGS_REFERENCE_WIDTH,
            maxHeight.value / SETTINGS_REFERENCE_HEIGHT
        ).coerceIn(.70f, 1f)
        if (androidFullScreenSubmenus) {
            AndroidSettingsLayout(
                model = model,
                scope = scope,
                selectedSection = selectedSection,
                onSectionChange = onSectionChange,
                remoteCategoryIndex = remoteCategoryIndex,
                remoteNavigationActive = remoteNavigationActive,
                remoteOptionIndex = remoteOptionIndex,
                settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                onCategoryFocus = onCategoryFocus,
                onOptionFocus = onOptionFocus,
                scale = scale,
                playbackEngineLabel = playbackEngineLabel
            )
        } else Column(Modifier.fillMaxSize()) {
            Text(
                tr(model.settings.language, "settings.title"),
                fontWeight = FontWeight.Black,
                fontSize = (29f * scale).sp,
                modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 34.dp * scale)
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(34.dp * scale)
            ) {
                SettingsNavigation(
                    model = model,
                    selected = selectedSection,
                    onSelect = onSectionChange,
                    onCategoryFocus = onCategoryFocus,
                    remoteCategoryIndex = remoteCategoryIndex,
                    remoteNavigationActive = remoteNavigationActive,
                    scale = scale,
                    modifier = Modifier.width(430.dp * scale)
                )
                if (selectedSection == null) {
                    SettingsHome(model, scale, Modifier.weight(1f).fillMaxHeight())
                } else {
                    SettingsDetail(
                        model = model,
                        scope = scope,
                        selectedSection = selectedSection,
                        scale = scale,
                        remoteOptionIndex = remoteOptionIndex,
                        settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                        settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                        onOptionFocus = onOptionFocus,
                        playbackEngineLabel = playbackEngineLabel,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigation(
    model: WukkiModel,
    selected: SettingsSection?,
    onSelect: (SettingsSection?) -> Unit,
    onCategoryFocus: (Int) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    scale: Float,
    modifier: Modifier,
    scrollable: Boolean = false
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollable, selected, remoteNavigationActive, remoteCategoryIndex) {
        if (scrollable && selected == null && remoteNavigationActive) {
            listState.animateScrollToItem(remoteCategoryIndex.coerceIn(0, SettingsSection.entries.lastIndex))
        }
    }
    Card(modifier = modifier) {
        LazyColumn(state = listState) {
            itemsIndexed(SettingsSection.entries) { index, item ->
                val active = item == selected
                SettingsListRow(
                    title = item.title(model),
                    selected = active,
                    onClick = { onCategoryFocus(index); onSelect(item) },
                    scale = scale,
                    titleFontSize = 19.sp,
                    titleWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                ) {
                    Row(
                        modifier = Modifier.width((if (scrollable) 116.dp else 148.dp) * scale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item == SettingsSection.LANGUAGE) {
                            Text(
                                tr(model.settings.language, "settings.language.current"),
                                modifier = Modifier.weight(1f),
                                fontSize = ((if (scrollable) 14f else 16f) * scale).sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                maxLines = 1
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.width((if (scrollable) 8.dp else 12.dp) * scale))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(22.dp * scale))
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidSettingsLayout(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    remoteOptionIndex: Int,
    settingsDropdownOpenRequest: Int,
    settingsDropdownOptionIndex: Int,
    onCategoryFocus: (Int) -> Unit,
    onOptionFocus: (Int) -> Unit,
    scale: Float,
    playbackEngineLabel: String
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            tr(model.settings.language, "settings.title"),
            fontWeight = FontWeight.Black,
            fontSize = (26f * scale).sp,
            modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 18.dp * scale)
        )
        if (selectedSection == null) {
            SettingsNavigation(
                model = model,
                selected = null,
                onSelect = onSectionChange,
                onCategoryFocus = onCategoryFocus,
                remoteCategoryIndex = remoteCategoryIndex,
                remoteNavigationActive = remoteNavigationActive,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
                scrollable = true
            )
        } else {
            val sectionTitle = selectedSection.title(model)
            ListItem(
                headlineContent = { Text(sectionTitle, fontSize = (18f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    IconButton(onClick = { onSectionChange(null) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr(model.settings.language, "action.back"))
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { onSectionChange(null) }
            )
            Spacer(Modifier.height(12.dp))
            SettingsDetail(
                model = model,
                scope = scope,
                selectedSection = selectedSection,
                scale = scale,
                remoteOptionIndex = remoteOptionIndex,
                settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                onOptionFocus = onOptionFocus,
                playbackEngineLabel = playbackEngineLabel,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsHome(model: WukkiModel, scale: Float, modifier: Modifier) {
    Column(
        modifier = modifier.padding(bottom = 80.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SettingsGear(scale)
        Spacer(Modifier.height(52.dp * scale))
        Text(
            tr(model.settings.language, "settings.home"),
            fontSize = (18f * scale).sp,
            lineHeight = (28f * scale).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsGear(scale: Float) {
    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(184.dp * scale))
}

@Composable
private fun SettingsDetail(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection,
    scale: Float,
    remoteOptionIndex: Int,
    settingsDropdownOpenRequest: Int,
    settingsDropdownOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    playbackEngineLabel: String,
    modifier: Modifier
) {
    SettingsCard(modifier) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
                when (selectedSection) {
                    SettingsSection.PLAYBACK -> PlaybackSettings(
                        model = model,
                        remoteOptionIndex = remoteOptionIndex,
                        dropdownOpenRequest = settingsDropdownOpenRequest,
                        dropdownOptionIndex = settingsDropdownOptionIndex,
                        onOptionFocus = onOptionFocus,
                        scale = scale
                    )
                    SettingsSection.EPG -> EpgSettings(model, scope, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.DISPLAY -> DisplaySettings(model, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.PARENTAL -> ParentalSettings(model, scale)
                    SettingsSection.PLAYLISTS -> PlaylistSettings(model, scope, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.LANGUAGE -> LanguageSettings(
                        model = model,
                        remoteOptionIndex = remoteOptionIndex,
                        dropdownOpenRequest = settingsDropdownOpenRequest,
                        dropdownOptionIndex = settingsDropdownOptionIndex,
                        onOptionFocus = onOptionFocus,
                        scale = scale
                    )
                    SettingsSection.ABOUT -> AboutSettings(model, playbackEngineLabel, scale)
                }
        }
    }
}

@Composable
private fun PlaybackSettings(
    model: WukkiModel,
    remoteOptionIndex: Int,
    dropdownOpenRequest: Int,
    dropdownOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    val settings = model.settings.playback
    val focusedOption = PlaybackOption.entries.getOrElse(remoteOptionIndex) { PlaybackOption.AUTOPLAY }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsOptionRow(model, "settings.playback.autoplay", "settings.playback.autoplay.description", focusedOption == PlaybackOption.AUTOPLAY, onFocus = { onOptionFocus(0) }, scale = scale) {
            Switch(
                checked = settings.autoPlayOnLaunch != false,
                onCheckedChange = { enabled -> onOptionFocus(0); model.updatePlayback { it.copy(autoPlayOnLaunch = enabled) } },
            )
        }
        SettingsOptionRow(model, "settings.playback.volume", "settings.playback.volume.description", focusedOption == PlaybackOption.VOLUME, onFocus = { onOptionFocus(1) }, scale = scale) {
            Row(
                modifier = Modifier.widthIn(min = 150.dp, max = 205.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${settings.volume}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Slider(
                    value = settings.volume.toFloat(),
                    onValueChange = { volume -> onOptionFocus(1); model.updatePlayback { it.copy(volume = volume.toInt()) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SettingsOptionRow(model, "settings.playback.buffer", "settings.playback.buffer.description", focusedOption == PlaybackOption.BUFFER, onFocus = { onOptionFocus(2) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            SettingsExposedDropdown(
                value = settings.bufferProfile,
                entries = BufferProfile.entries.toList(),
                label = { it.label(model) },
                onFocus = { onOptionFocus(2) },
                openRequest = if (dropdownOptionIndex == 2) dropdownOpenRequest else 0
            ) { profile -> model.updatePlayback { it.copy(bufferProfile = profile) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.aspect", "settings.playback.aspect.description", focusedOption == PlaybackOption.ASPECT_RATIO, onFocus = { onOptionFocus(3) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            SettingsExposedDropdown(
                value = settings.aspectRatio ?: AspectRatioMode.AUTO,
                entries = AspectRatioMode.entries.toList(),
                label = { it.label(model) },
                onFocus = { onOptionFocus(3) },
                openRequest = if (dropdownOptionIndex == 3) dropdownOpenRequest else 0
            ) { ratio -> model.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.reconnect", "settings.playback.reconnect.description", focusedOption == PlaybackOption.RECONNECT, onFocus = { onOptionFocus(4) }, scale = scale) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> onOptionFocus(4); model.updatePlayback { it.copy(autoReconnect = enabled) } },
            )
        }
        SettingsOptionRow(model, "settings.playback.attempts", "settings.playback.attempts.description", focusedOption == PlaybackOption.RETRIES, onFocus = { onOptionFocus(5) }, scale = scale) {
            PlaybackStepper(
                value = settings.reconnectAttempts,
                onDecrease = { onOptionFocus(5); model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts - 1).coerceAtLeast(1)) } },
                onIncrease = { onOptionFocus(5); model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) } }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SettingsOptionRow(
    model: WukkiModel,
    titleKey: String,
    descriptionKey: String? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    scale: Float = 1f,
    control: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }
    SettingsListRow(
        title = tr(model.settings.language, titleKey),
        description = descriptionKey?.let { tr(model.settings.language, it) },
        onClick = if (onFocus != null || onSelect != null) ({ onFocus?.invoke(); onSelect?.invoke() }) else null,
        scale = scale,
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
    ) { control() }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SettingsExposedDropdown(
    value: T,
    entries: List<T>,
    label: (T) -> String,
    onFocus: () -> Unit,
    openRequest: Int,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownShape = RoundedCornerShape(28.dp)
    LaunchedEffect(openRequest) {
        if (openRequest > 0) expanded = true
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { isExpanded ->
            onFocus()
            expanded = isExpanded
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = label(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = dropdownShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = dropdownShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(label(entry)) },
                    onClick = {
                        onFocus()
                        onSelect(entry)
                        expanded = false
                    }
                )
                if (index < entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun PlaybackStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrease,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("−") }
        Text(value.toString(), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(
            onClick = onIncrease,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("+") }
    }
}

@Composable
private fun EpgSettings(model: WukkiModel, scope: CoroutineScope, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
    SettingsOptionRow(model, "settings.epg.refresh", "settings.epg.refresh.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        RefreshSelector(
            model,
            model.settings.epgRefresh,
            intervals = RefreshInterval.entries.toList(),
            useHourlyLabels = true,
            onFocus = { onOptionFocus(0) },
            onSelect = model::setEpgRefresh
        )
    }
    FixedSourceCard(
        model = model,
        titleKey = "settings.epg.source",
        sourceName = model.officialEpgSource?.name ?: tr(model.settings.language, "settings.no.sources"),
        location = model.officialEpgSource?.url,
        updatedAt = model.officialEpgSource?.lastUpdatedAt,
        selected = remoteOptionIndex == 1,
        onFocus = { onOptionFocus(1) },
        onRefresh = { scope.launch { model.refreshOfficialEpg() } },
        scale = scale
    )
    }
}
@Composable
private fun DisplaySettings(model: WukkiModel, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
    SettingsOptionRow(model, "settings.display.scale", "settings.display.scale.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        val options = listOf(.9f to tr(model.settings.language, "settings.display.small"), 1f to tr(model.settings.language, "settings.display.normal"), 1.15f to tr(model.settings.language, "settings.display.large"))
        SettingsSegmentedChoice(
            entries = options,
            selected = options.first { it.first == model.settings.display.uiScale },
            label = { it.second },
            onSelect = { option -> onOptionFocus(0); model.updateDisplay { it.copy(uiScale = option.first) } }
        )
    }
    SettingsOptionRow(model, "settings.display.channel.list", "settings.display.channel.list.description", selected = remoteOptionIndex == 1, onFocus = { onOptionFocus(1) }, scale = scale) {
        SettingsSegmentedChoice(
            entries = ChannelListDisplayMode.entries,
            selected = model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL,
            label = { it.label(model) },
            onSelect = { mode -> onOptionFocus(1); model.updateDisplay { it.copy(channelListMode = mode) } }
        )
    }
    SettingsToggle(model, "settings.display.programme", "settings.display.programme.description", model.settings.display.showChannelProgramme, remoteOptionIndex == 2, { onOptionFocus(2) }, scale) { model.updateDisplay { settings -> settings.copy(showChannelProgramme = it) } }
    SettingsToggle(model, "settings.display.mini.guide", "settings.display.mini.guide.description", model.settings.display.showMiniGuide, remoteOptionIndex == 3, { onOptionFocus(3) }, scale) { model.updateDisplay { settings -> settings.copy(showMiniGuide = it) } }
    SettingsToggle(model, "settings.display.logos", "settings.display.logos.description", model.settings.display.showLogos, remoteOptionIndex == 4, { onOptionFocus(4) }, scale) { model.updateDisplay { settings -> settings.copy(showLogos = it) } }
    SettingsToggle(model, "settings.display.programme.images", "settings.display.programme.images.description", model.settings.display.showProgrammeImages != false, remoteOptionIndex == 5, { onOptionFocus(5) }, scale) { model.updateDisplay { settings -> settings.copy(showProgrammeImages = it) } }
    }
}

@Composable
private fun PlaylistSettings(model: WukkiModel, scope: CoroutineScope, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
    SettingsOptionRow(model, "settings.playlist.refresh", "settings.playlist.refresh.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        RefreshSelector(
            model,
            model.settings.playlistRefresh,
            intervals = listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY),
            onFocus = { onOptionFocus(0) },
            onSelect = model::setPlaylistRefresh
        )
    }
    FixedSourceCard(
        model = model,
        titleKey = "settings.playlist.source",
        sourceName = model.officialPlaylist.name,
        location = model.officialPlaylist.location,
        updatedAt = model.officialPlaylist.updatedAt,
        footer = tr(model.settings.language, "settings.channels.count", model.state.channels.size),
        selected = remoteOptionIndex == 1,
        onFocus = { onOptionFocus(1) },
        onRefresh = { scope.launch { model.refreshOfficialPlaylist() } },
        scale = scale
    )
    }
}

@Composable
private fun FixedSourceCard(
    model: WukkiModel,
    titleKey: String,
    sourceName: String,
    location: String?,
    updatedAt: Long?,
    selected: Boolean,
    onFocus: () -> Unit,
    onRefresh: () -> Unit,
    footer: String? = null,
    scale: Float
) {
    val details = footer ?: updatedAt?.let { "${tr(model.settings.language, "settings.updated")}: ${formatTime(it)}" }
        ?: tr(model.settings.language, "settings.not.updated")
    SettingsListRow(
        title = tr(model.settings.language, titleKey),
        description = details,
        onClick = onFocus,
        scale = scale
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(sourceName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            location?.let { Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            TextButton(onClick = { onFocus(); onRefresh() }) { Text(tr(model.settings.language, "settings.refresh")) }
        }
    }
}
@Composable
private fun LanguageSettings(
    model: WukkiModel,
    remoteOptionIndex: Int,
    dropdownOpenRequest: Int,
    dropdownOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    Column {
    SettingsOptionRow(model, "settings.language.title", "settings.language.notice", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            SettingsExposedDropdown(
                value = model.settings.language,
                entries = AppLanguage.entries.toList(),
                label = { language -> tr(model.settings.language, if (language == AppLanguage.HUNGARIAN) "language.hungarian" else "language.english") },
                onFocus = { onOptionFocus(0) },
                openRequest = if (dropdownOptionIndex == 0) dropdownOpenRequest else 0,
                onSelect = model::setLanguage
            )
        }
    }
    }
}

@Composable
private fun ParentalSettings(model: WukkiModel, scale: Float) {
    SettingsOptionRow(model, "settings.parental.coming", "settings.parental.description", scale = scale) { }
}

@Composable
private fun AboutSettings(model: WukkiModel, playbackEngineLabel: String, scale: Float) {
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.Default) { DeviceInfoProvider.collect() }
    }
    Column {
        SettingsOptionRow(model, "settings.about", "settings.about.licenses", scale = scale) { Text("Wukki TV", fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.version", scale = scale) { Text(WukkiBuildInfo.VERSION, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.build", scale = scale) { Text(WukkiBuildInfo.BUILD, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.engine", scale = scale) { Text(playbackEngineLabel, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.platform", scale = scale) { Text(deviceInfo?.platform ?: tr(model.settings.language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.os", scale = scale) { Text(deviceInfo?.osVersion ?: tr(model.settings.language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.device.id", scale = scale) { Text(deviceInfo?.installationId ?: tr(model.settings.language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.storage", scale = scale) {
            Text(
                deviceInfo?.let { info -> tr(model.settings.language, "settings.about.storage.value", formatByteSize(info.appDataBytes), formatByteSize(info.availableStorageBytes)) }
                    ?: tr(model.settings.language, "settings.about.loading"),
                fontWeight = FontWeight.SemiBold
            )
        }
        SettingsOptionRow(model, "settings.about.privacy", onSelect = { legalDocument = LegalDocument.PRIVACY }, scale = scale) {
            Text(tr(model.settings.language, "action.open"), fontWeight = FontWeight.SemiBold)
        }
        SettingsOptionRow(model, "settings.about.licenses.title", onSelect = { legalDocument = LegalDocument.LICENSES }, scale = scale) {
            Text(tr(model.settings.language, "action.open"), fontWeight = FontWeight.SemiBold)
        }
    }
    legalDocument?.let { document ->
        LegalDocumentDialog(model, document) { legalDocument = null }
    }
}

private enum class LegalDocument(val resourceStem: String, val titleKey: String) {
    PRIVACY("privacy", "settings.about.privacy"),
    LICENSES("vlc_notice", "settings.about.licenses.title")
}

@Composable
private fun LegalDocumentDialog(model: WukkiModel, document: LegalDocument, onDismiss: () -> Unit) {
    val language = model.settings.language
        val text = remember(document, language) {
            val languageSuffix = if (language == AppLanguage.HUNGARIAN) "hu" else "en"
            Localizer.legalText("legal/${document.resourceStem}_$languageSuffix.txt")
                ?: tr(language, "settings.about.document.unavailable")
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(language, document.titleKey), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = text,
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(tr(language, "action.close")) }
        }
    )
}

@Composable
private fun SettingsToggle(
    model: WukkiModel,
    titleKey: String,
    descriptionKey: String,
    checked: Boolean,
    selected: Boolean = false,
    onFocus: () -> Unit,
    scale: Float,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsOptionRow(model, titleKey, descriptionKey, selected = selected, onFocus = onFocus, scale = scale) {
        Switch(checked = checked, onCheckedChange = { value -> onFocus(); onCheckedChange(value) })
    }
}

/** Shared Material list row used by both the settings categories and their option panels. */
@Composable
private fun SettingsListRow(
    title: String,
    description: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    scale: Float = 1f,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    titleWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontSize = titleFontSize * scale, fontWeight = titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = description?.let { value -> { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = { control() },
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                headlineColor = MaterialTheme.colorScheme.onPrimary,
                supportingColor = MaterialTheme.colorScheme.onPrimary,
                trailingIconColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ListItemDefaults.colors()
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 81.dp * scale)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
    )
    HorizontalDivider()
}

@Composable
private fun RefreshSelector(
    model: WukkiModel,
    selected: RefreshInterval,
    intervals: List<RefreshInterval>,
    useHourlyLabels: Boolean = false,
    onFocus: () -> Unit,
    onSelect: (RefreshInterval) -> Unit
) {
    SettingsSegmentedChoice(
        entries = intervals,
        selected = selected,
        label = { interval -> interval.label(model, useHourlyLabels) },
        onSelect = { interval -> onFocus(); onSelect(interval) }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SettingsSegmentedChoice(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow {
        entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                label = { Text(label(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun SettingsSection.title(model: WukkiModel): String = tr(model.settings.language, when (this) {
    SettingsSection.PLAYBACK -> "settings.playback"
    SettingsSection.EPG -> "settings.epg"
    SettingsSection.DISPLAY -> "settings.display"
    SettingsSection.PARENTAL -> "settings.parental"
    SettingsSection.PLAYLISTS -> "settings.playlists"
    SettingsSection.LANGUAGE -> "settings.language"
    SettingsSection.ABOUT -> "settings.about"
})

private fun RefreshInterval.label(model: WukkiModel, useHourlyLabels: Boolean = false): String = tr(model.settings.language, when (this) {
    RefreshInterval.MANUAL -> "refresh.manual"
    RefreshInterval.SIX_HOURS -> "refresh.six.hours"
    RefreshInterval.TWELVE_HOURS -> "refresh.twelve.hours"
    RefreshInterval.DAILY -> if (useHourlyLabels) "refresh.twentyfour.hours" else "refresh.daily"
})
private fun BufferProfile.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    BufferProfile.LOW_LATENCY -> "buffer.small"; BufferProfile.BALANCED -> "buffer.medium"; BufferProfile.STABLE -> "buffer.large"
})
private fun AspectRatioMode.label(model: WukkiModel): String = when (this) { AspectRatioMode.AUTO -> tr(model.settings.language, "aspect.auto"); AspectRatioMode.RATIO_16_9 -> "16:9"; AspectRatioMode.RATIO_4_3 -> "4:3"; AspectRatioMode.RATIO_21_9 -> "21:9"; AspectRatioMode.FILL_CROP -> tr(model.settings.language, "aspect.fill") }
private fun ChannelListDisplayMode.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    ChannelListDisplayMode.COMPACT -> "settings.display.channel.list.compact"
    ChannelListDisplayMode.NORMAL -> "settings.display.channel.list.normal"
    ChannelListDisplayMode.DETAILED -> "settings.display.channel.list.detailed"
})
