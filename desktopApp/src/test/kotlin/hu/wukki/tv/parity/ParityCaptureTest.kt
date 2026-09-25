package hu.wukki.tv.parity

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test

class ParityCaptureTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun captureReferenceScreens() {
        val output = File(System.getProperty("parity.output")).apply { mkdirs() }
        File(output.parentFile.parentFile, "fixture.json").writeText(
            Json {
                prettyPrint = true
                encodeDefaults = true
            }.encodeToString(ParityReference.state()),
        )
        val width = Integer.getInteger("parity.width", 1920)
        val height = Integer.getInteger("parity.height", 1080)
        SwingUtilities.invokeAndWait {
            ParityReference.scenarios.forEach { scenario ->
                ImageComposeScene(width, height) { ParityReferenceScreen(scenario, androidLayout = false) }.use { scene ->
                    repeat(12) { scene.render(it * 100_000_000L).close() }
                    scene.render(1_200_000_000L).use { image ->
                        image.encodeToData()!!.use { File(output, "$scenario.png").writeBytes(it.bytes) }
                    }
                }
            }
        }
    }
}
