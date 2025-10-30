package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.utils.toByteArray
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData

interface ZipAPI {
    fun getEntries(data: NSData): List<IOSZipEntry>
    fun getPaths(data: NSData): List<String>
    fun decode(data: NSData): NSData?
}

data class IOSZipEntry(
    val path: String,
    val data: NSData?,
    val isDirectory: Boolean = false
)

/**
 * # iOS Zip
 *
 * Zip-Files are a little funky with iOS. I dont really have a system API at hand, so
 * I am using a ZipAPI interface that I can implement in Swift and then use as a backend
 *
 * This is then going to utilize the Swift ZipFoundation Library
 */
actual object Zip {
    var zipAPI: ZipAPI? = null

    @OptIn(ExperimentalForeignApi::class)
    actual fun getEntries(file: PlatformFile): List<ZipEntry> {
        return zipAPI?.let {
            val data = runBlocking { file.readBytes() }

            it.getEntries(data.toNSData()).map {
                ZipEntry(
                    path = it.path,
                    data = it.data?.bytes?.readBytes(it.data.length.toInt()) ?: ByteArray(0),
                    isDirectory = it.isDirectory
                )
            }
        } ?: emptyList()
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        return zipAPI?.let {
            val data = runBlocking { file.readBytes() }

            it.getPaths(data.toNSData())
        } ?: emptyList()
    }

    actual fun decode(data: ByteArray): ByteArray {
        return zipAPI?.let {
            it.decode(data.toNSData())?.toByteArray()
        } ?: ByteArray(0)
    }
}