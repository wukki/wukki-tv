package hu.wukki.tv.ui.app

import hu.wukki.tv.*
import hu.wukki.tv.ui.guide.*
import hu.wukki.tv.ui.settings.*
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Executes reducer effects and shared touch/navigation actions against the current session. */
internal class AppSessionController(
    private val session: AppSessionState,
    private val model: WukkiModel,
    private val scope: CoroutineScope,
    private val guideState: EpgGuideState,
    private val guideDataSource: GuideDataSource,
    private val androidSettingsNavigation: Boolean,
    private val requireDoubleBackToExit: Boolean,
    private val onExitConfirmation: (String) -> Unit
) {
    private val mainSections get() = DashboardSection.entries
    private val visibleChannels get() = model.filteredChannels()
    private val visibleChannelIds get() = visibleChannels.map { it.id }

    fun dismissLiveChannelPreview(hidePanel: Boolean = false) {
        with(session) {
            if (liveChannelPreviewState.isActive) {
                liveChannelPreviewState = liveChannelPreviewState.copy(
                    channelId = null,
                    interactionSequence = liveChannelPreviewState.interactionSequence + 1
                )
            }
            if (hidePanel) programmeOverlayVisible = false
        }
    }

    fun handleLiveNavigation(event: LiveNavigationVisibilityEvent) {
        with(session) {
            val result = liveNavigationState.reduce(event)
            liveNavigationState = result.state
            when (result.effect) {
                LiveNavigationVisibilityEffect.NONE -> Unit
                LiveNavigationVisibilityEffect.FOCUS_NAVIGATION -> {
                    mainNavigationIndex = mainSections.indexOf(DashboardSection.LIVE).coerceAtLeast(0)
                    focusZone = TvFocusZone.MAIN_NAVIGATION
                }
                LiveNavigationVisibilityEffect.FOCUS_CONTENT_IF_NAVIGATION_FOCUSED -> {
                    if (focusZone == TvFocusZone.MAIN_NAVIGATION) focusZone = TvFocusZone.CONTENT
                }
            }
        }
    }

    fun activateSection(section: DashboardSection) {
        with(session) {
            exitConfirmationState = ExitConfirmationState()
            val previousSection = activeSection
            if (section == DashboardSection.LIVE && previousSection != DashboardSection.LIVE) {
                handleLiveNavigation(LiveNavigationVisibilityEvent.EnterLive)
            } else if (section != DashboardSection.LIVE && previousSection == DashboardSection.LIVE) {
                handleLiveNavigation(LiveNavigationVisibilityEvent.LeaveLive)
            }
            if (section != DashboardSection.LIVE) dismissLiveChannelPreview(hidePanel = true)
            if (section == DashboardSection.CHANNELS && activeSection != DashboardSection.CHANNELS) {
                val visibleChannels = model.filteredChannels()
                channelListIndex = activeChannelIndex(visibleChannels.map { it.id }, model.selectedChannelId)
                channelFocusedId = visibleChannels.getOrNull(channelListIndex)?.id
                channelRemoteFocus = ChannelRemoteFocus.LIST
                channelListOpenRequest++
            }
            if (section == DashboardSection.GUIDE && activeSection != DashboardSection.GUIDE) {
                guideState.focusCurrentProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd()), tick)
            }
            if (androidSettingsNavigation && section == DashboardSection.SETTINGS && activeSection != DashboardSection.SETTINGS) {
                settingsNavigation = settingsNavigation.copy(section = null, option = null)
            }
            activeSection = section
            mainNavigationIndex = mainSections.indexOf(section).coerceAtLeast(0)
            focusZone = TvFocusZone.CONTENT
        }
    }

    fun selectChannelPreview(channelId: String) {
        with(session) {
            val index = visibleChannelIds.indexOf(channelId)
            if (index < 0) return
            channelListIndex = index
            channelFocusedId = channelId
            channelRemoteFocus = ChannelRemoteFocus.LIST
        }
    }

    fun openChannelFromBrowser(channelId: String) {
        with(session) {
            model.selectChannel(channelId)
            activateSection(DashboardSection.LIVE)
            overlayRequest++
        }
    }

    fun openGuideProgrammeChannel(channelId: String) {
        with(session) {
            model.selectChannel(channelId)
            guideProgrammeDetailsVisible = false
            activateSection(DashboardSection.LIVE)
            overlayRequest++
        }
    }

    fun switchLiveChannel(delta: Int) {
        with(session) {
            channelNumberInput = ""
            dismissLiveChannelPreview()
            model.moveChannel(delta)
            overlayRequest++
        }
    }

    fun toggleLiveProgrammeInfo() {
        with(session) {
            if (programmeOverlayVisible) {
                dismissLiveChannelPreview(hidePanel = true)
            } else {
                dismissLiveChannelPreview()
                overlayRequest++
            }
        }
    }

    fun handleLiveChannelPreview(event: LiveChannelPreviewEvent): Boolean {
        return with(session) {
            val result = liveChannelPreviewState.reduce(
                event = event,
                channelIds = model.filteredChannels().map { it.id },
                activeChannelId = model.selectedChannelId,
                panelVisible = programmeOverlayVisible
            )
            liveChannelPreviewState = result.state
            when (result.effect) {
                LiveChannelPreviewEffect.NONE -> if (result.handled) overlayRequest++
                LiveChannelPreviewEffect.DISMISS -> programmeOverlayVisible = false
                LiveChannelPreviewEffect.OPEN_CHANNEL -> result.channelIdToOpen?.let { channelId ->
                    if (channelId != model.selectedChannelId) model.selectChannel(channelId)
                    overlayRequest++
                }
            }
            return result.handled
        }
    }

    fun showGuideProgrammeDetails() {
        with(session) {
            if (guideState.focusedProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd())) != null) {
                guideProgrammeDetailsVisible = true
                guideProgrammeDialogState = GuideProgrammeDialogState()
            }
        }
    }

    fun handleGuideProgrammeDialogEvent(dialogEvent: GuideProgrammeDialogEvent) {
        with(session) {
            val transition = guideProgrammeDialogState.reduce(dialogEvent)
            guideProgrammeDialogState = transition.state
            when (transition.effect) {
                GuideProgrammeDialogEffect.DISMISS -> guideProgrammeDetailsVisible = false
                GuideProgrammeDialogEffect.OPEN_CHANNEL -> guideState.focusedProgramme(guideDataSource, guideTimeline(tick, model.guideLatestProgrammeEnd()))?.first?.let { channel ->
                    openGuideProgrammeChannel(channel.id)
                } ?: run { guideProgrammeDetailsVisible = false }
                GuideProgrammeDialogEffect.NONE -> Unit
            }
        }
    }

    fun applySettingsEffect(effect: SettingsNavigationEffect) {
        with(session) {
            when (effect) {
                SettingsNavigationEffect.None -> Unit
                SettingsNavigationEffect.ExitToMainMenu -> focusZone = TvFocusZone.MAIN_NAVIGATION
                is SettingsNavigationEffect.Adjust -> model.adjustSetting(effect.option, effect.delta)
                is SettingsNavigationEffect.Activate -> when (val option = effect.option) {
                    PlaybackSettingsOption.AUTOPLAY -> model.adjustSetting(option, 1)
                    PlaybackSettingsOption.BUFFER, PlaybackSettingsOption.ASPECT_RATIO, LanguageSettingsOption.LANGUAGE -> {
                        settingsDropdownOptionIndex = settingsNavigation.optionIndex
                        settingsDropdownOpenRequest++
                    }
                    PlaybackSettingsOption.RECONNECT -> model.adjustSetting(option, 1)
                    PlaybackSettingsOption.RETRIES -> model.adjustSetting(option, 1)
                    PlaybackSettingsOption.VOLUME -> Unit
                    is DisplaySettingsOption -> model.adjustSetting(option, 1)
                    EpgSettingsOption.SCHEDULE -> model.adjustSetting(option, 1)
                    EpgSettingsOption.REFRESH -> scope.launch { model.refreshOfficialEpg() }
                    PlaylistSettingsOption.SCHEDULE -> model.adjustSetting(option, 1)
                    PlaylistSettingsOption.REFRESH -> scope.launch { model.refreshOfficialPlaylist() }
                    is AboutSettingsOption -> if (
                        option == AboutSettingsOption.PRIVACY || option == AboutSettingsOption.LICENSES
                    ) settingsAboutOpenRequest++
                    is ParentalSettingsOption -> Unit
                }
            }
        }
    }

    fun applyBackEffect(effect: AppBackNavigationEffect): Boolean {
        return with(session) {
            return when (effect) {
                AppBackNavigationEffect.DISMISS_GUIDE_DIALOG -> {
                    // Keep the reducer's exit confirmation state.
                    guideProgrammeDetailsVisible = false
                    true
                }
                AppBackNavigationEffect.CLOSE_CHANNEL_SEARCH -> {
                    // Keep the reducer's exit confirmation state.
                    model.setChannelQuery("")
                    channelSearchOpen = false
                    channelRemoteFocus = ChannelRemoteFocus.LIST
                    true
                }
                AppBackNavigationEffect.DISMISS_LIVE_OVERLAY -> {
                    // Keep the reducer's exit confirmation state.
                    dismissLiveChannelPreview(hidePanel = true)
                    true
                }
                AppBackNavigationEffect.CLOSE_SETTINGS_DETAIL -> {
                    // Keep the reducer's exit confirmation state.
                    settingsNavigation = settingsNavigation.copy(section = null, option = null)
                    true
                }
                AppBackNavigationEffect.FOCUS_MAIN_NAVIGATION -> {
                    // Keep the reducer's exit confirmation state.
                    mainNavigationIndex = mainSections.indexOf(activeSection).coerceAtLeast(0)
                    focusZone = TvFocusZone.MAIN_NAVIGATION
                    true
                }
                AppBackNavigationEffect.EXIT_APPLICATION -> false
            }
        }
    }

    fun dispatchRemote(key: AppRemoteKey): Boolean {
        return with(session) {
            val result = AppRemoteState(
                section = activeSection, focus = focusZone, menuIndex = mainNavigationIndex,
                exitConfirmation = exitConfirmationState, requireDoubleBack = requireDoubleBackToExit,
                nowMillis = System.currentTimeMillis(),
                settings = settingsNavigation,
                channels = ChannelNavigationState(channelRemoteFocus, channelFilterIndex, channelListIndex),
                filterCount = model.categories().size + 2, channelIds = visibleChannelIds,
                selectedChannelId = model.selectedChannelId, searchHasText = model.query.isNotEmpty(),
                searchOpen = channelSearchOpen, dialogVisible = guideProgrammeDetailsVisible,
                overlayVisible = programmeOverlayVisible, preview = liveChannelPreviewState,
                number = channelNumberInput, navigationVisible = liveNavigationState.visible
            ).reduce(key)
            val next = result.state
            exitConfirmationState = next.exitConfirmation
            focusZone = next.focus
            mainNavigationIndex = next.menuIndex
            settingsNavigation = next.settings
            channelRemoteFocus = next.channels.focus
            channelFilterIndex = next.channels.filterIndex
            channelListIndex = next.channels.channelIndex
            channelSearchOpen = next.searchOpen
            guideProgrammeDetailsVisible = next.dialogVisible
            programmeOverlayVisible = next.overlayVisible
            liveChannelPreviewState = next.preview
            channelNumberInput = next.number
            var handled = result.handled
            result.effects.forEach { effect ->
                when (effect) {
                    AppRemoteEffect.ShowExitHint -> onExitConfirmation(tr(model.settings.language, "app.exit.confirm"))
                    AppRemoteEffect.ResetExit -> exitConfirmationState = ExitConfirmationState()
                    is AppRemoteEffect.Dialog -> handleGuideProgrammeDialogEvent(effect.event)
                    is AppRemoteEffect.Back -> handled = applyBackEffect(effect.effect)
                    AppRemoteEffect.RevealNavigation -> {
                        // Keep the reducer's exit confirmation state.
                        handleLiveNavigation(LiveNavigationVisibilityEvent.Reveal(focusNavigation = true))
                    }
                    is AppRemoteEffect.SwitchChannel -> switchLiveChannel(effect.delta)
                    is AppRemoteEffect.ActivateSection -> activateSection(effect.section)
                    AppRemoteEffect.InteractNavigation -> handleLiveNavigation(LiveNavigationVisibilityEvent.Interact)
                    AppRemoteEffect.ShowGuideDetails -> showGuideProgrammeDetails()
                    is AppRemoteEffect.GuideKey -> guideState.handleRemoteKey(effect.key, guideDataSource, scope, guideTimeline(tick, model.guideLatestProgrammeEnd()))
                    is AppRemoteEffect.SelectNumber -> if (model.selectChannelByNumber(effect.number)) overlayRequest++
                    AppRemoteEffect.ShowOverlay -> overlayRequest++
                    is AppRemoteEffect.Preview -> when (effect.result.effect) {
                        LiveChannelPreviewEffect.NONE -> if (effect.result.handled) overlayRequest++
                        LiveChannelPreviewEffect.DISMISS -> programmeOverlayVisible = false
                        LiveChannelPreviewEffect.OPEN_CHANNEL -> effect.result.channelIdToOpen?.let { id ->
                            if (id != model.selectedChannelId) model.selectChannel(id)
                            overlayRequest++
                        }
                    }
                    is AppRemoteEffect.Settings -> applySettingsEffect(effect.effect)
                    is AppRemoteEffect.Channels -> when (val action = effect.effect) {
                        ChannelNavigationEffect.None -> Unit
                        ChannelNavigationEffect.ExitToMainMenu -> focusZone = TvFocusZone.MAIN_NAVIGATION
                        is ChannelNavigationEffect.ActivateFilter -> {
                            when (action.index) {
                                0 -> model.showAllChannels()
                                1 -> model.showFavoriteChannels()
                                else -> model.showChannelCategory(model.categories()[action.index - 2])
                            }
                            channelListIndex = 0
                        }
                        is ChannelNavigationEffect.OpenChannel -> visibleChannels.getOrNull(action.index)?.let { openChannelFromBrowser(it.id) }
                        is ChannelNavigationEffect.ToggleFavorite -> visibleChannels.getOrNull(action.index)?.let { model.toggleFavorite(it.id) }
                    }
                }
            }
            return handled
        }
    }

    fun handleBackNavigation(): Boolean = dispatchRemote(AppRemoteKey(back = true))

    val callbacks: DashboardCallbacks
        get() = with(session) {
            DashboardCallbacks(
                onSectionChange = { section ->
                    if (section == DashboardSection.LIVE && activeSection == DashboardSection.LIVE) {
                        handleLiveNavigation(LiveNavigationVisibilityEvent.Interact)
                    }
                    activateSection(section)
                },
                onSettingsSectionChange = { section ->
                    val categoryIndex = section?.let { SettingsSection.entries.indexOf(it).coerceAtLeast(0) }
                        ?: settingsNavigation.categoryIndex
                    settingsNavigation = SettingsNavigationState(
                        section = section,
                        categoryIndex = categoryIndex,
                        option = section?.options()?.firstOrNull()
                    )
                },
                onChannelPreviewSelect = ::selectChannelPreview,
                onOpenChannel = ::openChannelFromBrowser,
                onChannelSearchOpenChange = { open ->
                    channelSearchOpen = open
                    if (open) {
                        channelRemoteFocus = ChannelRemoteFocus.SEARCH
                    } else if (channelRemoteFocus == ChannelRemoteFocus.SEARCH) {
                        channelRemoteFocus = ChannelRemoteFocus.LIST
                    }
                },
                onSettingsCategoryFocus = { index ->
                    settingsNavigation = settingsNavigation.copy(categoryIndex = index.coerceIn(0, SettingsSection.entries.lastIndex))
                },
                onSettingsOptionFocus = { index ->
                    settingsNavigation.section?.options()?.getOrNull(index)?.let { option ->
                        settingsNavigation = settingsNavigation.copy(option = option)
                    }
                },
                onShowGuideProgrammeDetails = ::showGuideProgrammeDetails,
                onDismissGuideProgrammeDetails = { guideProgrammeDetailsVisible = false },
                onOpenGuideProgrammeChannel = ::openGuideProgrammeChannel,
                onGuideProgrammeDialogEvent = ::handleGuideProgrammeDialogEvent
            )
        }

    val liveVideoGestures = LiveVideoGestures(
        onTap = ::toggleLiveProgrammeInfo,
        onNextChannel = { switchLiveChannel(1) },
        onPreviousChannel = { switchLiveChannel(-1) },
        onShowNavigation = {
            handleLiveNavigation(LiveNavigationVisibilityEvent.Reveal(focusNavigation = false))
        }
    )
}
