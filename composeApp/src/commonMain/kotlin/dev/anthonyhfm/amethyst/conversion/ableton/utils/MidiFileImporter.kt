package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
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
        val data = try {
            runBlocking { file.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        var offset = 0

        fun bytesRemaining(): Int = data.size - offset
        fun requireBytes(n: Int): Boolean = bytesRemaining() >= n

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
            if (!requireBytes(len)) return ""
            val s = data.copyOfRange(offset, offset + len)
            offset += len
            return s.decodeToString()
        }

        fun readVarLen(): Long {
            var value = 0L
            repeat(4) {
                if (!requireBytes(1)) return value
                val c = readByte()
                value = (value shl 7) or (c and 0x7F).toLong()
                if ((c and 0x80) == 0) return value
            }
            return value
        }

        // ---- Header ----
        if (!requireBytes(14)) return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        val headerId = readString(4)
        val headerSize = readInt32()
        if (headerId != "MThd" || headerSize != 6 || !requireBytes(6)) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }
        val formatType = readInt16()
        val trackCount = readInt16()
        val divisionRaw = readInt16()

        // Strict like the original: only Format 0 + exactly one track
        if (formatType != 0 || trackCount != 1) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }

        // Timing mode
        val isSmpte = (divisionRaw and 0x8000) != 0
        val ppq = if (!isSmpte) divisionRaw else 0
        val smpteFps = if (isSmpte) (256 - ((divisionRaw ushr 8) and 0xFF)) else 0 // two's complement
        val smpteTicksPerFrame = if (isSmpte) (divisionRaw and 0xFF) else 0

        var bpm = 120.0 // PPQ mode default; ignored in SMPTE mode

        fun ticksToMs(deltaTicks: Long): Int {
            return if (!isSmpte) {
                if (ppq <= 0) 0 else {
                    val msPerQuarter = 60000.0 / bpm
                    val msPerTick = msPerQuarter / ppq
                    round(deltaTicks * msPerTick).toInt()
                }
            } else {
                val tps = smpteFps * smpteTicksPerFrame // ticks per second
                if (tps <= 0) 0 else round(deltaTicks * 1000.0 / tps).toInt()
            }
        }

        // ---- Prepare frames ----
        val frames = mutableListOf<KeyframesChainDeviceContract.Frame>()
        var currentFrame = KeyframesChainDeviceContract.Frame(
            timing = Timing.Duration(100.milliseconds)
        )
        frames.add(currentFrame)

        var runningStatus: Int? = null

        // ---- Read single track ----
        if (!requireBytes(8)) return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        val trackId = readString(4)
        val trackLength = readInt32()
        if (trackId != "MTrk" || trackLength < 0 || !requireBytes(trackLength)) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }
        val trackEnd = offset + trackLength

        var currentTick = 0L

        fun cloneLastFrameWithDuration(elapsedMs: Int) {
            if (frames.isEmpty()) return
            val lastIndex = frames.lastIndex
            val last = frames[lastIndex]
            // finalize previous frame
            frames[lastIndex] = last.copy(timing = Timing.Duration(elapsedMs.milliseconds))
            // clone for next edits
            val newEntries = last.entries.map { it.copy() }
            currentFrame = last.copy(timing = Timing.Duration(100.milliseconds), entries = newEntries)
            frames.add(currentFrame)
        }

        while (offset < trackEnd) {
            // Delta time
            val delta = readVarLen().toLong()
            if (delta > 0) {
                currentTick += delta
                cloneLastFrameWithDuration(ticksToMs(delta))
            }

            if (!requireBytes(1)) break
            var statusByte = readByte()
            var eventType: Int
            var handledRunning = false

            if (statusByte < 0x80) {
                // Running status: first data byte already consumed into statusByte
                eventType = runningStatus ?: continue
                handledRunning = true
            } else {
                eventType = statusByte
                runningStatus = eventType
            }

            when (eventType and 0xF0) {
                0x80, 0x90 -> { // Note Off / Note On
                    val isNoteOn = (eventType and 0xF0) == 0x90
                    val pitch: Int
                    val velocity: Int
                    if (handledRunning) {
                        pitch = statusByte
                        velocity = if (requireBytes(1)) readByte() else 0
                    } else {
                        pitch = if (requireBytes(1)) readByte() else 0
                        velocity = if (requireBytes(1)) readByte() else 0
                    }
                    val noteOn = isNoteOn && velocity != 0

                    if (pitch in 0 until DRUM_RACK_TO_XY.size) {
                        val xy = DRUM_RACK_TO_XY[pitch]
                        val x = xy % 10
                        val y = 9 - (xy / 10) // UI expects flipped Y

                        val filtered = currentFrame.entries.filterNot { it.x == x && it.y == y }
                        val updatedEntries =
                            if (noteOn) {
                                val idx = velocity.coerceIn(0, Palettes.novation.size - 1)
                                val triple = Palettes.novation[idx]
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
                                    r = 0f, g = 0f, b = 0f
                                )
                            }

                        val lastIndex = frames.lastIndex
                        val frame = frames[lastIndex]
                        frames[lastIndex] = frame.copy(entries = updatedEntries)
                        currentFrame = frames[lastIndex]
                    }
                }

                0xA0, 0xB0, 0xE0 -> { // two data bytes
                    if (handledRunning) {
                        if (requireBytes(1)) readByte()
                    } else {
                        if (requireBytes(2)) { readByte(); readByte() } else while (offset < trackEnd && requireBytes(1)) readByte()
                    }
                }

                0xC0, 0xD0 -> { // one data byte
                    if (!handledRunning && requireBytes(1)) readByte()
                }

                else -> {
                    when (eventType and 0xFF) {
                        0xFF -> { // Meta
                            if (!requireBytes(1)) break
                            val metaType = readByte()
                            val length = readVarLen().toInt()
                            when (metaType) {
                                0x2F -> { // End of track
                                    // consume remaining declared length, if any
                                    if (length > 0 && requireBytes(length)) offset += length
                                    offset = trackEnd // stop
                                }
                                0x51 -> { // Set Tempo (microseconds per quarter note; length MUST be 3)
                                    if (length >= 3 && requireBytes(3)) {
                                        val b1 = readByte()
                                        val b2 = readByte()
                                        val b3 = readByte()
                                        if (!isSmpte) {
                                            val tempo = (b1 shl 16) or (b2 shl 8) or b3
                                            if (tempo != 0) bpm = 60000000.0 / tempo.toDouble()
                                        }
                                        // skip any over-declared extra bytes, if present
                                        val skip = length - 3
                                        if (skip > 0 && requireBytes(skip)) offset += skip
                                    } else {
                                        if (length > 0 && requireBytes(length)) offset += length
                                    }
                                }
                                else -> {
                                    if (length > 0 && requireBytes(length)) offset += length
                                }
                            }
                        }
                        0xF0, 0xF7 -> { // SysEx
                            val length = readVarLen().toInt()
                            if (length > 0 && requireBytes(length)) offset += length
                        }
                        0xF2 -> { // Song Position Pointer: two data bytes
                            if (!handledRunning && requireBytes(2)) { readByte(); readByte() }
                        }
                        0xF1, 0xF3 -> { // one data byte
                            if (!handledRunning && requireBytes(1)) readByte()
                        }
                        else -> {
                            // Unknown system message — best-effort skip one byte if present
                            if (!handledRunning && requireBytes(1)) readByte()
                        }
                    }
                }
            }
        }

        var renderedAnimation: List<Pair<Int, List<Signal>>> = emptyList()
        KeyframesChainDevice().apply {
            state.update { it.copy(frames = frames) }

            renderAnimation()

            renderedAnimation = state.value.renderedAnimation
        }

        return KeyframesChainDeviceContract.KeyframesChainDeviceState(
            frames = frames,
            renderedAnimation = renderedAnimation
        )
    }
}