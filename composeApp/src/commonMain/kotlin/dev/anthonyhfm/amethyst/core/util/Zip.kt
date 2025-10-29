package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

expect object Zip {
    fun getEntries(file: PlatformFile): List<ZipEntry>
    fun getPaths(file: PlatformFile): List<String>

    fun decode(data: ByteArray): ByteArray
}

fun Zip.determineFormat(file: PlatformFile): ZippedProjectFormat {
    val paths = Zip.getPaths(file)

    return when {
        paths.any { it.endsWith(".als") } -> ZippedProjectFormat.ABLETON

        paths.any {
            it.contains(".als")
        } && paths.any {
            it.contains(".approj")
        } -> ZippedProjectFormat.ABLETON_APOLLO

        else -> ZippedProjectFormat.UNIPAD
    }
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