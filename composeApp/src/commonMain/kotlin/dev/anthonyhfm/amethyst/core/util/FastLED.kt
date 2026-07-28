package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate

/**
 * Encoder for the custom FastLED command (0x5F) used by Mat1jaczyyy and CoreFW.
 *
 * Each block is encoded as RR GG BB NN followed by NN pitches. For groups of one
 * to seven pitches, NN is stored in bit 6 of RR, GG and BB and the explicit NN
 * byte is omitted.
 */
object FastLED {
    private data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    fun compress(updates: List<RawLEDUpdate>, factor: Int = 63): ByteArray {
        return encodeBlocks(updates, factor, MAX_EXPLICIT_COUNT)
            .fold(ByteArray(0), ByteArray::plus)
    }

    fun compressToChunks(
        updates: List<RawLEDUpdate>,
        maxChunkSize: Int,
        factor: Int = 63,
    ): List<ByteArray> {
        require(maxChunkSize >= MIN_BLOCK_SIZE) {
            "FastLED chunks must have room for RGB and at least one pitch"
        }

        val maxCompactPitchCount = (maxChunkSize - RGB_SIZE).coerceIn(0, MAX_COMPACT_COUNT)
        val maxExplicitPitchCount = (maxChunkSize - RGB_SIZE - 1).coerceIn(0, MAX_EXPLICIT_COUNT)
        val maxPitchCount = maxOf(maxCompactPitchCount, maxExplicitPitchCount)
        val blocks = encodeBlocks(updates, factor, maxPitchCount)
        if (blocks.isEmpty()) return emptyList()

        val chunks = mutableListOf<ByteArray>()
        var current = ByteArray(0)

        for (block in blocks) {
            if (current.isNotEmpty() && current.size + block.size > maxChunkSize) {
                chunks += current
                current = ByteArray(0)
            }
            current += block
        }

        if (current.isNotEmpty()) {
            chunks += current
        }
        return chunks
    }

    private fun encodeBlocks(
        updates: List<RawLEDUpdate>,
        factor: Int,
        maxPitchCount: Int,
    ): List<ByteArray> {
        require(factor in 0..63) {
            "FastLED colors must remain in the 6-bit RGB range"
        }
        require(maxPitchCount in 1..MAX_EXPLICIT_COUNT)

        val pitchesByColor = linkedMapOf<Rgb, MutableList<Byte>>()
        for (update in updates) {
            val pitch = update.index.toInt() and 0xFF
            require(pitch in 0..127) {
                "FastLED pitches must be valid 7-bit MIDI data"
            }

            val rgb = Rgb(
                red = (update.color.red.coerceIn(0f, 1f) * factor).toInt(),
                green = (update.color.green.coerceIn(0f, 1f) * factor).toInt(),
                blue = (update.color.blue.coerceIn(0f, 1f) * factor).toInt(),
            )
            pitchesByColor.getOrPut(rgb, ::mutableListOf) += update.index
        }

        return buildList {
            for ((rgb, pitches) in pitchesByColor) {
                optimizeGridLines(pitches).chunked(maxPitchCount).forEach { chunk ->
                    add(encodeBlock(rgb, chunk))
                }
            }
        }
    }

    /**
     * A complete central-grid row or column can be represented by one target.
     * Surrounding buttons are deliberately left as individual targets.
     */
    private fun optimizeGridLines(pitches: List<Byte>): List<Byte> {
        val targets = pitches
            .map { it.toInt() and 0xFF }
            .toCollection(linkedSetOf())

        fun compressLines(rows: Boolean): LinkedHashSet<Int> {
            val compressed = LinkedHashSet(targets)

            for (line in 1..8) {
                val pads = if (rows) {
                    (1..8).map { column -> line * 10 + column }
                } else {
                    (1..8).map { row -> row * 10 + line }
                }

                if (pads.all(targets::contains)) {
                    compressed.removeAll(pads.toSet())
                    compressed += if (rows) 100 + line else 110 + line
                }
            }

            return compressed
        }

        val rows = compressLines(rows = true)
        val columns = compressLines(rows = false)
        return (if (rows.size <= columns.size) rows else columns)
            .map(Int::toByte)
    }

    private fun encodeBlock(rgb: Rgb, pitches: List<Byte>): ByteArray {
        require(pitches.isNotEmpty())
        require(pitches.size <= MAX_EXPLICIT_COUNT)

        if (pitches.size <= MAX_COMPACT_COUNT) {
            val count = pitches.size
            val red = rgb.red or if (count and 0b100 != 0) COUNT_BIT else 0
            val green = rgb.green or if (count and 0b010 != 0) COUNT_BIT else 0
            val blue = rgb.blue or if (count and 0b001 != 0) COUNT_BIT else 0
            return byteArrayOf(red.toByte(), green.toByte(), blue.toByte()) +
                pitches.toByteArray()
        }

        return byteArrayOf(
            rgb.red.toByte(),
            rgb.green.toByte(),
            rgb.blue.toByte(),
            pitches.size.toByte(),
        ) + pitches.toByteArray()
    }

    private const val RGB_SIZE = 3
    private const val MIN_BLOCK_SIZE = RGB_SIZE + 1
    private const val COUNT_BIT = 0x40
    private const val MAX_COMPACT_COUNT = 7
    private const val MAX_EXPLICIT_COUNT = 127
}
