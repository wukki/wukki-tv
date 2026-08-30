package hu.wukki.tv

import java.io.ObjectOutputStream
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStateMigrationTest {
    @Test
    fun `legacy binary state is migrated to json`() {
        val directory = Files.createTempDirectory("wukki-state-migration")
        try {
            val state = AppState(lastChannelId = "rtl", settings = AppSettings(language = AppLanguage.ENGLISH))
            ObjectOutputStream(Files.newOutputStream(directory.resolve("state.bin"))).use { it.writeObject(state) }

            val migrated = DesktopStateStore(directory).load()

            assertEquals("rtl", migrated.lastChannelId)
            assertEquals(AppLanguage.ENGLISH, migrated.settings?.language)
            assertTrue(Files.isRegularFile(directory.resolve("state.json")))
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
