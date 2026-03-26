package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

actual object Zip {
    actual fun getEntries(
        file: PlatformFile,
    ): List<ZipEntry> {
        val data: ByteArray = runBlocking(Dispatchers.IO) {
            file.readBytes()
        }

        return try {
            readZipFile(data) { zipFile ->
                val entries = mutableListOf<ZipEntry>()

                zipFile.entries().asSequence().forEach { entry ->
                    val entryData = if (entry.isDirectory) {
                        ByteArray(0)
                    } else {
                        zipFile.getInputStream(entry).use { it.readBytes() }
                    }

                    entries.add(
                        ZipEntry(
                            path = entry.name,
                            data = entryData,
                            isDirectory = entry.isDirectory,
                        )
                    )
                }

                entries
            }
        } catch (e: Exception) {
            println("Error reading ZIP file: ${e.message}")
            emptyList()
        }
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        val data: ByteArray = runBlocking(Dispatchers.IO) {
            file.readBytes()
        }

        return try {
            readZipFile(data) { zipFile ->
                zipFile.entries().asSequence()
                    .map { it.name }
                    .toList()
            }
        } catch (e: Exception) {
            println("Error reading ZIP paths: ${e.message}")
            emptyList()
        }
    }

    actual fun decode(data: ByteArray): ByteArray {
        if (data.size < 4) return data

        val b0 = data[0].toUByte().toInt()
        val b1 = data[1].toUByte().toInt()

        // Check for GZIP header (0x1F 0x8B)
        if (b0 == 0x1F && b1 == 0x8B) {
            return try {
                GZIPInputStream(data.inputStream()).use { it.readBytes() }
            } catch (e: Exception) {
                println("GZIP decompression failed: ${e.message}")
                data
            }
        }

        // Check for ZIP header (PK\u0003\u0004 -> 0x50 0x4B 0x03 0x04)
        if (b0 == 0x50 && b1 == 0x4B && data[2].toInt() == 0x03 && data[3].toInt() == 0x04) {
            return try {
                val decodedEntryData = readZipFile(data) { zipFile ->
                    zipFile.entries().asSequence().firstNotNullOfOrNull { entry ->
                        if (!entry.isDirectory && entry.name.endsWith(".als")) {
                            zipFile.getInputStream(entry).use { it.readBytes() }
                        } else {
                            null
                        }
                    }
                }

                decodedEntryData?.let(::decode) ?: data
            } catch (e: Exception) {
                println("ZIP extraction failed: ${e.message}")
                data
            }
        }

        return data
    }

    actual fun encode(data: ByteArray): ByteArray {
        ByteArrayOutputStream(data.size).use { out ->
            GZIPOutputStream(out).use { gzip ->
                gzip.write(data)
            }

            return out.toByteArray()
        }
    }

    private fun <T> readZipFile(data: ByteArray, block: (ZipFile) -> T): T {
        val tempFile = File.createTempFile(
            "amethyst-zip-",
            ".zip",
            File(FileKit.cacheDir.path)
        )

        return try {
            tempFile.outputStream().use { it.write(data) }

            ZipFile(tempFile).use(block)
        } finally {
            tempFile.delete()
        }
    }
}
