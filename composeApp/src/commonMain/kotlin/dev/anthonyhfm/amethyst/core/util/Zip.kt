package dev.anthonyhfm.amethyst.core.util

expect object Zip {
    fun getEntries(file: String): List<ZipEntry>
    fun getInputStream(zipPath: String, file: String): ByteArray
}

data class ZipEntry(
    val path: String,
    val isDirectory: Boolean = false
)