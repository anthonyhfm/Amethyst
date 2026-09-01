package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

expect object Zip {
    fun getEntries(file: PlatformFile): List<ZipEntry>
    fun getPaths(file: PlatformFile): List<String>

    fun decode(data: ByteArray): ByteArray

    fun encode(data: ByteArray): ByteArray
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
