package dev.anthonyhfm.amethyst.core.util

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate

/**
 * FastRGB encoder and decompressor for Midi Fighter 64 custom firmware.
 *
 * Button indexing in Drum Rack layout (0..63):
 * - Bit 5 (0x20): Half-select (0 = Left columns 0..3, 1 = Right columns 4..7)
 * - Bits 4..2 (0x1C): Row index 0..7 (row shl 2)
 * - Bits 1..0 (0x03): Column offset within half (0..3)
 *
 * Target Command Mapping (0..127):
 * - 0..63: Single pad in Drum Rack layout
 * - 64..95: 2-way point symmetry (pad and its 180° point-inverted opposite)
 * - 96..103: Full row macro (sets all 8 pads in row 0..7 across both halves)
 * - 104..111: Full column macro (sets all 8 pads in col 0..7 across all rows)
 * - 112..127: 4-way quadrant symmetry (mirrors a pad in the 4x4 quadrant to all 4 quadrants)
 */
object FastRgbMidiFighter {
    data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    const val BUTTON_COUNT = 64
    const val DEFAULT_MAX_PAYLOAD_SIZE = 317

    private const val RGB_SIZE = 3
    private const val MIN_BLOCK_SIZE = RGB_SIZE + 1
    private const val COUNT_BIT = 0x40
    private const val MAX_COMPACT_COUNT = 7
    private const val MAX_EXPLICIT_COUNT = 127

    /**
     * Converts a 10x10 grid index (y * 10 + x with x in 1..8, y in 1..8)
     * to a Midi Fighter 64 Drum Rack pad index (0..63).
     */
    fun xyToDrumRack(xy: Int): Int? {
        val x = xy % 10
        val y = xy / 10
        if (x !in 1..8 || y !in 1..8) return null
        val col = x - 1
        val row = y - 1
        return (row shl 2) or (if (col >= 4) 0x20 else 0) or (col % 4)
    }

    /**
     * Converts a Midi Fighter 64 Drum Rack pad index (0..63)
     * to a 10x10 grid index (y * 10 + x with x in 1..8, y in 1..8).
     */
    fun drumRackToXy(drumRack: Int): Int? {
        if (drumRack !in 0 until BUTTON_COUNT) return null
        val row = (drumRack and 0x1C) shr 2
        val col = (drumRack and 0x03) + if (drumRack and 0x20 != 0) 4 else 0
        return (row + 1) * 10 + (col + 1)
    }

    /**
     * Converts a list of 10x10 XY updates to Drum Rack index (0..63) updates.
     */
    fun prepareUpdates(updates: List<RawLEDUpdate>): List<RawLEDUpdate> {
        return updates.mapNotNull { update ->
            val rawIndex = update.index.toInt() and 0xFF
            val drumRackIndex = xyToDrumRack(rawIndex) ?: return@mapNotNull null
            update.copy(index = drumRackIndex.toByte())
        }
    }

    /**
     * Compresses a list of Drum Rack RawLEDUpdates (indices 0..63) into a single compressed payload byte array.
     */
    fun compress(updates: List<RawLEDUpdate>, factor: Int = 63): ByteArray {
        return encodeBlocks(updates, factor, MAX_EXPLICIT_COUNT)
            .fold(ByteArray(0), ByteArray::plus)
    }

    /**
     * Compresses a list of Drum Rack RawLEDUpdates (indices 0..63) into chunks that fit within maxChunkSize.
     */
    fun compressToChunks(
        updates: List<RawLEDUpdate>,
        maxChunkSize: Int = DEFAULT_MAX_PAYLOAD_SIZE,
        factor: Int = 63,
    ): List<ByteArray> {
        require(maxChunkSize >= MIN_BLOCK_SIZE) {
            "FastRGB chunks must have room for RGB and at least one pitch"
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
            "FastRGB colors must remain in the 6-bit RGB range"
        }
        require(maxPitchCount in 1..MAX_EXPLICIT_COUNT)

        val padToColor = linkedMapOf<Int, Rgb>()
        for (update in updates) {
            val drumRackIndex = update.index.toInt() and 0xFF
            if (drumRackIndex !in 0 until BUTTON_COUNT) continue

            val rgb = Rgb(
                red = (update.color.red.coerceIn(0f, 1f) * factor).toInt(),
                green = (update.color.green.coerceIn(0f, 1f) * factor).toInt(),
                blue = (update.color.blue.coerceIn(0f, 1f) * factor).toInt(),
            )
            padToColor[drumRackIndex] = rgb
        }

        val padsByColor = linkedMapOf<Rgb, MutableSet<Int>>()
        for ((pad, rgb) in padToColor) {
            padsByColor.getOrPut(rgb, ::mutableSetOf) += pad
        }

        return buildList {
            for ((rgb, pads) in padsByColor) {
                optimizeTargets(pads).chunked(maxPitchCount).forEach { chunk ->
                    add(encodeBlock(rgb, chunk))
                }
            }
        }
    }

    /**
     * Optimizes a set of Drum Rack pad indices (0..63) for a single color into
     * compressed target command bytes using rows, columns, quadrant mirrors,
     * point symmetry, and single pads.
     */
    fun optimizeTargets(pads: Set<Int>): List<Int> {
        if (pads.isEmpty()) return emptyList()

        val remaining = pads.toMutableSet()
        val targets = mutableListOf<Int>()

        fun getRowPads(row: Int): Set<Int> {
            val base = row shl 2
            return (0..3).map { base or it }.toSet() + (0..3).map { base or 0x20 or it }.toSet()
        }

        fun getColPads(col: Int): Set<Int> {
            val colBase = (if (col >= 4) 0x20 else 0) or (col % 4)
            return (0..7).map { colBase or (it shl 2) }.toSet()
        }

        // 1. Full Row / Column lines
        val completeRows = (0..7).filter { row -> remaining.containsAll(getRowPads(row)) }
        val completeCols = (0..7).filter { col -> remaining.containsAll(getColPads(col)) }

        if (completeRows.size >= completeCols.size) {
            for (row in completeRows) {
                remaining.removeAll(getRowPads(row))
                targets += (96 + row)
            }
            for (col in completeCols) {
                val colPads = getColPads(col)
                if (remaining.containsAll(colPads)) {
                    remaining.removeAll(colPads)
                    targets += (104 + col)
                }
            }
        } else {
            for (col in completeCols) {
                remaining.removeAll(getColPads(col))
                targets += (104 + col)
            }
            for (row in completeRows) {
                val rowPads = getRowPads(row)
                if (remaining.containsAll(rowPads)) {
                    remaining.removeAll(rowPads)
                    targets += (96 + row)
                }
            }
        }

        // 2. 4-way Quadrant Mirroring (112..127)
        for (rSub in 0..3) {
            for (cSub in 0..3) {
                val pBl = (rSub shl 2) or cSub
                val pBr = (rSub shl 2) or 0x20 or (3 - cSub)
                val pTl = ((7 - rSub) shl 2) or cSub
                val pTr = ((7 - rSub) shl 2) or 0x20 or (3 - cSub)
                val quadPads = setOf(pBl, pBr, pTl, pTr)

                if (remaining.containsAll(quadPads)) {
                    remaining.removeAll(quadPads)
                    val q = ((3 - rSub) shl 2) or (3 - cSub)
                    targets += (112 + q)
                }
            }
        }

        // 3. 2-way Point Symmetry (64..95)
        for (p in 0..31) {
            val pOpposite = 63 - p
            if (remaining.contains(p) && remaining.contains(pOpposite)) {
                remaining.remove(p)
                remaining.remove(pOpposite)
                targets += (64 + p)
            }
        }

        // 4. Remaining single pads (0..63)
        for (p in remaining.sorted()) {
            targets += p
        }

        return targets
    }

    private fun encodeBlock(rgb: Rgb, targets: List<Int>): ByteArray {
        require(targets.isNotEmpty())
        require(targets.size <= MAX_EXPLICIT_COUNT)

        if (targets.size <= MAX_COMPACT_COUNT) {
            val count = targets.size
            val red = rgb.red or if (count and 0b100 != 0) COUNT_BIT else 0
            val green = rgb.green or if (count and 0b010 != 0) COUNT_BIT else 0
            val blue = rgb.blue or if (count and 0b001 != 0) COUNT_BIT else 0
            val result = ByteArray(3 + targets.size)
            result[0] = red.toByte()
            result[1] = green.toByte()
            result[2] = blue.toByte()
            for (i in targets.indices) {
                result[3 + i] = targets[i].toByte()
            }
            return result
        }

        val result = ByteArray(4 + targets.size)
        result[0] = rgb.red.toByte()
        result[1] = rgb.green.toByte()
        result[2] = rgb.blue.toByte()
        result[3] = targets.size.toByte()
        for (i in targets.indices) {
            result[4 + i] = targets[i].toByte()
        }
        return result
    }

    /**
     * Recreates the firmware decompressor state (64 pads x 3 RGB channels)
     * exactly according to the firmware's fastrgb_decompress logic.
     */
    fun decompress(bytes: ByteArray): Array<IntArray> {
        val state = Array(BUTTON_COUNT) { IntArray(3) }
        var i = 0
        while (i < bytes.size) {
            val rByte = bytes[i++].toInt() and 0xFF
            if (i >= bytes.size) break
            val gByte = bytes[i++].toInt() and 0xFF
            if (i >= bytes.size) break
            val bByte = bytes[i++].toInt() and 0xFF

            var n = ((rByte and 0x40) shr 4) or ((gByte and 0x40) shr 5) or ((bByte and 0x40) shr 6)
            if (n == 0) {
                if (i >= bytes.size) break
                n = bytes[i++].toInt() and 0xFF
            }

            val r = rByte and 0x3F
            val g = gByte and 0x3F
            val b = bByte and 0x3F

            fun setUnsafe(p: Int) {
                val pad = p and 0x3F
                state[pad][0] = if (r == 0) 0 else r + 2
                state[pad][1] = if (g == 0) 0 else g + 2
                state[pad][2] = if (b == 0) 0 else b + 2
            }

            for (j in 0 until n) {
                if (i >= bytes.size) break
                val x = bytes[i++].toInt() and 0xFF

                if ((x and 0b01110000) != 0b01100000) {
                    setUnsafe(x and 0x3F)

                    if ((x and 0b01000000) != 0) {
                        val xInv = (x.inv()) and 0x3F
                        setUnsafe(xInv)

                        if ((x and 0b00100000) != 0) {
                            setUnsafe((x and 0b00011100) or (xInv and 0b00000011))
                            setUnsafe((x and 0b00100011) or (xInv and 0b00011100))
                        }
                    }
                } else if ((x and 0b00001000) != 0) {
                    val col = x and (if ((x and 0b00000100) != 0) 0b00100011 else 0b00000011)
                    for (k in 0 until 8) {
                        setUnsafe(col or (k shl 2))
                    }
                } else {
                    val row = (x and 0b00000111) shl 2
                    for (k in 0 until 4) {
                        setUnsafe(row or k)
                        setUnsafe(row or 0b00100000 or k)
                    }
                }
            }
        }
        return state
    }
}
