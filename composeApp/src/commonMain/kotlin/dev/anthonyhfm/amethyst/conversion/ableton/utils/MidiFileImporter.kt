package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

object MidiFileImporter {
    fun loadData(
        data: ByteArray,
        bpm: Double = 120.0,
        palette: Array<Triple<Int, Int, Int>> = Palettes.novation,
        xyOffset: IntOffset = IntOffset.Zero
    ): KeyframesChainDeviceContract.KeyframesChainDeviceState {
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

        var usPerQuarter = (60000000.0 / bpm).toLong()

        val frames = mutableListOf<KeyframesChainDeviceContract.Frame>()
        var currentFrame = KeyframesChainDeviceContract.Frame(
            timing = Timing.Duration(100.milliseconds),
            _internalUuid = UUID.randomUUID()
        )
        frames.add(currentFrame)

        val frameTicks = mutableListOf<Long>(0L)

        val tempoChanges = mutableListOf(0L to usPerQuarter)

        var runningStatus: Int? = null

        if (!requireBytes(8)) return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        val trackId = readString(4)
        val trackLength = readInt32()
        if (trackId != "MTrk" || trackLength < 0 || !requireBytes(trackLength)) {
            return KeyframesChainDeviceContract.KeyframesChainDeviceState()
        }
        val trackEnd = offset + trackLength

        var currentTick = 0L

        fun cloneFrameAfterTickAdvance() {
            val last = frames.last()
            val newEntries = last.entries.map { it.copy() }
            currentFrame = last.copy(
                timing = Timing.Duration(100.milliseconds),
                entries = newEntries,
                _internalUuid = UUID.randomUUID()
            )
            frames.add(currentFrame)
        }

        while (offset < trackEnd) {
            val delta = readVarLen()
            if (delta > 0) {
                currentTick += delta

                frameTicks.add(currentTick)
                cloneFrameAfterTickAdvance()
            }

            if (!requireBytes(1)) break
            var statusByte = readByte()
            var eventType: Int
            var handledRunning = false

            if (statusByte < 0x80) {
                eventType = runningStatus ?: continue
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
                        velocity = if (requireBytes(1)) readByte() else 0
                    } else {
                        pitch = if (requireBytes(1)) readByte() else 0
                        velocity = if (requireBytes(1)) readByte() else 0
                    }
                    val noteOn = isNoteOn && velocity != 0

                    if (pitch in 0 until DRUM_RACK_TO_XY.size) {
                        val xy = DRUM_RACK_TO_XY[pitch]
                        val x = (xy % 10) + xyOffset.x
                        val y = (9 - (xy / 10)) + xyOffset.y

                        val filtered = currentFrame.entries.filterNot { it.x == x && it.y == y }
                        val updatedEntries =
                            if (noteOn) {
                                val idx = velocity.coerceIn(0, palette.size - 1)
                                val triple = palette[idx]
                                filtered + KeyframesChainDeviceContract.KeyframesEntry(
                                    x = x,
                                    y = y,
                                    r = triple.first / 63f,
                                    g = triple.second / 63f,
                                    b = triple.third / 63f
                                )
                            } else {
                                filtered
                            }

                        frames[frames.lastIndex] = frames.last().copy(entries = updatedEntries)
                        currentFrame = frames.last()
                    }
                }

                0xA0, 0xB0, 0xE0 -> {
                    if (handledRunning) {
                        if (requireBytes(1)) readByte()
                    } else {
                        if (requireBytes(2)) { readByte(); readByte() } else while (offset < trackEnd && requireBytes(1)) readByte()
                    }
                }

                0xC0, 0xD0 -> {
                    if (!handledRunning && requireBytes(1)) readByte()
                }

                else -> {
                    when (eventType and 0xFF) {
                        0xFF -> {
                            if (!requireBytes(1)) break
                            val metaType = readByte()
                            val length = readVarLen().toInt()
                            when (metaType) {
                                0x2F -> {
                                    if (length > 0 && requireBytes(length)) offset += length
                                    offset = trackEnd
                                }
                                0x51 -> {
                                    if (length >= 3 && requireBytes(3)) {
                                        val b1 = readByte()
                                        val b2 = readByte()
                                        val b3 = readByte()
                                        if (!isSmpte) {
                                            val tempo = (b1 shl 16) or (b2 shl 8) or b3
                                            if (tempo > 0) {
                                                usPerQuarter = tempo.toLong()
                                                tempoChanges.add(currentTick to usPerQuarter)
                                            }
                                        }
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
                        0xF0, 0xF7 -> {
                            val length = readVarLen().toInt()
                            if (length > 0 && requireBytes(length)) offset += length
                        }
                        0xF2 -> {
                            if (!handledRunning && requireBytes(2)) { readByte(); readByte() }
                        }
                        0xF1, 0xF3 -> {
                            if (!handledRunning && requireBytes(1)) readByte()
                        }
                        else -> {
                            if (!handledRunning && requireBytes(1)) readByte()
                        }
                    }
                }
            }
        }

        if (frames.size > 1) {
            tempoChanges.sortBy { it.first }

            fun intervalMicros(startTick: Long, endTick: Long): Long {
                if (endTick <= startTick) return 0L
                var acc = 0L
                var pos = startTick
                var tempoIndex = tempoChanges.indexOfLast { it.first <= startTick }
                if (tempoIndex < 0) tempoIndex = 0
                while (pos < endTick) {
                    val (_, tempoUSPQ) = tempoChanges[tempoIndex]
                    val nextTempoTick = if (tempoIndex + 1 < tempoChanges.size) tempoChanges[tempoIndex + 1].first else Long.MAX_VALUE
                    val segmentEnd = minOf(endTick, nextTempoTick)
                    val ticks = segmentEnd - pos
                    acc += (ticks * tempoUSPQ) / ppq
                    pos = segmentEnd
                    if (segmentEnd == nextTempoTick && tempoIndex + 1 < tempoChanges.size) tempoIndex++
                }
                return acc
            }

            var cumulativeUs = 0L
            var prevRoundedMs = 0L
            for (i in 0 until frames.size - 1) {
                val startTick = frameTicks[i]
                val endTick = frameTicks[i + 1]
                val intervalUs = intervalMicros(startTick, endTick)
                cumulativeUs += intervalUs
                val roundedTotalMs = (cumulativeUs + 500) / 1000
                val frameMs = (roundedTotalMs - prevRoundedMs).toInt()
                prevRoundedMs = roundedTotalMs
                if (frameMs > 0) {
                    frames[i] = frames[i].copy(
                        timing = Timing.Duration(frameMs.milliseconds),
                        _internalUuid = UUID.randomUUID()
                    )
                } else {
                    frames[i] = frames[i].copy(
                        timing = Timing.Duration(1.milliseconds),
                        _internalUuid = UUID.randomUUID()
                    )
                }
            }

            if (frames.isNotEmpty()) {
                val last = frames.last()
                val penultimate = if (frames.size >= 2) frames[frames.size - 2] else null
                if (penultimate != null && last.entries == penultimate.entries) {
                    frames.removeLast()
                } else {
                    // Falls behalten: Dauer minimal setzen
                    frames[frames.lastIndex] = last.copy(
                        timing = Timing.Duration(50.milliseconds),
                        _internalUuid = UUID.randomUUID()
                    )
                }
            }

            val speedFactor = if (platform is Platform.iOS) 0.98 else 0.96
            if (speedFactor != 1.0 && frames.isNotEmpty()) {
                val durMs = frames.map { f ->
                    when (val t = f.timing) {
                        is Timing.Duration -> t.duration.inWholeMilliseconds
                        is Timing.Rythm -> {
                            val fraction = t.timing.factor * 4.0
                            (fraction * (60000.0 / bpm)).roundToLong()
                        }
                    }
                }
                val cum = LongArray(durMs.size + 1)
                for (i in durMs.indices) cum[i + 1] = cum[i] + durMs[i]

                val scaledCum = LongArray(cum.size) { i ->
                    (cum[i] * speedFactor).roundToLong()
                }

                for (i in durMs.indices) {
                    val newMs = (scaledCum[i + 1] - scaledCum[i]).coerceAtLeast(0L)
                    frames[i] = frames[i].copy(
                        timing = Timing.Duration(newMs.toInt().milliseconds),
                        _internalUuid = UUID.randomUUID()
                    )
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
