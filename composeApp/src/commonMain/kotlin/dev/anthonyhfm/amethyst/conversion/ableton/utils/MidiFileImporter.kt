package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Palettes.novation
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds

object MidiFileImporter {
    fun loadFile(file: PlatformFile): KeyframesChainDeviceContract.KeyframesChainDeviceState {
        println("Loading MIDI file: ${'$'}{file.path}")

        val data: ByteArray = try {
            runBlocking {
                file.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        var offset = 0

        fun bytesRemaining(): Int = data.size - offset

        fun readByte(): Int {
            if (offset >= data.size) return 0
            return data[offset++].toInt() and 0xFF
        }

        fun readInt16(): Int {
            val b1 = readByte()
            val b2 = readByte()
            return (b1 shl 8) or b2
        }

        fun readInt32(): Int {
            val b1 = readByte()
            val b2 = readByte()
            val b3 = readByte()
            val b4 = readByte()
            return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }

        fun readString(len: Int): String {
            if (offset + len > data.size) {
                val remaining = data.size - offset
                val arr = data.copyOfRange(offset, data.size)
                offset = data.size
                return arr.decodeToString()
            }
            val arr = data.copyOfRange(offset, offset + len)
            offset += len
            return arr.decodeToString()
        }

        fun readVarLen(): Int {
            var result = 0
            var i = 0
            while (i < 4 && bytesRemaining() > 0) {
                val b = readByte()
                result = (result shl 7) + (b and 0x7F)
                if ((b and 0x80) == 0) break
                i++
            }
            return result
        }

        if (bytesRemaining() < 14) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        val headerId = readString(4)
        val headerSize = readInt32()
        val formatType = readInt16()
        val trackCount = readInt16()
        val divisionRaw = readInt16()

        if (headerId != "MThd" || headerSize != 6 || formatType != 0 || trackCount < 1) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        val ticksPerQuarter = if ((divisionRaw and 0x8000) == 0) {
            divisionRaw
        } else {
            val fps = ((divisionRaw shr 8) and 0xFF)
            val tpf = (divisionRaw and 0xFF)
            if (fps != 0 && tpf != 0) fps * tpf else 96
        }

        var bpm: Double = 120.0

        val frames = mutableListOf<KeyframesChainDeviceContract.Frame>()
        var currentFrame = KeyframesChainDeviceContract.Frame(
            timing = Timing.Duration(100.milliseconds)
        )
        frames.add(currentFrame)

        var runningStatus: Int? = null
        var currentTick = 0L

        repeat(trackCount) { _ ->
            if (bytesRemaining() < 8) return@repeat
            val trackId = readString(4)
            val trackLength = readInt32()
            if (trackId != "MTrk" || trackLength < 0 || bytesRemaining() < trackLength) {
                offset += trackLength.coerceAtLeast(0)
                return@repeat
            }
            val trackEnd = offset + trackLength
            while (offset < trackEnd) {
                val deltaTicks = readVarLen().toLong()
                if (deltaTicks > 0) {
                    val prevTick = currentTick
                    currentTick += deltaTicks
                    val msPerQuarter = 60000.0 / bpm
                    val msPerTick = msPerQuarter / ticksPerQuarter
                    val elapsedMs = (round(currentTick * msPerTick) - round(prevTick * msPerTick)).toInt()
                    if (frames.isNotEmpty()) {
                        val lastIndex = frames.lastIndex
                        val last = frames[lastIndex]
                        frames[lastIndex] = last.copy(
                            timing = Timing.Duration(elapsedMs.milliseconds)
                        )
                        val newEntries = last.entries.map { it.copy() }
                        currentFrame = last.copy(
                            timing = Timing.Duration(100.milliseconds),
                            entries = newEntries
                        )
                        frames.add(currentFrame)
                    }
                }

                if (bytesRemaining() <= 0) break
                var statusByte = readByte()
                var eventType: Int
                var handledRunning = false
                if (statusByte < 0x80) {
                    eventType = runningStatus ?: return@repeat
                    handledRunning = true
                } else {
                    eventType = statusByte
                    runningStatus = eventType
                }

                when (eventType and 0xF0) {
                    0x80, 0x90 -> {
                        val isNoteOn = (eventType and 0xF0) == 0x90
                        val pitch: Int
                        val velocity: Int
                        if (handledRunning) {
                            pitch = statusByte
                            velocity = readByte()
                        } else {
                            pitch = readByte()
                            velocity = readByte()
                        }
                        val noteOn = isNoteOn && velocity != 0
                        val xyValue = if (pitch < DRUM_RACK_TO_XY.size) DRUM_RACK_TO_XY[pitch] else 0
                        val x = xyValue % 10
                        val y = 9 - (xyValue / 10)
                        if (xyValue != 0 || pitch == 0) {
                            val lastIndex = frames.lastIndex
                            val frame = frames[lastIndex]
                            val filtered = frame.entries.filter { it.x != x || it.y != y }
                            val updatedEntries = if (noteOn) {
                                val colourIndex = velocity.coerceIn(0, Palettes.novation.size - 1)
                                val triple = Palettes.novation[colourIndex]
                                filtered + KeyframesChainDeviceContract.KeyframesEntry(
                                    x = x,
                                    y = y,
                                    r = triple.first / 63f,
                                    g = triple.second / 63f,
                                    b = triple.third / 63f
                                )
                            } else {
                                filtered + KeyframesChainDeviceContract.KeyframesEntry(
                                    x = x,
                                    y = y,
                                    r = 0f,
                                    g = 0f,
                                    b = 0f
                                )
                            }
                            frames[lastIndex] = frame.copy(entries = updatedEntries)
                            currentFrame = frames[lastIndex]
                        }
                    }
                    0xA0, 0xB0, 0xE0 -> {
                        if (handledRunning) {
                            if (bytesRemaining() > 0) readByte()
                        } else {
                            if (bytesRemaining() >= 2) {
                                readByte(); readByte()
                            } else {
                                while (bytesRemaining() > 0) readByte()
                            }
                        }
                    }
                    0xC0, 0xD0 -> {
                        if (!handledRunning && bytesRemaining() > 0) readByte()
                    }
                    else -> {
                        when (eventType) {
                            0xFF -> {
                                if (!handledRunning && bytesRemaining() > 0) {
                                    val metaType = readByte()
                                    val length = readVarLen()
                                    if (metaType == 0x51 && length >= 3) {
                                        if (bytesRemaining() >= 3) {
                                            val b1 = readByte()
                                            val b2 = readByte()
                                            val b3 = readByte()
                                            val tempo = (b1 shl 16) or (b2 shl 8) or b3
                                            if (tempo != 0) {
                                                bpm = 60000000.0 / tempo
                                            }
                                            val skip = length - 3
                                            if (skip > 0 && bytesRemaining() >= skip) offset += skip
                                        } else {
                                            val skip = length
                                            if (skip > 0 && bytesRemaining() >= skip) offset += skip
                                        }
                                    } else {
                                        if (length > 0 && bytesRemaining() >= length) offset += length else {
                                            while (bytesRemaining() > 0) readByte()
                                        }
                                    }
                                }
                            }
                            0xF0, 0xF7 -> {
                                val length = readVarLen()
                                if (length > 0 && bytesRemaining() >= length) offset += length else {
                                    while (bytesRemaining() > 0) readByte()
                                }
                            }
                            0xF2 -> {
                                if (!handledRunning && bytesRemaining() >= 2) {
                                    readByte(); readByte()
                                } else {
                                    while (bytesRemaining() > 0) readByte()
                                }
                            }
                            0xF3 -> {
                                if (!handledRunning && bytesRemaining() > 0) readByte()
                            }
                            else -> {
                            }
                        }
                    }
                }
            }
        }

        if (frames.size > 1) frames.removeAt(frames.size - 1)

        val renderedAnimation: List<Pair<Int, List<dev.anthonyhfm.amethyst.core.heaven.elements.Signal>>>
        try {
            KeyframesChainDevice().apply {
                state.update { it.copy(frames = frames) }
                renderAnimation()
                renderedAnimation = state.value.renderedAnimation
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        return KeyframesChainDeviceContract.KeyframesChainDeviceState(
            frames = frames,
            renderedAnimation = renderedAnimation
        )
    }
}