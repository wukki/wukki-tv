package hu.wukki.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import hu.wukki.tv.ui.channels.ChannelBrowserCallbacks
import hu.wukki.tv.ui.channels.ChannelBrowserRowUiState
import hu.wukki.tv.ui.channels.ChannelBrowserScreen
import hu.wukki.tv.ui.channels.ChannelBrowserUiState
import hu.wukki.tv.ui.channels.ChannelEmptyState
import hu.wukki.tv.ui.components.WukkiColorScheme
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ChannelHistoryBrowserSnapshotTest {
    @Test
    fun `recent filter and previous action render with accessible state`() {
        renderHistory(empty = false)
        renderHistory(empty = true)
    }

    private fun renderHistory(empty: Boolean) {
        var previousClicks = 0
        val channel = Channel("1", "playlist", "Film TV", "https://example.test/live", null, null, group = "Film", logo = null)
        val state =
            ChannelBrowserUiState(
                language = AppLanguage.HUNGARIAN,
                categories = listOf("Film"),
                query = "",
                selectedCategory = null,
                onlyFavorites = false,
                channels = if (empty) emptyList() else listOf(ChannelBrowserRowUiState(channel, 1, null, null)),
                displayMode = ChannelListDisplayMode.NORMAL,
                showChannelProgramme = true,
                showMiniGuide = false,
                showLogos = false,
                showProgrammeImages = false,
                playingChannelId = "1",
                emptyState = if (empty) ChannelEmptyState.NO_RECENT else null,
                playlistRefreshing = false,
                preview = null,
                onlyRecent = true,
                hasPreviousChannel = !empty,
            )
        val callbacks = ChannelBrowserCallbacks({}, {}, {}, {}, {}, {}, {}, {}, onPreviousChannel = { previousClicks++ })
        val scene =
            ImageComposeScene(width = 1280, height = 720) {
                MaterialTheme(colorScheme = WukkiColorScheme) {
                    ChannelBrowserScreen(state, callbacks, Modifier.fillMaxSize(), 1f, ChannelRemoteFocus.FILTERS, 2, 0, 0, false, {})
                }
            }
        try {
            repeat(3) { scene.render(it * 16_000_000L).close() }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val recent = nodes.first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Legutóbb nézett" } == true }
            assertTrue(recent.config.getOrNull(SemanticsProperties.Selected) == true)
            val previous = nodes.first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Előző csatorna" } == true }
            assertEquals(empty, previous.config.contains(SemanticsProperties.Disabled))
            if (!empty) {
                assertNotNull(previous.config.getOrNull(SemanticsActions.OnClick)?.action).invoke()
                assertEquals(1, previousClicks)
            }
            val output = Path.of("build", "reports", "ux08", if (empty) "empty-history.png" else "recent-history.png")
            Files.createDirectories(output.parent)
            scene.render(64_000_000L).use { image ->
                image.encodeToData()!!.use { Files.write(output, it.bytes) }
            }
        } finally {
            scene.close()
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
