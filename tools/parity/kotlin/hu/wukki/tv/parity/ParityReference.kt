package hu.wukki.tv.parity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AppSettings
import hu.wukki.tv.AppState
import hu.wukki.tv.AspectRatioMode
import hu.wukki.tv.Channel
import hu.wukki.tv.Clock
import hu.wukki.tv.EpgSource
import hu.wukki.tv.LiveVideoGestures
import hu.wukki.tv.OfficialWukkiSource
import hu.wukki.tv.PlaybackQuickSettings
import hu.wukki.tv.PlaybackSettings
import hu.wukki.tv.PlaybackTrack
import hu.wukki.tv.PlaylistDefinition
import hu.wukki.tv.PlaylistSource
import hu.wukki.tv.Programme
import hu.wukki.tv.RemoteTextLoader
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.XmlTvParser
import hu.wukki.tv.ui.app.AppSessionState
import hu.wukki.tv.ui.app.DashboardCallbacks
import hu.wukki.tv.ui.app.DashboardScreen
import hu.wukki.tv.ui.app.PlaybackQuickSettingsPanel
import hu.wukki.tv.ui.components.WukkiColorScheme
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.guide.GuideDataSource
import hu.wukki.tv.ui.guide.GuideTimeline
import hu.wukki.tv.ui.guide.rememberEpgGuideState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.PlaybackSettingsOption
import hu.wukki.tv.ui.navigation.SettingsNavigationState
import hu.wukki.tv.ui.settings.SettingsSection

/** Test/debug only. No network, playback engine, user storage or running wall clock. */
object ParityReference {
    const val NOW = 1_790_006_400_000L // 2026-09-21T16:00:00Z
    val scenarios = listOf("live", "live-empty", "channels", "search", "no-results", "no-data", "favorites", "recent", "guide", "guide-details", "settings", "settings-playback", "settings-epg", "settings-display", "settings-parental", "settings-playlists", "settings-language", "settings-about", "quick-settings", "offline")
    val channels =
        listOf("Hírek", "Kultúra", "Sport", "Film – nagyon hosszú csatornanév a csonkolás ellenőrzéséhez").mapIndexed { i, name ->
            Channel(
                "ref-${i + 1}",
                "reference",
                name,
                "https://example.invalid/${i + 1}.m3u8",
                "ref-${i + 1}",
                name,
                tvgChno = i + 1,
                group = if (i < 2) "Közszolgálati" else "Szórakozás",
                logo = null,
                favorite = i == 0,
                epgChannelId = "ref-${i + 1}",
                epgSourceId = "reference-epg",
            )
        }
    val programmes =
        channels.flatMap { channel ->
            (-2..3).map { offset ->
                Programme(
                    channel.id,
                    if (offset == 0) "Esti műsor – ${channel.name}" else "Műsor ${offset + 3}",
                    NOW - 900_000 + offset * 3_600_000,
                    NOW + 2_700_000 + offset * 3_600_000,
                    "Rögzített műsorleírás. Árvíztűrő tükörfúrógép; több soros tartalom ellenőrzéséhez.",
                )
            }
        }

    fun state(empty: Boolean = false) =
        AppState(
            playlists = listOf(PlaylistDefinition("reference", "Reference", OfficialWukkiSource.PLAYLIST_URL, PlaylistSource.URL, NOW)),
            channels = if (empty) emptyList() else channels,
            lastChannelId = if (empty) null else "ref-1",
            recentChannelIds = if (empty) emptyList() else listOf("ref-2", "ref-1"),
            epgSources = listOf(EpgSource("reference-epg", "Referencia EPG", "https://example.invalid/epg.xml", lastUpdatedAt = NOW, managedByPlaylist = true)),
            epgProgrammesBySource = mapOf("reference-epg" to programmes),
            settings = AppSettings(playback = PlaybackSettings(autoPlayOnLaunch = false)),
        )
}

@Composable
fun ParityReferenceScreen(
    scenario: String,
    androidLayout: Boolean,
) {
    require(scenario in ParityReference.scenarios)
    val model =
        remember(scenario) {
            WukkiModel(
                ParityReference.state(scenario == "no-data" || scenario == "live-empty"),
                RemoteTextLoader { error("Reference capture must not use the network") },
                XmlTvParser { emptyList() },
                {},
                clock = Clock { ParityReference.NOW },
            ).apply {
                when (scenario) {
                    "search" -> setChannelQuery("hir")
                    "no-results" -> setChannelQuery("nincsilyen")
                    "favorites" -> showFavoriteChannels()
                    "recent" -> showRecentChannels()
                    "offline" -> showRawError("Nincs hálózati kapcsolat. A mentett csatornalista látható.")
                }
            }
        }
    val session =
        remember(scenario) {
            AppSessionState(false).apply {
                tick = ParityReference.NOW
                activeSection =
                    when {
                        scenario.startsWith("live") || scenario == "quick-settings" -> DashboardSection.LIVE
                        scenario.startsWith("guide") -> DashboardSection.GUIDE
                        scenario.startsWith("settings") -> DashboardSection.SETTINGS
                        else -> DashboardSection.CHANNELS
                    }
                mainNavigationIndex = activeSection.ordinal
                channelFocusedId = "ref-1"
                channelSearchOpen = scenario == "search" || scenario == "no-results"
                channelRemoteFocus = if (channelSearchOpen) ChannelRemoteFocus.SEARCH else ChannelRemoteFocus.LIST
                if (scenario.startsWith("settings-")) {
                    val section = SettingsSection.valueOf(scenario.removePrefix("settings-").uppercase())
                    settingsNavigation = SettingsNavigationState(section, section.ordinal)
                }
                guideProgrammeDetailsVisible = scenario == "guide-details"
            }
        }
    val data =
        remember(model) {
            object : GuideDataSource {
                override val language get() = model.settings.language
                override val selectedChannelId get() = model.selectedChannelId
                override val showLogos = true

                override fun channels() = model.guideChannels()

                override fun latestProgrammeEnd() = model.guideLatestProgrammeEnd()

                override fun programmesFor(
                    channel: Channel,
                    from: Long,
                    to: Long,
                ) = model.programmesFor(channel, from, to)
            }
        }
    val guide = rememberEpgGuideState()
    remember(guide) {
        guide.focusCurrentProgramme(data, GuideTimeline(ParityReference.NOW - 64_800_000L, ParityReference.NOW + 21_600_000L), ParityReference.NOW)
        true
    }
    val callbacks = DashboardCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        MaterialTheme(
            colorScheme = WukkiColorScheme,
            typography =
                hu.wukki.tv.ui.components
                    .wukkiTypography(),
        ) {
            Surface(Modifier.fillMaxSize(), color = WukkiColors.background, contentColor = WukkiColors.textPrimary) {
                DashboardScreen(
                    session,
                    callbacks,
                    model,
                    rememberCoroutineScope(),
                    data,
                    guide,
                    androidLayout,
                    videoHost = { modifier, _ -> Box(modifier.background(WukkiColors.video)) },
                    liveVideoGestures = LiveVideoGestures({}, {}, {}, {}),
                    playbackEngineLabel = "Reference (no decoder)",
                    playingChannelId = "ref-1",
                )
                if (scenario == "quick-settings") {
                    PlaybackQuickSettingsPanel(
                        PlaybackQuickSettings(aspect = AspectRatioMode.AUTO, audio = listOf(PlaybackTrack("hu", "Magyar", true), PlaybackTrack("en", "English", false))),
                        AppLanguage.HUNGARIAN,
                        { _, _ -> },
                        {},
                    )
                }
            }
        }
    }
}
