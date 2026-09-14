package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.context
import io.github.vinceglb.filekit.path
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

actual object Zip {
    actual fun open(file: PlatformFile): ProjectArchiveReader? {
        var ownedTempFile: File? = null
        return try {
            val archiveFile = when (val androidFile = file.androidFile) {
                is AndroidFile.FileWrapper -> androidFile.file
                is AndroidFile.UriWrapper -> {
                    File.createTempFile("amethyst-zip-stream-", ".zip", File(FileKit.cacheDir.path)).also { temp ->
                        ownedTempFile = temp
                        FileKit.context.contentResolver.openInputStream(androidFile.uri).use { input ->
                            requireNotNull(input) { "Could not open ZIP content URI" }
                            temp.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
                        }
                    }
                }
            }
            AndroidProjectArchiveReader(ZipFile(archiveFile), ownedTempFile)
        } catch (exception: Exception) {
            ownedTempFile?.delete()
            println("Error opening ZIP file: ${exception.message}")
            null
        }
    }

    actual fun getEntries(
        file: PlatformFile,
    ): List<ZipEntry> {
        val reader = open(file) ?: return emptyList()
        return try {
            reader.entries.map { entry ->
                ZipEntry(
                    path = entry.path,
                    data = if (entry.isDirectory) ByteArray(0) else reader.readEntry(entry.path) ?: ByteArray(0),
                    isDirectory = entry.isDirectory,
                )
            }
        } finally {
            reader.close()
        }
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        val reader = open(file) ?: return emptyList()
        return try {
            reader.entries.map(ProjectArchiveEntry::path)
        } finally {
            reader.close()
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

private class AndroidProjectArchiveReader(
    private val zipFile: ZipFile,
    private val ownedTempFile: File?,
) : ProjectArchiveReader {
    private var closed = false
    private val entriesByPath = zipFile.entries().asSequence().associateBy { it.name }

    override val entries: List<ProjectArchiveEntry> = entriesByPath.values.map { entry ->
        ProjectArchiveEntry(
            path = entry.name,
            isDirectory = entry.isDirectory,
            compressedSize = entry.compressedSize,
            uncompressedSize = entry.size,
        )
    }

    override fun readEntry(path: String): ByteArray? {
        if (closed) return null
        val entry = entriesByPath[path] ?: return null
        if (entry.isDirectory) return ByteArray(0)
        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            zipFile.close()
        } finally {
            ownedTempFile?.delete()
        }
    }
}
