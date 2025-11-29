package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import sun.nio.cs.UTF_8
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream
import kotlin.io.encoding.Base64


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

    actual fun getPaths(file: PlatformFile): List<String> {
        val file: FileInputStream = file.file.let {
            if (!it.exists() || !it.isFile) {
                return emptyList()
            }

            return@let it.inputStream()
        }

        val zipFile = ZipInputStream(file)

        val entries = mutableListOf<String>()

        var entry = zipFile.nextEntry
        while (entry != null) {
            entries.add(entry.name)
            entry = zipFile.nextEntry
        }

        zipFile.close()

        return entries
    }

    actual fun decode(data: ByteArray): ByteArray {
        val zipFile = GZIPInputStream(data.inputStream())

        return zipFile.readAllBytes()
    }

    actual fun encode(data: ByteArray): ByteArray {
        ByteArrayOutputStream(data.size).use { out ->
            GZIPOutputStream(out).use { gzip ->
                gzip.write(data)
            }

            return out.toByteArray()
        }
    }
}