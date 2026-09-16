package hu.wukki.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import hu.wukki.tv.ui.app.PlaybackQuickSettingsContent
import hu.wukki.tv.ui.components.WukkiColorScheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class PlaybackQuickSettingsSnapshotTest {
    @Test
    fun `panel hides unavailable tracks and exposes supported selections`() {
        render(PlaybackQuickSettings(AspectRatioMode.AUTO), "aspect-only")
        render(
            PlaybackQuickSettings(
                AspectRatioMode.RATIO_16_9,
                listOf(PlaybackTrack("1", "Magyar", true), PlaybackTrack("2", "English", false)),
                listOf(PlaybackTrack("off", null, true), PlaybackTrack("3", "Magyar", false)),
            ),
            "all-options",
        )
    }

    private fun render(
        snapshot: PlaybackQuickSettings,
        name: String,
    ) {
        var change: QuickSetting? = null
        val scene =
            ImageComposeScene(width = 640, height = 480) {
                MaterialTheme(colorScheme = WukkiColorScheme) {
                    PlaybackQuickSettingsContent(snapshot, AppLanguage.HUNGARIAN, { setting, _ -> change = setting }, {})
                }
            }
        try {
            repeat(3) { scene.render(it * 16_000_000L).close() }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            assertEquals(snapshot.audio.size > 1, texts.any { it.startsWith("Hangsáv:") })
            assertEquals(snapshot.subtitles.isNotEmpty(), texts.any { it.startsWith("Felirat:") })
            val aspect = nodes.first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith("Képarány:") } == true }
            assertTrue(aspect.config.getOrNull(SemanticsProperties.Focused) == true)
            assertNotNull(aspect.config.getOrNull(SemanticsActions.OnClick)?.action).invoke()
            assertEquals(QuickSetting.ASPECT, change)
            assertFalse(texts.any { it.contains("⟪") })
            val output = Path.of("build", "reports", "ux09", "$name.png")
            Files.createDirectories(output.parent)
            scene.render(64_000_000L).use { image -> image.encodeToData()!!.use { Files.write(output, it.bytes) } }
        } finally {
            scene.close()
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
