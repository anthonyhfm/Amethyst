package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absoluteFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

actual object Zip {
    actual fun getEntries(
        file: PlatformFile,
    ): List<ZipEntry> {
        val file: FileInputStream = file.file.let {
            if (!it.exists() || !it.isFile) {
                return emptyList()
            }

            return@let it.inputStream()
        }

        val zipFile = ZipInputStream(file)

        val entries = mutableListOf<ZipEntry>()

        var entry = zipFile.nextEntry
        while (entry != null) {
            entries.add(
                ZipEntry(
                    path = entry.name,
                    data = zipFile.readBytes(),
                    isDirectory = entry.isDirectory,
                )
            )
            entry = zipFile.nextEntry
        }

        zipFile.close()

        return entries
    }

    actual fun decode(file: String): ByteArray {
        val _file = File(file).let {
            if (!it.exists() || !it.isFile) {
                return ByteArray(0)
            }

            return@let it.inputStream()
        }

        val zipFile = GZIPInputStream(_file)

        return zipFile.readAllBytes()
    }
}