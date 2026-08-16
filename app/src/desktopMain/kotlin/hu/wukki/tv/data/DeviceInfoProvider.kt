package hu.wukki.tv

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Locally available support information; it never reads hardware or network identifiers. */
data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val installationId: String,
    val appDataBytes: Long,
    val availableStorageBytes: Long
)

object DeviceInfoProvider {
    private val appDirectory: Path
        get() = Path.of(System.getProperty("user.home"), ".wukki-tv")

    fun collect(): DeviceInfo {
        val directory = appDirectory
        runCatching { Files.createDirectories(directory) }
        return DeviceInfo(
            platform = listOfNotNull(
                System.getProperty("os.name")?.takeIf { it.isNotBlank() },
                System.getProperty("os.arch")?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Unknown" },
            osVersion = System.getProperty("os.version").orEmpty().ifBlank { "Unknown" },
            installationId = installationId(directory),
            appDataBytes = directorySize(directory),
            availableStorageBytes = runCatching { Files.getFileStore(directory).usableSpace }.getOrDefault(0L)
        )
    }

    private fun installationId(directory: Path): String {
        val path = directory.resolve("device-id")
        val stored = runCatching { Files.readString(path).trim() }.getOrNull()
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        if (stored != null) return stored

        val created = UUID.randomUUID().toString()
        return runCatching {
            Files.writeString(path, created)
            created
        }.getOrDefault(created)
    }

    private fun directorySize(directory: Path): Long = runCatching {
        if (!Files.exists(directory)) return@runCatching 0L
        Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .mapToLong { path -> runCatching { Files.size(path) }.getOrDefault(0L) }
                .sum()
        }
    }.getOrDefault(0L)
}

internal fun formatByteSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$safeBytes ${units[unit]}" else "%.1f %s".format(java.util.Locale.ROOT, value, units[unit])
}
