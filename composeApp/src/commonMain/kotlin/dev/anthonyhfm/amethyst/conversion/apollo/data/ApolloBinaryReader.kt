package dev.anthonyhfm.amethyst.conversion.apollo.data

import kotlin.math.pow

class ApolloBinaryReader(
    private val data: ByteArray
) {
    var position: Int = 0
        private set

    val remaining: Int
        get() = data.size - position

    private fun requireAvailable(count: Int) {
        require(position + count <= data.size) { "Requested $count bytes, only $remaining left" }
    }

    fun skip(count: Int) {
        requireAvailable(count)
        position += count
    }

    fun readByte(): Byte {
        requireAvailable(1)
        return data[position++]
    }

    fun readBoolean(): Boolean = readByte().toInt() != 0

    fun readBytes(count: Int): ByteArray {
        requireAvailable(count)
        val end = position + count
        val result = data.copyOfRange(position, end)
        position = end
        return result
    }

    fun readInt32(): Int {
        requireAvailable(4)
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        val b2 = data[position++].toInt() and 0xFF
        val b3 = data[position++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readInt64(): Long {
        requireAvailable(8)
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((data[position++].toLong() and 0xFF) shl (8 * i))
        }
        return result
    }

    fun readDouble(): Double = Double.fromBits(readInt64())

    // .NET System.Decimal: 4 Int32 LE = [lo, mid, hi, flags]
    // flags: bit31=sign, bits16-23=scale (0-28)
    fun readDecimalAsDouble(): Double {
        val lo = readInt32().toLong() and 0xFFFFFFFFL
        val mid = readInt32().toLong() and 0xFFFFFFFFL
        val hi = readInt32().toLong() and 0xFFFFFFFFL
        val flags = readInt32()
        val scale = (flags shr 16) and 0x7F
        val negative = (flags ushr 31) != 0
        val raw = hi * 18446744073709551616.0 + mid * 4294967296.0 + lo
        val divisor = 10.0.pow(scale.toDouble())
        return if (negative) -(raw / divisor) else raw / divisor
    }

    fun read7BitEncodedInt(): Int {
        var count = 0
        var shift = 0
        while (shift < 35) {
            val b = readByte().toInt() and 0xFF
            count = count or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return count
            shift += 7
        }
        error("7-bit encoded int is too large")
    }

    fun readString(): String {
        val length = read7BitEncodedInt()
        if (length == 0) return ""
        return readBytes(length).decodeToString()
    }

    fun expectMagic(expected: String = "APOL") {
        val magic = readBytes(expected.length).decodeToString()
        require(magic == expected) { "Magic mismatch: expected $expected, got $magic" }
    }
}

fun ByteArray.asApolloBinaryReader(): ApolloBinaryReader = ApolloBinaryReader(this)
