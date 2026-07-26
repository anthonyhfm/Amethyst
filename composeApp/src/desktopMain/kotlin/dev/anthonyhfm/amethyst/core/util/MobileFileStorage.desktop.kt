package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import java.io.File

actual object MobileFileStorage {
    actual fun resolvePath(path: String): PlatformFile =
        FileHelper.indexedFiles[path] ?: PlatformFile(path)

    actual suspend fun copyToPersistentStorage(file: PlatformFile): PlatformFile = file

    actual suspend fun copyBytesToPersistentStorage(bytes: ByteArray, filename: String): PlatformFile {
        val destFile = File(filename)
        destFile.writeBytes(bytes)
        return PlatformFile(destFile)
    }
}
