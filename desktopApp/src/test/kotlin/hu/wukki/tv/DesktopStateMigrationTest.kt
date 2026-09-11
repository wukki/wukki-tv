package hu.wukki.tv

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStateMigrationTest {
    @Test
    fun `legacy binary state is migrated to json`() =
        runBlocking {
            val directory = Files.createTempDirectory("wukki-state-migration")
            try {
                javaClass.getResourceAsStream("/legacy-state.bin").use { fixture ->
                    checkNotNull(fixture)
                    Files.copy(fixture, directory.resolve("state.bin"))
                }

                val migrated = DesktopStateStore(directory).load().state

                assertEquals("rtl", migrated.lastChannelId)
                assertEquals(AppLanguage.ENGLISH, migrated.settings.language)
                assertEquals("RTL", migrated.channels.single().name)
                assertTrue(Files.isRegularFile(directory.resolve("state.json")))
                assertEquals(migrated, DesktopStateStore(directory).load().state)
            } finally {
                Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `domain state does not implement java serialization`() {
        assertFalse(java.io.Serializable::class.java.isAssignableFrom(AppState::class.java))
    }
}
