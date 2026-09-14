package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

expect object Zip {
    /** Opens a ZIP without inflating its entries. Callers must close the reader. */
    fun open(file: PlatformFile): ProjectArchiveReader?

    fun getEntries(file: PlatformFile): List<ZipEntry>
    fun getPaths(file: PlatformFile): List<String>

    fun decode(data: ByteArray): ByteArray

    fun encode(data: ByteArray): ByteArray
}

data class ProjectArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
)

/** Random-access archive reader. [readEntry] materializes only the requested entry. */
interface ProjectArchiveReader {
    val entries: List<ProjectArchiveEntry>

    fun readEntry(path: String): ByteArray?

    fun close()
}

fun Zip.determineFormat(file: PlatformFile): ZippedProjectFormat {
    return determineProjectArchiveFormat(Zip.getPaths(file))
}

data class ZipEntry(
    val path: String,
    val data: ByteArray,
    val isDirectory: Boolean = false
)

/**
 * Zipped project formats will make it possible to determine which project type is zipped.
 */
enum class ZippedProjectFormat {
    ABLETON,
    ABLETON_APOLLO,
    UNIPAD
}
