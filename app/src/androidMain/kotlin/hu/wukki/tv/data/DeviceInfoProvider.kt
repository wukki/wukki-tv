package hu.wukki.tv

import android.content.Context
import android.os.Build
import java.util.UUID

/** Android-safe diagnostics; no hardware or network identifier is collected. */
data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val installationId: String,
    val appDataBytes: Long,
    val availableStorageBytes: Long
)

object DeviceInfoProvider {
    private var context: Context? = null

    fun install(value: Context) { context = value.applicationContext }

    fun collect(): DeviceInfo {
        val appContext = context
        val preferences = appContext?.getSharedPreferences("wukki_device", Context.MODE_PRIVATE)
        val id = preferences?.getString("installation_id", null)?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: UUID.randomUUID().toString().also { preferences?.edit()?.putString("installation_id", it)?.apply() }
        val directory = appContext?.filesDir
        val available = directory?.usableSpace ?: 0L
        return DeviceInfo(
            platform = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Android" },
            osVersion = "Android ${Build.VERSION.RELEASE}",
            installationId = id,
            appDataBytes = directorySize(directory),
            availableStorageBytes = available
        )
    }

    private fun directorySize(directory: java.io.File?): Long = directory?.walkTopDown()
        ?.filter { it.isFile }
        ?.sumOf { it.length() }
        ?: 0L
}

internal fun formatByteSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return if (unit == 0) "${bytes.coerceAtLeast(0L)} ${units[unit]}" else "%.1f %s".format(java.util.Locale.ROOT, value, units[unit])
}
