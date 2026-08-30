package hu.wukki.tv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Prevents user-idle display sleep while the desktop window owns the foreground. */
interface ScreenWakeController {
    fun acquire()
    fun release()
}

/** Keeps platform calls balanced even when focus and window-state events arrive repeatedly. */
class ScreenWakeCoordinator(private val controller: ScreenWakeController) {
    private var acquired = false

    fun update(shouldKeepAwake: Boolean) {
        if (shouldKeepAwake == acquired) return
        acquired = shouldKeepAwake
        if (shouldKeepAwake) controller.acquire() else controller.release()
    }

    fun close() = update(false)
}

fun createDesktopScreenWakeController(osName: String = System.getProperty("os.name")): ScreenWakeController = when {
    osName.startsWith("Mac", ignoreCase = true) -> MacScreenWakeController()
    osName.startsWith("Windows", ignoreCase = true) -> WindowsScreenWakeController()
    else -> LinuxScreenWakeController()
}

private class MacScreenWakeController : ScreenWakeController {
    private var assertionId: Int? = null

    override fun acquire() {
        if (assertionId != null) return
        runCatching {
            val assertionType = CoreFoundation.CFStringRef.createCFString("PreventUserIdleDisplaySleep")
            val assertionName = CoreFoundation.CFStringRef.createCFString("Wukki TV foreground window")
            try {
                val id = IntByReference()
                if (MacPowerManagement.INSTANCE.IOPMAssertionCreateWithName(assertionType, ASSERTION_LEVEL_ON, assertionName, id) == 0) {
                    assertionId = id.value
                }
            } finally {
                CoreFoundation.INSTANCE.CFRelease(assertionType)
                CoreFoundation.INSTANCE.CFRelease(assertionName)
            }
        }
    }

    override fun release() {
        assertionId?.let { id -> runCatching { MacPowerManagement.INSTANCE.IOPMAssertionRelease(id) } }
        assertionId = null
    }

    private interface MacPowerManagement : Library {
        fun IOPMAssertionCreateWithName(
            assertionType: CoreFoundation.CFStringRef,
            assertionLevel: Int,
            assertionName: CoreFoundation.CFStringRef,
            assertionId: IntByReference
        ): Int

        fun IOPMAssertionRelease(assertionId: Int): Int

        companion object {
            val INSTANCE: MacPowerManagement = Native.load("IOKit", MacPowerManagement::class.java)
        }
    }

    private companion object {
        const val ASSERTION_LEVEL_ON = 255
    }
}

private class WindowsScreenWakeController : ScreenWakeController {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wukki-screen-wake").apply { isDaemon = true }
    }

    override fun acquire() = runOnWakeThread {
        WindowsPowerManagement.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS or ES_DISPLAY_REQUIRED)
    }

    override fun release() = runOnWakeThread {
        WindowsPowerManagement.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS)
    }

    private fun runOnWakeThread(action: () -> Unit) {
        runCatching { executor.submit(action).get(2, TimeUnit.SECONDS) }
    }

    private interface WindowsPowerManagement : Library {
        fun SetThreadExecutionState(flags: Int): Int

        companion object {
            val INSTANCE: WindowsPowerManagement = Native.load("kernel32", WindowsPowerManagement::class.java)
        }
    }

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_DISPLAY_REQUIRED = 0x00000002
    }
}

private class LinuxScreenWakeController : ScreenWakeController {
    private var cookie: Int? = null

    override fun acquire() {
        if (cookie != null) return
        val output = runDbus(
            "--session", "--print-reply", "--dest=org.freedesktop.ScreenSaver",
            "/ScreenSaver", "org.freedesktop.ScreenSaver.Inhibit",
            "string:Wukki TV", "string:Foreground Wukki TV window"
        ) ?: return
        cookie = COOKIE_PATTERN.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    override fun release() {
        cookie?.let { token ->
            runDbus(
                "--session", "--dest=org.freedesktop.ScreenSaver",
                "/ScreenSaver", "org.freedesktop.ScreenSaver.UnInhibit", "uint32:$token"
            )
        }
        cookie = null
    }

    private fun runDbus(vararg arguments: String): String? = runCatching {
        val process = ProcessBuilder(listOf("dbus-send") + arguments)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        process.inputStream.bufferedReader().use { output ->
            output.readText().takeIf { process.exitValue() == 0 }
        }
    }.getOrNull()

    private companion object {
        val COOKIE_PATTERN = Regex("""uint32\\s+(\\d+)""")
    }
}
