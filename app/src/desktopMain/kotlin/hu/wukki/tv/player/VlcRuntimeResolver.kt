package hu.wukki.tv

import java.io.File

internal data class VlcRuntime(val home: File, val pluginDirectory: File?) {
    val factoryArguments: Array<String>
        get() = arrayOf("--no-video-title-show", "--quiet")
}

/** Finds a packaged runtime first, then a developer's locally installed VLC runtime. */
internal object VlcRuntimeResolver {
    fun find(): VlcRuntime? {
        val configured = sequenceOf(
            System.getProperty("wukki.vlc.home"),
            System.getenv("WUKKI_VLC_HOME"),
            File(System.getProperty("user.dir"), "runtime/vlc").absolutePath
        ).filterNotNull().map(::File)
        val runtime = sequenceOf(configured, packagedCandidates().asSequence(), systemCandidates().asSequence())
            .flatten().firstOrNull(::isRuntime) ?: return null
        configureNativePath(runtime)
        return VlcRuntime(runtime, pluginDirectory(runtime))
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

    private fun isRuntime(home: File) = libraryCandidates(home).any(File::isFile)
    private fun configureNativePath(home: File) {
        val directory = libraryCandidates(home).firstOrNull(File::isFile)?.parentFile ?: home
        val existing = System.getProperty("jna.library.path").orEmpty()
        System.setProperty("jna.library.path", listOf(directory.absolutePath, existing).filter(String::isNotBlank).joinToString(File.pathSeparator))
    }
    private fun libraryCandidates(home: File): List<File> = when {
        System.getProperty("os.name").startsWith("Mac", true) -> listOf(File(home, "lib/libvlc.dylib"), File(home, "libvlc.dylib"))
        System.getProperty("os.name").startsWith("Windows", true) -> listOf(File(home, "libvlc.dll"))
        else -> listOf(File(home, "libvlc.so"), File(home, "lib/libvlc.so"))
    }
    private fun pluginDirectory(home: File): File? = listOf(File(home, "plugins"), File(home, "lib/vlc/plugins"))
        .firstOrNull(File::isDirectory)
}
