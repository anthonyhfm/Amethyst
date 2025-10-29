package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual object Zip {
    actual fun getEntries(file: PlatformFile): List<ZipEntry> {
        TODO("Cannot get entries on iOS yet")
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        TODO("Cannot get paths on iOS yet")
    }

    actual fun decode(data: ByteArray): ByteArray {
        TODO("Cannot decode on iOS yet")
    }
}