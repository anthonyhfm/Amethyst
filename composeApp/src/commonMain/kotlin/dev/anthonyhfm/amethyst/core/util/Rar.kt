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
