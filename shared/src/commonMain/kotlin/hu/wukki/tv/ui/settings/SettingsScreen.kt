package hu.wukki.tv.ui.settings

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.tr

import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsSection { PLAYBACK, EPG, DISPLAY, PARENTAL, PLAYLISTS, LANGUAGE, ABOUT }
private const val SETTINGS_REFERENCE_WIDTH = 1116f
private const val SETTINGS_REFERENCE_HEIGHT = 892f

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int = 0,
    remoteNavigationActive: Boolean = false,
    remoteOptionIndex: Int = 0,
    settingsDropdownOpenRequest: Int = 0,
    settingsDropdownOptionIndex: Int = -1,
    settingsAboutOpenRequest: Int = 0,
    androidFullScreenSubmenus: Boolean = false,
    onCategoryFocus: (Int) -> Unit = {},
    onOptionFocus: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(
            maxWidth.value / SETTINGS_REFERENCE_WIDTH,
            maxHeight.value / SETTINGS_REFERENCE_HEIGHT
        ).coerceIn(.70f, 1f)
        if (androidFullScreenSubmenus) {
            AndroidSettingsLayout(
                state = state,
                callbacks = callbacks,
                selectedSection = selectedSection,
                onSectionChange = onSectionChange,
                remoteCategoryIndex = remoteCategoryIndex,
                remoteNavigationActive = remoteNavigationActive,
                remoteOptionIndex = remoteOptionIndex,
                settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                settingsAboutOpenRequest = settingsAboutOpenRequest,
                onCategoryFocus = onCategoryFocus,
                onOptionFocus = onOptionFocus,
                scale = scale
            )
        } else Column(Modifier.fillMaxSize()) {
            Text(
                tr(state.language, "settings.title"),
                fontWeight = FontWeight.Black,
                fontSize = (29f * scale).sp,
                modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 34.dp * scale)
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(34.dp * scale)
            ) {
                SettingsNavigation(
                    state = state,
                    selected = selectedSection,
                    onSelect = onSectionChange,
                    onCategoryFocus = onCategoryFocus,
                    remoteCategoryIndex = remoteCategoryIndex,
                    remoteNavigationActive = remoteNavigationActive,
                    scale = scale,
                    modifier = Modifier.width(430.dp * scale)
                )
                if (selectedSection == null) {
                    SettingsHome(state.language, scale, Modifier.weight(1f).fillMaxHeight())
                } else {
                    SettingsDetail(
                        state = state,
                        callbacks = callbacks,
                        selectedSection = selectedSection,
                        scale = scale,
                        remoteOptionIndex = remoteOptionIndex,
                        settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                        settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                        settingsAboutOpenRequest = settingsAboutOpenRequest,
                        onOptionFocus = onOptionFocus,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigation(
    state: SettingsUiState,
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
                val focused = selected == null && remoteNavigationActive && index == remoteCategoryIndex
                SettingsListRow(
                    title = item.title(state.language),
                    selected = active || focused,
                    onClick = { onCategoryFocus(index); onSelect(item) },
                    scale = scale,
                    titleFontSize = 19.sp,
                    titleWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Normal
                ) {
                    Row(
                        modifier = Modifier.width((if (scrollable) 116.dp else 148.dp) * scale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item == SettingsSection.LANGUAGE) {
                            Text(
                                tr(state.language, "settings.language.current"),
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
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    remoteOptionIndex: Int,
    settingsDropdownOpenRequest: Int,
    settingsDropdownOptionIndex: Int,
    settingsAboutOpenRequest: Int,
    onCategoryFocus: (Int) -> Unit,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            tr(state.language, "settings.title"),
            fontWeight = FontWeight.Black,
            fontSize = (26f * scale).sp,
            modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 18.dp * scale)
        )
        if (selectedSection == null) {
            SettingsNavigation(
                state = state,
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
            val sectionTitle = selectedSection.title(state.language)
            ListItem(
                headlineContent = { Text(sectionTitle, fontSize = (18f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    IconButton(onClick = { onSectionChange(null) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr(state.language, "action.back"))
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { onSectionChange(null) }
            )
            Spacer(Modifier.height(12.dp))
            SettingsDetail(
                state = state,
                callbacks = callbacks,
                selectedSection = selectedSection,
                scale = scale,
                remoteOptionIndex = remoteOptionIndex,
                settingsDropdownOpenRequest = settingsDropdownOpenRequest,
                settingsDropdownOptionIndex = settingsDropdownOptionIndex,
                settingsAboutOpenRequest = settingsAboutOpenRequest,
                onOptionFocus = onOptionFocus,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsHome(language: AppLanguage, scale: Float, modifier: Modifier) {
    Column(
        modifier = modifier.padding(bottom = 80.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SettingsGear(scale)
        Spacer(Modifier.height(52.dp * scale))
        Text(
            tr(language, "settings.home"),
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
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    selectedSection: SettingsSection,
    scale: Float,
    remoteOptionIndex: Int,
    settingsDropdownOpenRequest: Int,
    settingsDropdownOptionIndex: Int,
    settingsAboutOpenRequest: Int,
    onOptionFocus: (Int) -> Unit,
    modifier: Modifier
) {
    SettingsCard(modifier) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
                when (selectedSection) {
                    SettingsSection.PLAYBACK -> PlaybackSettingsPane(
                        state = state,
                        callbacks = callbacks,
                        remoteOptionIndex = remoteOptionIndex,
                        dropdownOpenRequest = settingsDropdownOpenRequest,
                        dropdownOptionIndex = settingsDropdownOptionIndex,
                        onOptionFocus = onOptionFocus,
                        scale = scale
                    )
                    SettingsSection.EPG -> EpgSettingsPane(state, callbacks, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.DISPLAY -> DisplaySettingsPane(state, callbacks, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.PARENTAL -> ParentalSettingsPane(
                        state.language,
                        remoteOptionIndex,
                        onOptionFocus,
                        scale
                    )
                    SettingsSection.PLAYLISTS -> PlaylistSettingsPane(state, callbacks, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.LANGUAGE -> LanguageSettingsPane(
                        state = state,
                        callbacks = callbacks,
                        remoteOptionIndex = remoteOptionIndex,
                        dropdownOpenRequest = settingsDropdownOpenRequest,
                        dropdownOptionIndex = settingsDropdownOptionIndex,
                        onOptionFocus = onOptionFocus,
                        scale = scale
                    )
                    SettingsSection.ABOUT -> AboutSettingsPane(
                        state,
                        remoteOptionIndex,
                        settingsAboutOpenRequest,
                        onOptionFocus,
                        scale
                    )
                }
        }
    }
}


@Composable
private fun SettingsCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun SettingsSection.title(language: AppLanguage): String = tr(language, when (this) {
    SettingsSection.PLAYBACK -> "settings.playback"
    SettingsSection.EPG -> "settings.epg"
    SettingsSection.DISPLAY -> "settings.display"
    SettingsSection.PARENTAL -> "settings.parental"
    SettingsSection.PLAYLISTS -> "settings.playlists"
    SettingsSection.LANGUAGE -> "settings.language"
    SettingsSection.ABOUT -> "settings.about"
})
