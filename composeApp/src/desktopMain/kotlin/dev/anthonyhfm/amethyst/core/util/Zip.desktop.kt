package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Zip {
    actual fun open(file: PlatformFile): ProjectArchiveReader? {
        val javaFile = file.file
        if (!javaFile.exists() || !javaFile.isFile) return null

        return try {
            DesktopProjectArchiveReader(ZipFile(javaFile))
        } catch (exception: Exception) {
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
        val javaFile = file.file
        if (!javaFile.exists() || !javaFile.isFile) {
            return emptyList()
        }

        return try {
            ZipFile(javaFile).use { zipFile -> zipFile.entries().asSequence().map { it.name }.toList() }
        } catch (_: Exception) {
            // Fallback to ZipInputStream if ZipFile fails
            try {
                FileInputStream(javaFile).use { fis ->
                    ZipInputStream(fis).use { zipStream ->
                        val entries = mutableListOf<String>()

                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            entries.add(entry.name)
                            entry = zipStream.nextEntry
                        }

                        entries
                    }
                }
            } catch (e2: Exception) {
                println("Error reading ZIP paths: ${e2.message}")
                emptyList()
            }
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
                ZipInputStream(data.inputStream()).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".als")) {
                            val entryData = zipStream.readBytes()
                            // Recursively decode to handle GZIP inside ZIP
                            return decode(entryData)
                        }
                        entry = zipStream.nextEntry
                    }
                }
                data
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
}

private class DesktopProjectArchiveReader(
    private val zipFile: ZipFile,
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
        if (!closed) {
            closed = true
            zipFile.close()
        }
    }
}
