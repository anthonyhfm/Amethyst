package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object MobileFileStorage {

    fun getAmethystDirectory(): File {
        val cacheDir = File(FileKit.cacheDir.path)
        val filesDir = cacheDir.parentFile?.resolve("files") ?: cacheDir
        val amethystDir = File(filesDir, "Amethyst")
        if (!amethystDir.exists()) {
            amethystDir.mkdirs()
        }
        return amethystDir
    }

    actual fun resolvePath(path: String): PlatformFile {
        FileHelper.indexedFiles[path]?.let { return it }

        val amethystDir = getAmethystDirectory()

        val relativePath = when {
            path.contains("/Amethyst/") -> path.substringAfter("/Amethyst/")
            path.contains("/files/") -> path.substringAfter("/files/")
            path.startsWith("/") -> path.substringAfterLast("/")
            else -> path.removePrefix("Amethyst/")
        }

        val targetFile = File(amethystDir, relativePath)
        if (targetFile.exists()) {
            val resolvedFile = PlatformFile(targetFile.absolutePath)
            FileHelper.indexedFiles[targetFile.absolutePath] = resolvedFile
            return resolvedFile
        }

        val rawFile = File(path)
        if (rawFile.exists()) {
            val resolvedFile = PlatformFile(rawFile.absolutePath)
            FileHelper.indexedFiles[rawFile.absolutePath] = resolvedFile
            return resolvedFile
        }

        return PlatformFile(targetFile.absolutePath)
    }

    actual suspend fun copyToPersistentStorage(file: PlatformFile): PlatformFile {
        val bytes = file.readBytes()
        return copyBytesToPersistentStorage(bytes, file.name)
    }

    actual suspend fun copyBytesToPersistentStorage(bytes: ByteArray, filename: String): PlatformFile {
        return withContext(Dispatchers.IO) {
            val amethystDir = getAmethystDirectory()
            val targetFile = File(amethystDir, filename)
            targetFile.writeBytes(bytes)

            val resolvedFile = PlatformFile(targetFile.absolutePath)
            FileHelper.indexedFiles[targetFile.absolutePath] = resolvedFile
            FileHelper.indexedFiles[filename] = resolvedFile
            resolvedFile
        }
    }
}
