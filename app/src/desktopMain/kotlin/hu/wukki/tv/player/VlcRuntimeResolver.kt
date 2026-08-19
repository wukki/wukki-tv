package hu.wukki.tv

import java.io.File

internal data class VlcRuntime(val home: File, val pluginDirectory: File?) {
    val factoryArguments: Array<String>
        get() = buildList {
            add("--no-video-title-show")
            add("--quiet")
            // The application bundle intentionally excludes VLC's timestamp-sensitive plugins.dat.
            add("--no-plugins-cache")
            // Callback rendering needs CPU-readable frames. This avoids a macOS hardware decoder
            // producing a native surface without delivering frames to vlcj's render callback.
            add("--avcodec-hw=none")
        }.toTypedArray()
}

internal enum class VlcRuntimeIssue {
    MISSING,
    VIDEO_PLUGIN_MISSING
}

internal data class VlcRuntimeResolution(
    val runtime: VlcRuntime?,
    val issue: VlcRuntimeIssue = VlcRuntimeIssue.MISSING
)

/** Finds a packaged runtime first, then a developer's locally installed VLC runtime. */
internal object VlcRuntimeResolver {
    fun find(): VlcRuntime? = resolve().runtime

    fun resolve(): VlcRuntimeResolution {
        val configured = sequenceOf(
            System.getProperty("wukki.vlc.home"),
            System.getenv("WUKKI_VLC_HOME"),
            File(System.getProperty("user.dir"), "runtime/vlc").absolutePath
        ).filterNotNull().map(::File)
        val candidates = sequenceOf(configured, packagedCandidates().asSequence(), systemCandidates().asSequence())
            .flatten()
            .toList()
        val runtime = candidates.firstOrNull(::isRuntime)
        if (runtime == null) {
            val hasMissingVideoPlugin = isMacOs() && candidates.any(::hasLibraries)
            return VlcRuntimeResolution(
                runtime = null,
                issue = if (hasMissingVideoPlugin) VlcRuntimeIssue.VIDEO_PLUGIN_MISSING else VlcRuntimeIssue.MISSING
            )
        }
        configureNativePath(runtime)
        return VlcRuntimeResolution(VlcRuntime(runtime, pluginDirectory(runtime)))
    }

    private fun packagedCandidates(): List<File> {
        val codeSource = runCatching { File(PlaybackController::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
        return buildList {
            codeSource?.parentFile?.let { directory ->
                add(File(directory, "resources/runtime/vlc")); add(File(directory, "../resources/runtime/vlc")); add(File(directory, "../runtime/vlc"))
            }
            add(File(System.getProperty("user.dir"), "app/resources/runtime/vlc"))
        }
    }

    private fun systemCandidates(): List<File> = when {
        System.getProperty("os.name").startsWith("Mac", true) -> listOf(File("/Applications/VLC.app/Contents/MacOS"))
        System.getProperty("os.name").startsWith("Windows", true) -> listOfNotNull(
            System.getenv("ProgramFiles")?.let { File(it, "VideoLAN/VLC") }, System.getenv("ProgramFiles(x86)")?.let { File(it, "VideoLAN/VLC") }
        )
        else -> listOf(File("/usr/lib/x86_64-linux-gnu"), File("/usr/lib64"), File("/usr/lib"))
    }

    private fun isRuntime(home: File): Boolean = hasLibraries(home) && (!isMacOs() || hasCallbackVideoPlugin(home))
    private fun hasLibraries(home: File) = libraryCandidates(home).any(File::isFile)
    private fun hasCallbackVideoPlugin(home: File): Boolean = pluginDirectory(home)
        ?.let { File(it, "libvmem_plugin.dylib").isFile } == true

    private fun isMacOs() = System.getProperty("os.name").startsWith("Mac", true)
    private fun configureNativePath(home: File) {
        val directory = libraryCandidates(home).firstOrNull(File::isFile)?.parentFile ?: home
        val existing = System.getProperty("jna.library.path").orEmpty()
        System.setProperty("jna.library.path", listOf(directory.absolutePath, existing).filter(String::isNotBlank).joinToString(File.pathSeparator))
    }
    private fun libraryCandidates(home: File): List<File> = when {
        isMacOs() -> listOf(File(home, "lib/libvlc.dylib"), File(home, "libvlc.dylib"))
        System.getProperty("os.name").startsWith("Windows", true) -> listOf(File(home, "libvlc.dll"))
        else -> listOf(File(home, "libvlc.so"), File(home, "lib/libvlc.so"))
    }
    private fun pluginDirectory(home: File): File? = listOf(File(home, "plugins"), File(home, "lib/vlc/plugins"))
        .firstOrNull(File::isDirectory)
}
