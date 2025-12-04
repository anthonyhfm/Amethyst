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
    actual fun getEntries(
        file: PlatformFile,
    ): List<ZipEntry> {
        val javaFile = file.file
        if (!javaFile.exists() || !javaFile.isFile) {
            return emptyList()
        }

        return try {
            // Try using ZipFile first (more robust)
            ZipFile(javaFile).use { zipFile ->
                val entries = mutableListOf<ZipEntry>()

                zipFile.entries().asSequence().forEach { entry ->
                    val data = if (!entry.isDirectory) {
                        zipFile.getInputStream(entry).use { it.readBytes() }
                    } else {
                        ByteArray(0)
                    }

                    entries.add(
                        ZipEntry(
                            path = entry.name,
                            data = data,
                            isDirectory = entry.isDirectory,
                        )
                    )
                }

                entries
            }
        } catch (_: Exception) {
            // Fallback to ZipInputStream if ZipFile fails
            try {
                FileInputStream(javaFile).use { fis ->
                    ZipInputStream(fis).use { zipStream ->
                        val entries = mutableListOf<ZipEntry>()

                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            entries.add(
                                ZipEntry(
                                    path = entry.name,
                                    data = zipStream.readBytes(),
                                    isDirectory = entry.isDirectory,
                                )
                            )
                            entry = zipStream.nextEntry
                        }

                        entries
                    }
                }
            } catch (e2: Exception) {
                println("Error reading ZIP file: ${e2.message}")
                emptyList()
            }
        }
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        val javaFile = file.file
        if (!javaFile.exists() || !javaFile.isFile) {
            return emptyList()
        }

        return try {
            // Try using ZipFile first (more robust)
            ZipFile(javaFile).use { zipFile ->
                zipFile.entries().asSequence()
                    .map { it.name }
                    .toList()
            }
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