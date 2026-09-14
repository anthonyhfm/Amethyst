package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension

expect object Rar {
    fun getEntries(file: PlatformFile): List<ZipEntry>
    fun getPaths(file: PlatformFile): List<String>
}

fun Rar.determineFormat(file: PlatformFile): ZippedProjectFormat =
    determineProjectArchiveFormat(getPaths(file))

fun determineProjectArchiveFormat(file: PlatformFile): ZippedProjectFormat =
    when (file.extension.lowercase()) {
        "rar" -> Rar.determineFormat(file)
        else -> Zip.determineFormat(file)
    }

internal fun determineProjectArchiveFormat(paths: List<String>): ZippedProjectFormat = when {
    paths.any { it.endsWith(".als", ignoreCase = true) } &&
        paths.any { it.endsWith(".approj", ignoreCase = true) } ->
        ZippedProjectFormat.ABLETON_APOLLO

    paths.any { it.endsWith(".als", ignoreCase = true) } ->
        ZippedProjectFormat.ABLETON

    else -> ZippedProjectFormat.UNIPAD
}

fun getProjectArchiveEntries(file: PlatformFile): List<ZipEntry> =
    when (file.extension.lowercase()) {
        "rar" -> Rar.getEntries(file)
        else -> Zip.getEntries(file)
    }

/**
 * Opens project archives through a random-access reader. ZIP files stay compressed on disk;
 * RAR keeps the legacy eager behavior until the RAR backends support random access everywhere.
 */
fun openProjectArchive(file: PlatformFile): ProjectArchiveReader? =
    when (file.extension.lowercase()) {
        "rar" -> InMemoryProjectArchiveReader(Rar.getEntries(file))
        else -> Zip.open(file)
    }

private class InMemoryProjectArchiveReader(entries: List<ZipEntry>) : ProjectArchiveReader {
    private val dataByPath = entries.associateBy(ZipEntry::path)

    override val entries: List<ProjectArchiveEntry> = entries.map { entry ->
        ProjectArchiveEntry(
            path = entry.path,
            isDirectory = entry.isDirectory,
            compressedSize = entry.data.size.toLong(),
            uncompressedSize = entry.data.size.toLong(),
        )
    }

    override fun readEntry(path: String): ByteArray? = dataByPath[path]?.data

    override fun close() = Unit
}
