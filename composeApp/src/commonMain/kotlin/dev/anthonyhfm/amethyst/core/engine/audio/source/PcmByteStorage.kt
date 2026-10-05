package dev.anthonyhfm.amethyst.core.engine.audio.source

import dev.anthonyhfm.amethyst.core.util.ConversionTempFiles
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal interface PcmByteStorage {
    val size: Int
    val filePath: String? get() = null
    operator fun get(index: Int): Byte
    fun readAll(): ByteArray
    fun readRange(fromIndex: Int, toIndex: Int): ByteArray {
        require(fromIndex in 0..toIndex && toIndex <= size)
        return ByteArray(size = toIndex - fromIndex) { get(index = fromIndex + it) }
    }

    fun writeTo(path: String) {
        val sourcePath = filePath
        if (sourcePath != null && ProjectPcmFiles.isTemporary(path = sourcePath)) {
            try {
                copyPcmFile(sourcePath = sourcePath, destinationPath = path)
                return
            } catch (_: Exception) {
            }
        }
        val writer = openPcmWriter(path = path)
        try {
            if (this is InMemoryPcmBytes) {
                writer.write(bytes = bytes, offset = 0, length = bytes.size)
            } else {
                val block = ByteArray(size = 64 * 1024)
                var offset = 0
                while (offset < size) {
                    val count = minOf(block.size, size - offset)
                    repeat(times = count) { index -> block[index] = get(index = offset + index) }
                    writer.write(bytes = block, offset = 0, length = count)
                    offset += count
                }
            }
        } finally {
            writer.close()
            releaseCachedPages()
        }
    }
    fun releaseCachedPages() = Unit
    fun contentEquals(other: PcmByteStorage): Boolean {
        if (this === other) {
            return true
        }
        if (size != other.size) {
            return false
        }
        var index = 0
        while (index < size) {
            if (get(index) != other[index]) {
                return false
            }
            index++
        }
        return true
    }

    fun contentHash(): Int {
        var hash = 1
        var index = 0
        while (index < size) {
            hash = 31 * hash + get(index)
            index++
        }
        return hash
    }
}

internal class InMemoryPcmBytes(val bytes: ByteArray) : PcmByteStorage {
    override val size: Int get() = bytes.size
    override fun get(index: Int): Byte = bytes[index]
    override fun readAll(): ByteArray = bytes
    override fun readRange(fromIndex: Int, toIndex: Int): ByteArray = bytes.copyOfRange(fromIndex = fromIndex, toIndex = toIndex)
    override fun contentHash(): Int = bytes.contentHashCode()
}

internal interface PcmFileWriter {
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun close()
}

internal expect fun openPcmWriter(path: String): PcmFileWriter

internal expect fun copyPcmFile(sourcePath: String, destinationPath: String)

internal expect fun mapPcmFile(path: String, expectedBytes: Int): PcmByteStorage?

internal object ProjectPcmFiles {
    private val lock = SynchronizedObject()
    private val temporaryPaths = mutableSetOf<String>()

    fun keep(bytes: ByteArray): PcmByteStorage = if (bytes.size >= MAPPED_PCM_THRESHOLD_BYTES) {
        runCatching { store(bytes = bytes) }.getOrElse { InMemoryPcmBytes(bytes = bytes) }
    } else {
        InMemoryPcmBytes(bytes = bytes)
    }

    fun store(bytes: ByteArray): PcmByteStorage = create(expectedBytes = bytes.size) { writer ->
        writer.write(bytes = bytes, offset = 0, length = bytes.size)
    }

    fun prepare(expectedBytes: Int, producer: (PcmFileWriter) -> Unit): PcmByteStorage {
        if (expectedBytes >= MAPPED_PCM_THRESHOLD_BYTES) {
            try {
                return create(expectedBytes = expectedBytes, producer = producer)
            } catch (_: Exception) {
            }
        }
        val outputBytes = ByteArray(size = expectedBytes)
        var position = 0
        val writer = object : PcmFileWriter {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                check(position + length <= expectedBytes)
                bytes.copyInto(
                    destination = outputBytes,
                    destinationOffset = position,
                    startIndex = offset,
                    endIndex = offset + length,
                )
                position += length
            }
            override fun close() = Unit
        }
        producer(writer)
        check(position == expectedBytes)
        return InMemoryPcmBytes(bytes = outputBytes)
    }

    private fun create(expectedBytes: Int, producer: (PcmFileWriter) -> Unit): PcmByteStorage {
        val path = ConversionTempFiles.newPath(extension = "pcm")
        try {
            val writer = openPcmWriter(path = path)
            try {
                producer(writer)
            } finally {
                writer.close()
            }
            val mapped = requireNotNull(mapPcmFile(path = path, expectedBytes = expectedBytes))
            mapped.releaseCachedPages()
            synchronized(lock = lock) { temporaryPaths.add(path) }
            return mapped
        } catch (exception: Exception) {
            ConversionTempFiles.remove(path = path)
            throw exception
        }
    }

    fun retain(paths: Set<String>) {
        val discarded = synchronized(lock = lock) {
            temporaryPaths.filter { it !in paths }.also { temporaryPaths.removeAll(elements = it.toSet()) }
        }
        discarded.forEach { ConversionTempFiles.remove(path = it) }
    }

    fun clear() = retain(paths = emptySet())

    internal fun isTemporary(path: String): Boolean = synchronized(lock = lock) { path in temporaryPaths }

    internal fun temporaryFileCount(): Int = synchronized(lock = lock) { temporaryPaths.size }
}

internal const val MAPPED_PCM_THRESHOLD_BYTES = 4 * 1024 * 1024
