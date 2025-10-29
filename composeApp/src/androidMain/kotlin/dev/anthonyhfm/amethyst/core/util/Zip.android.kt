package dev.anthonyhfm.amethyst.core.util

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absoluteFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

actual object Zip {
    actual fun getEntries(
        file: PlatformFile,
    ): List<ZipEntry> {
        val data: ByteArray = runBlocking(Dispatchers.IO) {
            file.readBytes()
        }

        val zipFile = ZipInputStream(data.inputStream())

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
        val data: ByteArray = runBlocking(Dispatchers.IO) {
            file.readBytes()
        }

        val zipFile = ZipInputStream(data.inputStream())

        val entries = mutableListOf<String>()

        var entry = zipFile.nextEntry
        while (entry != null) {
            entries.add(entry.name)
            entry = zipFile.nextEntry
        }

        zipFile.close()

        return entries
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    actual fun decode(data: ByteArray): ByteArray {
        val zipFile = GZIPInputStream(data.inputStream())

        return zipFile.readAllBytes()
    }
}