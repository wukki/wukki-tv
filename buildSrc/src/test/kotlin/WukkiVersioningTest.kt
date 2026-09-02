import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WukkiVersioningTest {
    @Test
    fun `formats the first day of the year`() {
        val version = WukkiVersioning.calculate(LocalDate.of(2026, 1, 1), "8001345Fabcdef01", 42)

        assertEquals("26.001.8001345f", version.displayVersion)
        assertEquals("8001345f", version.buildId)
        assertEquals("26.0.42", version.packageVersion)
        assertEquals(260010042, version.androidVersionCode)
    }

    @Test
    fun `supports the last day of a leap year`() {
        val version = WukkiVersioning.calculate(LocalDate.of(2028, 12, 31), "abcdef0123456789", 9999)

        assertEquals("28.366.abcdef01", version.displayVersion)
        assertEquals("28.182.9999", version.packageVersion)
        assertEquals(283669999, version.androidVersionCode)
    }

    @Test
    fun `uses day 365 on the last day of a regular year`() {
        val version = WukkiVersioning.calculate(LocalDate.of(2027, 12, 31), "0123456789abcdef", 1)

        assertEquals("27.365.01234567", version.displayVersion)
        assertEquals("27.182.1", version.packageVersion)
        assertEquals(273650001, version.androidVersionCode)
    }

    @Test
    fun `uses a monotonically increasing code for later days and runs`() {
        val first = WukkiVersioning.calculate(LocalDate.of(2026, 9, 2), "11111111abcdef00", 100)
        val laterRun = WukkiVersioning.calculate(LocalDate.of(2026, 9, 2), "22222222abcdef00", 101)
        val laterDay = WukkiVersioning.calculate(LocalDate.of(2026, 9, 3), "33333333abcdef00", 1)

        assert(laterRun.androidVersionCode > first.androidVersionCode)
        assert(laterDay.androidVersionCode > laterRun.androidVersionCode)
    }

    @Test
    fun `rejects invalid commit identifiers and oversized Android run numbers`() {
        assertFailsWith<IllegalArgumentException> {
            WukkiVersioning.calculate(LocalDate.of(2026, 1, 1), "not-a-sha", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            WukkiVersioning.calculate(LocalDate.of(2026, 1, 1), "8001345f", 10_000)
        }
    }
}
