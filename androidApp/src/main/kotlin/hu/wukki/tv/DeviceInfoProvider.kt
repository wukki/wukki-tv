package hu.wukki.tv

import android.content.Context
import android.os.Build
import java.util.UUID

/** Android-safe diagnostics; no hardware or network identifier is collected. */
class AndroidDeviceInfoProvider(context: Context) : DeviceInfoProvider {
    private val appContext = context.applicationContext

    override fun collect(): DeviceInfo {
        val preferences = appContext.getSharedPreferences("wukki_device", Context.MODE_PRIVATE)
        val id = preferences?.getString("installation_id", null)?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: UUID.randomUUID().toString().also { preferences?.edit()?.putString("installation_id", it)?.apply() }
        val directory = appContext.filesDir
        val available = directory.usableSpace
        return DeviceInfo(
            platform = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Android" },
            osVersion = "Android ${Build.VERSION.RELEASE}",
            installationId = id,
            appDataBytes = directorySize(directory),
            availableStorageBytes = available
        )
    }

    private fun directorySize(directory: java.io.File): Long = directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}
