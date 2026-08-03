package hu.wukki.tv

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object LocalStore {
    private val path: Path = Path.of(System.getProperty("user.home"), ".wukki-tv", "state.bin")

    fun load(): AppState = try {
        ObjectInputStream(BufferedInputStream(FileInputStream(path.toFile()))).use { it.readObject() as AppState }
    } catch (_: Exception) {
        AppState()
    }

    fun save(state: AppState) {
        try {
            Files.createDirectories(path.parent)
            val temporaryPath = path.resolveSibling("state.tmp")
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(temporaryPath.toFile()))).use { it.writeObject(state) }
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            // A futó munkamenet megmarad akkor is, ha a helyi mentés sikertelen.
        }
    }
}
