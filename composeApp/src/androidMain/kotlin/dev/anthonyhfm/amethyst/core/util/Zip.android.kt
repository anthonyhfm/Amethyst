package dev.anthonyhfm.amethyst.core.util

import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

actual object Zip {
    actual fun getEntries(file: String): List<ZipEntry> {
        val fis = File(file).let {
            if (!it.exists() || !it.isFile) return emptyList()
            FileInputStream(it)
        }
        val zis = ZipInputStream(fis)
        val result = mutableListOf<ZipEntry>()
        var e = zis.nextEntry
        while (e != null) {
            result.add(ZipEntry(path = e.name, isDirectory = e.isDirectory))
            e = zis.nextEntry
        }
        zis.close()
        return result
    }

    actual fun getInputStream(zipPath: String, file: String): ByteArray {
        val fis = File(zipPath).let {
            if (!it.exists() || !it.isFile) return ByteArray(0)
            FileInputStream(it)
        }
        val zis = ZipInputStream(fis)
        var e = zis.nextEntry
        while (e != null) {
            if (e.name == file) {
                val bytes = zis.readBytes()
                zis.close()
                return bytes
            }
            e = zis.nextEntry
        }
        zis.close()
        return ByteArray(0)
    }

    actual fun decode(file: String): ByteArray {
        val fis = File(file).let {
            if (!it.exists() || !it.isFile) return ByteArray(0)
            FileInputStream(it)
        }
        return GZIPInputStream(fis).use { it.readBytes() }
    }
}

