package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.zlib.*

private class ByteArrayBuilder(initialCapacity: Int = 1024) {
    private var buffer = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(size + length)
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buffer.size) {
            var newCap = buffer.size * 2
            if (newCap < minCapacity) newCap = minCapacity
            buffer = buffer.copyOf(newCap)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun inflateData(data: ByteArray, windowBits: Int): ByteArray? {
    if (data.isEmpty()) return ByteArray(0)

    return memScoped {
        val strm = alloc<z_stream>()
        val initStatus = inflateInit2_(
            strm.ptr,
            windowBits,
            ZLIB_VERSION,
            sizeOf<z_stream>().toInt()
        )
        if (initStatus != Z_OK) {
            return@memScoped null
        }

        try {
            data.usePinned { pinnedIn ->
                strm.next_in = pinnedIn.addressOf(0).reinterpret()
                strm.avail_in = data.size.toUInt()

                val builder = ByteArrayBuilder(data.size * 2)
                val chunkSize = 32768
                val buffer = ByteArray(chunkSize)

                buffer.usePinned { pinnedOut ->
                    var ret: Int
                    do {
                        strm.next_out = pinnedOut.addressOf(0).reinterpret()
                        strm.avail_out = chunkSize.toUInt()

                        ret = inflate(strm.ptr, Z_NO_FLUSH)
                        if (ret != Z_OK && ret != Z_STREAM_END && ret != Z_BUF_ERROR) {
                            return@usePinned null
                        }

                        val have = chunkSize - strm.avail_out.toInt()
                        if (have > 0) {
                            builder.write(buffer, 0, have)
                        }
                    } while (ret != Z_STREAM_END && (strm.avail_in > 0u || have > 0))
                    builder.toByteArray()
                }
            }
        } finally {
            inflateEnd(strm.ptr)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun gzipCompress(data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)

    return memScoped {
        val strm = alloc<z_stream>()
        val initStatus = deflateInit2_(
            strm.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            16 + 15,
            8,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            sizeOf<z_stream>().toInt()
        )
        if (initStatus != Z_OK) {
            return@memScoped data
        }

        try {
            data.usePinned { pinnedIn ->
                strm.next_in = pinnedIn.addressOf(0).reinterpret()
                strm.avail_in = data.size.toUInt()

                val builder = ByteArrayBuilder(data.size / 2 + 64)
                val chunkSize = 32768
                val buffer = ByteArray(chunkSize)

                buffer.usePinned { pinnedOut ->
                    var ret: Int
                    do {
                        strm.next_out = pinnedOut.addressOf(0).reinterpret()
                        strm.avail_out = chunkSize.toUInt()

                        ret = deflate(strm.ptr, Z_FINISH)
                        if (ret != Z_OK && ret != Z_STREAM_END && ret != Z_BUF_ERROR) {
                            return@usePinned data
                        }

                        val have = chunkSize - strm.avail_out.toInt()
                        if (have > 0) {
                            builder.write(buffer, 0, have)
                        }
                    } while (ret != Z_STREAM_END)
                    builder.toByteArray()
                }
            }
        } finally {
            deflateEnd(strm.ptr)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun gzipDecompress(data: ByteArray): ByteArray? {
    return inflateData(data, 32 + 15)
}

@OptIn(ExperimentalForeignApi::class)
private fun rawDeflateDecompress(data: ByteArray): ByteArray? {
    return inflateData(data, -15)
}

private fun ByteArray.readUInt16LE(offset: Int): Int {
    if (offset + 1 >= size) return 0
    val b0 = this[offset].toInt() and 0xFF
    val b1 = this[offset + 1].toInt() and 0xFF
    return b0 or (b1 shl 8)
}

private fun ByteArray.readUInt32LE(offset: Int): Long {
    if (offset + 3 >= size) return 0L
    val b0 = this[offset].toLong() and 0xFFL
    val b1 = this[offset + 1].toLong() and 0xFFL
    val b2 = this[offset + 2].toLong() and 0xFFL
    val b3 = this[offset + 3].toLong() and 0xFFL
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun findEOCD(data: ByteArray): Int {
    val maxCommentLength = 65535
    val minOffset = maxOf(0, data.size - 22 - maxCommentLength)
    for (i in (data.size - 22) downTo minOffset) {
        if (data[i] == 0x50.toByte() &&
            data[i + 1] == 0x4B.toByte() &&
            data[i + 2] == 0x05.toByte() &&
            data[i + 3] == 0x06.toByte()
        ) {
            return i
        }
    }
    return -1
}

private data class CentralDirectoryEntry(
    val path: String,
    val compressionMethod: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val localHeaderOffset: Int,
    val isDirectory: Boolean
)

private fun parseCentralDirectory(data: ByteArray): List<CentralDirectoryEntry> {
    val eocdOffset = findEOCD(data)
    if (eocdOffset == -1) return emptyList()

    val totalEntries = data.readUInt16LE(eocdOffset + 10)
    val cdOffset = data.readUInt32LE(eocdOffset + 16).toInt()

    val entries = mutableListOf<CentralDirectoryEntry>()
    var offset = cdOffset

    for (i in 0 until totalEntries) {
        if (offset + 46 > data.size) break
        if (data[offset] != 0x50.toByte() ||
            data[offset + 1] != 0x4B.toByte() ||
            data[offset + 2] != 0x01.toByte() ||
            data[offset + 3] != 0x02.toByte()
        ) break

        val method = data.readUInt16LE(offset + 10)
        val compSize = data.readUInt32LE(offset + 20).toInt()
        val uncompSize = data.readUInt32LE(offset + 24).toInt()
        val fileNameLength = data.readUInt16LE(offset + 28)
        val extraFieldLength = data.readUInt16LE(offset + 30)
        val commentLength = data.readUInt16LE(offset + 32)
        val localOffset = data.readUInt32LE(offset + 42).toInt()

        val nameOffset = offset + 46
        if (nameOffset + fileNameLength > data.size) break
        val path = data.decodeToString(nameOffset, nameOffset + fileNameLength)
        val isDirectory = path.endsWith("/")

        entries.add(
            CentralDirectoryEntry(
                path = path,
                compressionMethod = method,
                compressedSize = compSize,
                uncompressedSize = uncompSize,
                localHeaderOffset = localOffset,
                isDirectory = isDirectory
            )
        )

        offset += 46 + fileNameLength + extraFieldLength + commentLength
    }

    return entries
}

private fun extractEntryData(data: ByteArray, entry: CentralDirectoryEntry): ByteArray {
    if (entry.isDirectory) return ByteArray(0)

    val localOffset = entry.localHeaderOffset
    if (localOffset + 30 > data.size) return ByteArray(0)

    if (data[localOffset] != 0x50.toByte() ||
        data[localOffset + 1] != 0x4B.toByte() ||
        data[localOffset + 2] != 0x03.toByte() ||
        data[localOffset + 3] != 0x04.toByte()
    ) return ByteArray(0)

    val fileNameLength = data.readUInt16LE(localOffset + 26)
    val extraFieldLength = data.readUInt16LE(localOffset + 28)

    val dataStart = localOffset + 30 + fileNameLength + extraFieldLength
    val dataEnd = dataStart + entry.compressedSize
    if (dataStart > data.size || dataEnd > data.size) return ByteArray(0)

    val compressedBytes = data.copyOfRange(dataStart, dataEnd)

    return when (entry.compressionMethod) {
        0 -> compressedBytes
        8 -> rawDeflateDecompress(compressedBytes) ?: ByteArray(0)
        else -> ByteArray(0)
    }
}

private fun parseLocalHeaders(data: ByteArray): List<ZipEntry> {
    val entries = mutableListOf<ZipEntry>()
    var offset = 0

    while (offset + 30 <= data.size) {
        if (data[offset] != 0x50.toByte() ||
            data[offset + 1] != 0x4B.toByte() ||
            data[offset + 2] != 0x03.toByte() ||
            data[offset + 3] != 0x04.toByte()
        ) {
            break
        }

        val method = data.readUInt16LE(offset + 8)
        val compSize = data.readUInt32LE(offset + 18).toInt()
        val fileNameLength = data.readUInt16LE(offset + 26)
        val extraFieldLength = data.readUInt16LE(offset + 28)

        val nameOffset = offset + 30
        if (nameOffset + fileNameLength > data.size) break
        val path = data.decodeToString(nameOffset, nameOffset + fileNameLength)
        val isDirectory = path.endsWith("/")

        val dataStart = nameOffset + fileNameLength + extraFieldLength
        val dataEnd = dataStart + compSize

        val entryData = if (isDirectory || compSize == 0 || dataEnd > data.size) {
            ByteArray(0)
        } else {
            val compressedBytes = data.copyOfRange(dataStart, dataEnd)
            when (method) {
                0 -> compressedBytes
                8 -> rawDeflateDecompress(compressedBytes) ?: ByteArray(0)
                else -> ByteArray(0)
            }
        }

        entries.add(
            ZipEntry(
                path = path,
                data = entryData,
                isDirectory = isDirectory
            )
        )

        offset = dataEnd
    }

    return entries
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Zip {
    actual fun getEntries(file: PlatformFile): List<ZipEntry> {
        val data = runBlocking(Dispatchers.Default) { file.readBytes() }
        if (data.isEmpty()) return emptyList()

        val cdEntries = parseCentralDirectory(data)
        if (cdEntries.isNotEmpty()) {
            return cdEntries.map { entry ->
                ZipEntry(
                    path = entry.path,
                    data = extractEntryData(data, entry),
                    isDirectory = entry.isDirectory
                )
            }
        }

        return parseLocalHeaders(data)
    }

    actual fun getPaths(file: PlatformFile): List<String> {
        val data = runBlocking(Dispatchers.Default) { file.readBytes() }
        if (data.isEmpty()) return emptyList()

        val cdEntries = parseCentralDirectory(data)
        if (cdEntries.isNotEmpty()) {
            return cdEntries.map { it.path }
        }

        return parseLocalHeaders(data).map { it.path }
    }

    actual fun decode(data: ByteArray): ByteArray {
        if (data.size < 4) return data

        val b0 = data[0].toUByte().toInt()
        val b1 = data[1].toUByte().toInt()

        // Check for GZIP header (0x1F 0x8B)
        if (b0 == 0x1F && b1 == 0x8B) {
            return gzipDecompress(data) ?: data
        }

        // Check for ZIP header (PK\u0003\u0004 -> 0x50 0x4B 0x03 0x04)
        if (b0 == 0x50 && b1 == 0x4B && data[2].toInt() == 0x03 && data[3].toInt() == 0x04) {
            val cdEntries = parseCentralDirectory(data)
            val alsEntry = cdEntries.firstOrNull { !it.isDirectory && it.path.endsWith(".als") }
            if (alsEntry != null) {
                val entryData = extractEntryData(data, alsEntry)
                return decode(entryData)
            }

            val localEntries = parseLocalHeaders(data)
            val localAls = localEntries.firstOrNull { !it.isDirectory && it.path.endsWith(".als") }
            if (localAls != null) {
                return decode(localAls.data)
            }
        }

        return data
    }

    actual fun encode(data: ByteArray): ByteArray {
        return gzipCompress(data)
    }
}