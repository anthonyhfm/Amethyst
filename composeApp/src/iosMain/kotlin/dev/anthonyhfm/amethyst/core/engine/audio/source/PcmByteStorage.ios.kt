package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.fopen
import platform.posix.fclose
import platform.posix.fwrite
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedAlways
import platform.Foundation.dataWithContentsOfFile
import kotlinx.atomicfu.atomic
import platform.posix.madvise
import platform.posix.MADV_DONTNEED

@OptIn(ExperimentalForeignApi::class)
internal actual fun mapPcmFile(path: String, expectedBytes: Int): PcmByteStorage? {
    val data = NSData.dataWithContentsOfFile(path = path, options = NSDataReadingMappedAlways, error = null)
        ?: return null
    if (data.length == 0uL || data.length > Int.MAX_VALUE.toULong() || (expectedBytes >= 0 && data.length != expectedBytes.toULong())) {
        return null
    }
    return MappedPcmBytes(filePath = path, data = data)
}

@OptIn(ExperimentalForeignApi::class)
private class MappedPcmBytes(override val filePath: String, data: NSData) : PcmByteStorage {
    private class Pages(val data: NSData) {
        private val bytes = requireNotNull(data.bytes).reinterpret<ByteVar>()
        fun byteAt(index: Int): Byte = bytes[index]
        fun releaseCachedPages(size: Int) {
            madvise(bytes, size.toULong(), MADV_DONTNEED)
        }
    }

    private val pages = atomic(initial = Pages(data = data))
    override val size: Int = data.length.toInt()
    override fun get(index: Int): Byte = pages.value.byteAt(index = index)
    override fun releaseCachedPages() {
        if (!ProjectPcmFiles.isTemporary(path = filePath)) {
            pages.value.releaseCachedPages(size = size)
            return
        }
        val data = NSData.dataWithContentsOfFile(path = filePath, options = NSDataReadingMappedAlways, error = null)
            ?: return
        if (data.length == size.toULong()) {
            pages.value = Pages(data = data)
        }
    }
    override fun readAll(): ByteArray {
        val current = pages.value
        return ByteArray(size = size) { current.byteAt(index = it) }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun openPcmWriter(path: String): PcmFileWriter {
    val output = fopen(path, "wb") ?: error("Could not create PCM file")
    return object : PcmFileWriter {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > 0) {
                bytes.usePinned { pinned ->
                    check(fwrite(pinned.addressOf(offset), 1uL, length.toULong(), output).toInt() == length)
                }
            }
        }
        override fun close() {
            check(fclose(output) == 0)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun copyPcmFile(sourcePath: String, destinationPath: String) {
    check(platform.Foundation.NSFileManager.defaultManager.copyItemAtPath(sourcePath, destinationPath, null))
}
