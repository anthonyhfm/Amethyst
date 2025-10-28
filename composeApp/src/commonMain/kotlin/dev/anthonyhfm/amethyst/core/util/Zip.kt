package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

expect object Zip {
    fun getEntries(file: PlatformFile): List<ZipEntry>

    fun decode(file: String): ByteArray
}

data class ZipEntry(
    val path: String,
    val data: ByteArray,
    val isDirectory: Boolean = false
)