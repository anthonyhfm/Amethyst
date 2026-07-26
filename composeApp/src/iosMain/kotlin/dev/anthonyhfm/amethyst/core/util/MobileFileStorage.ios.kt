package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object MobileFileStorage {

    fun getDocumentsDirectory(): String? {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        return paths.firstOrNull() as? String
    }

    fun getAmethystDirectory(): String {
        val docs = getDocumentsDirectory() ?: ""
        val amethystDir = if (docs.isNotEmpty()) "$docs/Amethyst" else "Amethyst"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = amethystDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return amethystDir
    }

    actual fun resolvePath(path: String): PlatformFile {
        FileHelper.indexedFiles[path]?.let { return it }

        val amethystDir = getAmethystDirectory()

        val relativePath = when {
            path.contains("/Amethyst/") -> path.substringAfter("/Amethyst/")
            path.contains("Documents/") -> path.substringAfter("Documents/")
            path.startsWith("/") -> path.substringAfterLast("/")
            else -> path.removePrefix("Amethyst/")
        }

        val targetPath = "$amethystDir/$relativePath"
        if (NSFileManager.defaultManager.fileExistsAtPath(targetPath)) {
            val resolvedFile = PlatformFile(targetPath)
            FileHelper.indexedFiles[targetPath] = resolvedFile
            return resolvedFile
        }

        if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            val resolvedFile = PlatformFile(path)
            FileHelper.indexedFiles[path] = resolvedFile
            return resolvedFile
        }

        return PlatformFile(targetPath)
    }

    actual suspend fun copyToPersistentStorage(file: PlatformFile): PlatformFile {
        val bytes = file.readBytes()
        return copyBytesToPersistentStorage(bytes, file.name)
    }

    actual suspend fun copyBytesToPersistentStorage(bytes: ByteArray, filename: String): PlatformFile {
        val amethystDir = getAmethystDirectory()
        val targetPath = "$amethystDir/$filename"

        NSFileManager.defaultManager.createFileAtPath(
            path = targetPath,
            contents = bytes.toNSData(),
            attributes = null,
        )

        val resolvedFile = PlatformFile(targetPath)
        FileHelper.indexedFiles[targetPath] = resolvedFile
        FileHelper.indexedFiles[filename] = resolvedFile
        return resolvedFile
    }
}
