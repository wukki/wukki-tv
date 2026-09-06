package hu.wukki.tv.ui.app

import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.settings.SettingsSection

/** User intentions emitted by the dashboard screens. */
data class DashboardCallbacks(
    val onSectionChange: (DashboardSection) -> Unit,
    val onSettingsSectionChange: (SettingsSection?) -> Unit,
    val onChannelPreviewSelect: (String) -> Unit,
    val onOpenChannel: (String) -> Unit,
    val onChannelSearchOpenChange: (Boolean) -> Unit,
    val onSettingsCategoryFocus: (Int) -> Unit,
    val onSettingsOptionFocus: (Int) -> Unit,
    val onShowGuideProgrammeDetails: () -> Unit,
    val onDismissGuideProgrammeDetails: () -> Unit,
    val onOpenGuideProgrammeChannel: (String) -> Unit,
    val onGuideProgrammeDialogEvent: (GuideProgrammeDialogEvent) -> Unit
)
