package dev.anthonyhfm.amethyst.core.engine.audio.source

import dev.anthonyhfm.amethyst.nativeengine.audio.releaseMappedPcmPages
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal actual fun mapPcmFile(path: String, expectedBytes: Int): PcmByteStorage? =
    RandomAccessFile(path, "r").use { file ->
        val byteCount = file.length()
        if (byteCount <= 0 || byteCount > Int.MAX_VALUE || (expectedBytes >= 0 && byteCount != expectedBytes.toLong())) {
            return@use null
        }
        MappedPcmBytes(filePath = path, buffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0L, byteCount))
    }

private class MappedPcmBytes(override val filePath: String, buffer: ByteBuffer) : PcmByteStorage {
    @Volatile
    private var buffer = buffer
    override val size: Int get() = buffer.capacity()
    override fun get(index: Int): Byte = buffer.get(index)
    override fun releaseCachedPages() {
        runCatching {
            if (System.getProperty("os.name").startsWith("Mac") && ProjectPcmFiles.isTemporary(path = filePath)) {
                RandomAccessFile(filePath, "r").use { file ->
                    buffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0L, size.toLong())
                }
            } else {
                releaseMappedPcmPages(buffer = buffer)
            }
        }
    }
    override fun readAll(): ByteArray = ByteArray(size = size).also { buffer.duplicate().get(it) }
}

internal actual fun openPcmWriter(path: String): PcmFileWriter {
    val output = FileOutputStream(path)
    return object : PcmFileWriter {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            output.write(bytes, offset, length)
        }
        override fun close() {
            output.close()
        }
    }
}

internal actual fun copyPcmFile(sourcePath: String, destinationPath: String) {
    java.io.File(sourcePath).copyTo(target = java.io.File(destinationPath), overwrite = true)
}
