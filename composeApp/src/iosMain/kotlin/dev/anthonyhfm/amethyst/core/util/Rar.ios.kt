package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

/** iOS RAR support is intentionally deferred. */
actual object Rar {
    actual fun getEntries(file: PlatformFile): List<ZipEntry> = emptyList()

    actual fun getPaths(file: PlatformFile): List<String> = emptyList()
}
