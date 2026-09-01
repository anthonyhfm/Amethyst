package dev.anthonyhfm.amethyst.core.util

import com.github.junrar.Archive
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Rar {
    actual fun getEntries(file: PlatformFile): List<ZipEntry> {
        val rarFile = file.file
        if (!rarFile.exists() || !rarFile.isFile) return emptyList()

        return try {
            Archive(rarFile).use { archive ->
                archive.fileHeaders.map { header ->
                    val data = if (header.isDirectory) {
                        ByteArray(0)
                    } else {
                        ByteArrayOutputStream().use { output ->
                            archive.extractFile(header, output)
                            output.toByteArray()
                        }
                    }

                    ZipEntry(
                        path = header.fileName,
                        data = data,
                        isDirectory = header.isDirectory,
                    )
                }
            }
        } catch (exception: Exception) {
            println("Error reading RAR file: ${exception.message}")
            emptyList()
        }
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        val rarFile = file.file
        if (!rarFile.exists() || !rarFile.isFile) return emptyList()

        return try {
            Archive(rarFile).use { archive ->
                archive.fileHeaders.map { it.fileName }
            }
        } catch (exception: Exception) {
            println("Error reading RAR paths: ${exception.message}")
            emptyList()
        }
    }
}
