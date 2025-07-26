package dev.anthonyhfm.amethyst.core.util

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

actual object Zip {
    actual fun getEntries(
        file: String,
    ): List<ZipEntry> {
        val file: FileInputStream = File(file).let {
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
                    isDirectory = entry.isDirectory,
                )
            )
            entry = zipFile.nextEntry
        }

        zipFile.close()

        return entries
    }

    actual fun getInputStream(zipPath: String, file: String): ByteArray {
        val _file = File(zipPath).let {
            if (!it.exists() || !it.isFile) {
                return ByteArray(0)
            }

            return@let it.inputStream()
        }

        val zipFile = ZipInputStream(_file)

        var entry = zipFile.nextEntry
        while (entry != null) {
            if (entry.name == file) {
                val bytes = zipFile.readBytes()
                zipFile.close()
                return bytes
            }
            entry = zipFile.nextEntry
        }

        zipFile.close()
        return ByteArray(0)
    }
}