package hu.wukki.tv.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AspectRatioMode
import hu.wukki.tv.BufferProfile
import hu.wukki.tv.ChannelListDisplayMode
import hu.wukki.tv.RefreshInterval
import hu.wukki.tv.ui.components.tr

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SettingsOptionRow(
    language: AppLanguage,
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
        title = tr(language, titleKey),
        description = descriptionKey?.let { tr(language, it) },
        onClick = if (onFocus != null || onSelect != null) ({ onFocus?.invoke(); onSelect?.invoke() }) else null,
        scale = scale,
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
        control = control
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> SettingsExposedDropdown(
    value: T,
    entries: List<T>,
    label: (T) -> String,
    onFocus: () -> Unit,
    openRequest: Int,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownShape = RoundedCornerShape(28.dp)
    LaunchedEffect(openRequest) { if (openRequest > 0) expanded = true }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { isExpanded -> onFocus(); expanded = isExpanded },
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
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = dropdownShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(label(entry)) },
                    onClick = { onFocus(); onSelect(entry); expanded = false }
                )
                if (index < entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
internal fun PlaybackStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrease,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("−") }
        Text(value.toString(), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
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
internal fun SettingsToggle(
    language: AppLanguage,
    titleKey: String,
    descriptionKey: String,
    checked: Boolean,
    selected: Boolean = false,
    onFocus: () -> Unit,
    scale: Float,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsOptionRow(language, titleKey, descriptionKey, selected = selected, onFocus = onFocus, scale = scale) {
        Switch(checked = checked, onCheckedChange = { value -> onFocus(); onCheckedChange(value) })
    }
}

@Composable
internal fun SettingsListRow(
    title: String,
    description: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    scale: Float = 1f,
    titleFontSize: TextUnit = 19.sp,
    titleWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontSize = titleFontSize * scale, fontWeight = titleWeight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = description?.let { value -> { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = control,
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                headlineColor = MaterialTheme.colorScheme.onPrimary,
                supportingColor = MaterialTheme.colorScheme.onPrimary,
                trailingIconColor = MaterialTheme.colorScheme.onPrimary
            )
        } else ListItemDefaults.colors(),
        modifier = modifier.fillMaxWidth().heightIn(min = 81.dp * scale)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
    )
    HorizontalDivider()
}

@Composable
internal fun RefreshSelector(
    language: AppLanguage,
    selected: RefreshInterval,
    intervals: List<RefreshInterval>,
    useHourlyLabels: Boolean = false,
    onFocus: () -> Unit,
    onSelect: (RefreshInterval) -> Unit
) {
    SettingsSegmentedChoice(
        entries = intervals,
        selected = selected,
        label = { interval -> interval.label(language, useHourlyLabels) },
        onSelect = { interval -> onFocus(); onSelect(interval) }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> SettingsSegmentedChoice(entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
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

internal fun RefreshInterval.label(language: AppLanguage, useHourlyLabels: Boolean = false): String = tr(language, when (this) {
    RefreshInterval.MANUAL -> "refresh.manual"
    RefreshInterval.SIX_HOURS -> "refresh.six.hours"
    RefreshInterval.TWELVE_HOURS -> "refresh.twelve.hours"
    RefreshInterval.DAILY -> if (useHourlyLabels) "refresh.twentyfour.hours" else "refresh.daily"
})

internal fun BufferProfile.label(language: AppLanguage): String = tr(language, when (this) {
    BufferProfile.LOW_LATENCY -> "buffer.small"
    BufferProfile.BALANCED -> "buffer.medium"
    BufferProfile.STABLE -> "buffer.large"
})

internal fun AspectRatioMode.label(language: AppLanguage): String = when (this) {
    AspectRatioMode.AUTO -> tr(language, "aspect.auto")
    AspectRatioMode.RATIO_16_9 -> "16:9"
    AspectRatioMode.RATIO_4_3 -> "4:3"
    AspectRatioMode.RATIO_21_9 -> "21:9"
    AspectRatioMode.FILL_CROP -> tr(language, "aspect.fill")
}

internal fun ChannelListDisplayMode.label(language: AppLanguage): String = tr(language, when (this) {
    ChannelListDisplayMode.COMPACT -> "settings.display.channel.list.compact"
    ChannelListDisplayMode.NORMAL -> "settings.display.channel.list.normal"
    ChannelListDisplayMode.DETAILED -> "settings.display.channel.list.detailed"
})
