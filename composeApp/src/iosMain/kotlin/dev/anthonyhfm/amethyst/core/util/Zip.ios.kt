package dev.anthonyhfm.amethyst.core.util

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy
import platform.zlib.*

@OptIn(ExperimentalForeignApi::class)
actual object Zip {
    private const val LOCAL_FILE_HEADER_SIG = 0x04034b50
    private const val GZIP_WINDOW_BITS = 16 + 15

    actual fun getEntries(file: String): List<ZipEntry> {
        val data = readFileBytes(file) ?: return emptyList()
        val internal = parseLocalFileHeaders(data, listContentOnly = true)
        return internal.map { ZipEntry(it.path, it.isDirectory) }
    }

    actual fun getInputStream(zipPath: String, file: String): ByteArray {
        val data = readFileBytes(zipPath) ?: return ByteArray(0)
        val entries = parseLocalFileHeaders(data, listContentOnly = false)
        val target = entries.firstOrNull { it.path == file } ?: return ByteArray(0)
        return extractEntryData(data, target) ?: ByteArray(0)
    }

    actual fun decode(file: String): ByteArray {
        val compressed = readFileBytes(file) ?: return ByteArray(0)
        return gunzip(compressed) ?: ByteArray(0)
    }

    private data class InternalEntry(
        val path: String,
        val isDirectory: Boolean,
        val compressionMethod: Int,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val dataOffset: Int
    )

    private fun readFileBytes(path: String): ByteArray? {
        val nsData = NSData.dataWithContentsOfFile(path) ?: return null
        val length = nsData.length.toInt()
        if (length <= 0) return ByteArray(0)
        return ByteArray(length).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), nsData.bytes, length.convert())
            }
        }
    }

    private fun parseLocalFileHeaders(zip: ByteArray, listContentOnly: Boolean): List<InternalEntry> {
        val result = mutableListOf<InternalEntry>()
        var offset = 0
        val limit = zip.size
        while (offset + 30 <= limit) {
            val sig = readUInt32(zip, offset)
            if (sig != LOCAL_FILE_HEADER_SIG) break
            val compressionMethod = readUInt16(zip, offset + 8)
            val compressedSize = readUInt32(zip, offset + 18)
            val uncompressedSize = readUInt32(zip, offset + 22)
            val fileNameLength = readUInt16(zip, offset + 26)
            val extraLength = readUInt16(zip, offset + 28)
            val nameStart = offset + 30
            if (nameStart + fileNameLength > limit) break
            val name = zip.decodeToString(nameStart, nameStart + fileNameLength)
            val dataStart = nameStart + fileNameLength + extraLength
            if (dataStart > limit) break
            val isDir = name.endsWith("/")
            val dataOffset = dataStart

            result += InternalEntry(
                path = name,
                isDirectory = isDir,
                compressionMethod = compressionMethod,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                dataOffset = dataOffset
            )

            val advance = 30 + fileNameLength + extraLength + compressedSize
            if (advance <= 0) break
            offset += advance
        }
        return if (listContentOnly) result.map { InternalEntry(it.path, it.isDirectory, 0, 0, 0, 0) } else result
    }

    private fun extractEntryData(zip: ByteArray, entry: InternalEntry): ByteArray? {
        if (entry.isDirectory) return ByteArray(0)
        val method = entry.compressionMethod
        val compSize = entry.compressedSize
        val uncompSize = entry.uncompressedSize
        val start = entry.dataOffset
        if (start + compSize > zip.size) return null
        val slice = zip.copyOfRange(start, start + compSize)
        return when (method) {
            0 -> slice
            8 -> inflateDeflate(slice, uncompSize)
            else -> null
        }
    }

    private fun inflateDeflate(data: ByteArray, expectedSize: Int): ByteArray? = memScoped {
        val stream = alloc<z_stream>()
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null

        val retInit = inflateInit2_(stream.ptr, 15, ZLIB_VERSION, sizeOf<z_stream>().convert()) // raw/normal deflate
        if (retInit != Z_OK) return null
        try {
            val output = ByteArray(if (expectedSize > 0) expectedSize else data.size * 4)
            var outOffset = 0
            data.usePinned { pinIn ->
                output.usePinned { pinOut ->
                    stream.next_in = pinIn.addressOf(0).reinterpret()
                    stream.avail_in = data.size.convert()
                    stream.next_out = pinOut.addressOf(0).reinterpret()
                    stream.avail_out = output.size.convert()

                    while (true) {
                        val ret = inflate(stream.ptr, Z_NO_FLUSH)
                        if (ret == Z_STREAM_END) break
                        if (ret != Z_OK) return null
                        if (stream.avail_out.toInt() == 0) {

                            val grown = output.copyOf(output.size * 2)
                            val used = stream.total_out.toInt()

                            grown.usePinned { pinGrown ->
                                stream.next_out = pinGrown.addressOf(0).reinterpret<UByteVarOf<UByte>>() + used
                                stream.avail_out = (grown.size - used).convert()
                            }

                            return null
                        }
                    }
                    outOffset = stream.total_out.toInt()
                }
            }
            return output.copyOf(outOffset)
        } finally {
            inflateEnd(stream.ptr)
        }
    }

    private fun gunzip(data: ByteArray): ByteArray? = memScoped {
        val stream = alloc<z_stream>()
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null

        val retInit = inflateInit2_(stream.ptr, GZIP_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().convert())
        if (retInit != Z_OK) return null
        try {
            val output = ArrayList<ByteArray>()
            var totalSize = 0
            data.usePinned { pinIn ->
                stream.next_in = pinIn.addressOf(0).reinterpret()
                stream.avail_in = data.size.convert()
                while (true) {
                    val chunk = ByteArray(64 * 1024)
                    var produced = 0
                    chunk.usePinned { pinOut ->
                        stream.next_out = pinOut.addressOf(0).reinterpret()
                        stream.avail_out = chunk.size.convert()
                        val ret = inflate(stream.ptr, Z_NO_FLUSH)
                        produced = (chunk.size - stream.avail_out.toInt())
                        output.add(if (produced == chunk.size) chunk else chunk.copyOf(produced))
                        totalSize += produced
                        if (ret == Z_STREAM_END) return@usePinned
                        if (ret != Z_OK) return null
                    }
                }
            }
            val merged = ByteArray(totalSize)
            var pos = 0
            for (part in output) {
                part.copyInto(merged, pos)
                pos += part.size
            }
            merged
        } finally {
            inflateEnd(stream.ptr)
        }
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}