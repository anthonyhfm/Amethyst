package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

object TimelineClipUtils {
    fun cutAtSelection(selection: Selectable.TimelineTime): Boolean {
        val trackIndex = selection.trackIndex
        val cutTimeMs = selection.timeMs
        val tracks = TimelineRepository.tracks.value
        if (trackIndex !in tracks.indices) return false
        val track = tracks[trackIndex]
        
        return when (track) {
            is AudioTimelineTrack -> cutAudioAtSelection(trackIndex, track, cutTimeMs)
            is MidiTimelineTrack -> cutMidiAtSelection(trackIndex, track, cutTimeMs)
            else -> false
        }
    }

    private fun pcmToMonoFloats(raw: ByteArray, bitDepth: Int, channels: Int): FloatArray {
        val bps = (bitDepth / 8).coerceAtLeast(1)
        val frameSize = bps * channels
        val frames = raw.size / frameSize
        val out = FloatArray(frames)
        var frameIdx = 0
        var byteIndex = 0
        while (frameIdx < frames) {
            var sum = 0f
            var c = 0
            while (c < channels) {
                val off = byteIndex + c * bps
                val sample = when (bitDepth) {
                    8 -> { val u = raw[off].toInt() and 0xFF; ((u - 128) / 128f) }
                    16 -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                    24 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt(); var v = b0 or (b1 shl 8) or (b2 shl 16); if ((v and 0x800000) != 0) v = v or -0x1000000; (v / 8388608f) }
                    32 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt() and 0xFF; val b3 = raw[off + 3].toInt(); val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24); if (v == Int.MIN_VALUE) -1f else (v / 2147483648f) }
                    else -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                }
                sum += sample; c++
            }
            out[frameIdx] = (sum / channels)
            frameIdx++
            byteIndex += frameSize
        }
        return out
    }

    private fun findNearestZeroCrossingMono(samples: FloatArray, fromIndex: Int, searchRadius: Int): Int {
        val n = samples.size
        var bestIdx = fromIndex.coerceIn(0, n - 2)
        var minAbsSum = Float.MAX_VALUE
        val start = (fromIndex - searchRadius).coerceAtLeast(0)
        val end = (fromIndex + searchRadius).coerceAtMost(n - 2)
        var i = start
        while (i <= end) {
            val s0 = samples[i]
            val s1 = samples[i + 1]
            val crosses = (s0 <= 0f && s1 >= 0f) || (s0 >= 0f && s1 <= 0f)
            if (crosses) {
                val score = abs(s0) + abs(s1)
                if (score < minAbsSum) {
                    minAbsSum = score
                    bestIdx = i
                }
            }
            i++
        }
        return bestIdx
    }

    private fun applyEdgeFadesPcm(raw: ByteArray, bitDepth: Int, channels: Int, sampleRate: Int, fadeMs: Int): ByteArray {
        if (fadeMs <= 0 || raw.isEmpty()) return raw
        val samplesPerMs = sampleRate / 1000f
        val fadeSamples = kotlin.math.max(1, (samplesPerMs * fadeMs).toInt())
        val bps = (bitDepth / 8)
        val frameSize = bps * channels
        val frames = raw.size / frameSize
        val mono = pcmToMonoFloats(raw, bitDepth, channels)
        // Fade-In
        var i = 0
        while (i < fadeSamples && i < mono.size) { mono[i] *= (i.toFloat() / fadeSamples.toFloat()); i++ }
        // Fade-Out
        i = 0
        while (i < fadeSamples && i < mono.size) { val idx = mono.size - 1 - i; mono[idx] *= (i.toFloat() / fadeSamples.toFloat()); i++ }
        // Write back
        val out = raw.copyOf()
        var frameIdx = 0
        var byteIndex = 0
        while (frameIdx < frames) {
            val v = mono[frameIdx].coerceIn(-1f, 1f)
            var c = 0
            while (c < channels) {
                val off = byteIndex + c * bps
                when (bitDepth) {
                    8 -> { val u = ((v * 128f) + 128f).toInt().coerceIn(0, 255); out[off] = u.toByte() }
                    16 -> { val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(); out[off] = (s.toInt() and 0xFF).toByte(); out[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte() }
                    24 -> { var vv = (v * 8388608f).toInt(); out[off] = (vv and 0xFF).toByte(); out[off + 1] = ((vv shr 8) and 0xFF).toByte(); out[off + 2] = ((vv shr 16) and 0xFF).toByte() }
                    32 -> { val iv = (v * 2147483648f).toInt(); out[off] = (iv and 0xFF).toByte(); out[off + 1] = ((iv shr 8) and 0xFF).toByte(); out[off + 2] = ((iv shr 16) and 0xFF).toByte(); out[off + 3] = ((iv shr 24) and 0xFF).toByte() }
                    else -> { val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(); out[off] = (s.toInt() and 0xFF).toByte(); out[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte() }
                }
                c++
            }
            frameIdx++
            byteIndex += frameSize
        }
        return out
    }

    private fun crossfadeEqualPower(
        left: ByteArray,
        right: ByteArray,
        bitDepth: Int,
        channels: Int,
        sampleRate: Int,
        overlapMs: Int
    ): Pair<ByteArray, ByteArray> {
        val bps = (bitDepth / 8)
        val frameSize = bps * channels
        val framesLeft = left.size / frameSize
        val framesRight = right.size / frameSize
        val overlapSamples = kotlin.math.max(1, (sampleRate / 1000f * overlapMs).toInt())
        if (framesLeft <= overlapSamples || framesRight <= overlapSamples) return left to right

        // Konvertiere Overlap-Segmente in Floats
        fun pcmSegmentToMonoFloats(raw: ByteArray, startFrame: Int, lengthFrames: Int): FloatArray {
            val out = FloatArray(lengthFrames)
            var frameIdx = 0
            var byteIndex = startFrame * frameSize
            while (frameIdx < lengthFrames) {
                var sum = 0f; var c = 0
                while (c < channels) {
                    val off = byteIndex + c * bps
                    val sample = when (bitDepth) {
                        8 -> { val u = raw[off].toInt() and 0xFF; ((u - 128) / 128f) }
                        16 -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                        24 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt(); var v = b0 or (b1 shl 8) or (b2 shl 16); if ((v and 0x800000) != 0) v = v or -0x1000000; (v / 8388608f) }
                        32 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt() and 0xFF; val b3 = raw[off + 3].toInt(); val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24); if (v == Int.MIN_VALUE) -1f else (v / 2147483648f) }
                        else -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                    }
                    sum += sample; c++
                }
                out[frameIdx] = (sum / channels)
                frameIdx++
                byteIndex += frameSize
            }
            return out
        }

        val leftOverlapStart = framesLeft - overlapSamples
        val rightOverlapStart = 0
        val leftSeg = pcmSegmentToMonoFloats(left, leftOverlapStart, overlapSamples)
        val rightSeg = pcmSegmentToMonoFloats(right, rightOverlapStart, overlapSamples)

        // Equal-Power-Crossfade: gL = cos(theta), gR = sin(theta)
        val mixed = FloatArray(overlapSamples)
        var i = 0
        while (i < overlapSamples) {
            val t = i.toFloat() / overlapSamples
            val theta = (t * (PI / 2.0)).toFloat()
            val gL = cos(theta)
            val gR = sin(theta)
            mixed[i] = leftSeg[i] * gL + rightSeg[i] * gR
            i++
        }

        // Schreibe Mix in PCM und ersetze die Overlap-Bereiche
        val outLeft = left.copyOf()
        val outRight = right.copyOf()
        fun writeMonoIntoPcm(dst: ByteArray, startFrame: Int, mono: FloatArray) {
            var frameIdx = 0
            var byteIndex = startFrame * frameSize
            while (frameIdx < mono.size) {
                val v = mono[frameIdx].coerceIn(-1f, 1f)
                var c = 0
                while (c < channels) {
                    val off = byteIndex + c * bps
                    when (bitDepth) {
                        8 -> { val u = ((v * 128f) + 128f).toInt().coerceIn(0, 255); dst[off] = u.toByte() }
                        16 -> { val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(); dst[off] = (s.toInt() and 0xFF).toByte(); dst[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte() }
                        24 -> { val vv = (v * 8388608f).toInt(); dst[off] = (vv and 0xFF).toByte(); dst[off + 1] = ((vv shr 8) and 0xFF).toByte(); dst[off + 2] = ((vv shr 16) and 0xFF).toByte() }
                        32 -> { val iv = (v * 2147483648f).toInt(); dst[off] = (iv and 0xFF).toByte(); dst[off + 1] = ((iv shr 8) and 0xFF).toByte(); dst[off + 2] = ((iv shr 16) and 0xFF).toByte(); dst[off + 3] = ((iv shr 24) and 0xFF).toByte() }
                        else -> { val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(); dst[off] = (s.toInt() and 0xFF).toByte(); dst[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte() }
                    }
                    c++
                }
                frameIdx++
                byteIndex += frameSize
            }
        }

        writeMonoIntoPcm(outLeft, leftOverlapStart, mixed)
        writeMonoIntoPcm(outRight, rightOverlapStart, mixed)

        return outLeft to outRight
    }

    private fun cutAudioAtSelection(trackIndex: Int, track: AudioTimelineTrack, cutTimeMs: Long): Boolean {
        val clip = track.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs } ?: return false
        val relativeOffsetMs = cutTimeMs - clip.startTimeMs
        if (relativeOffsetMs <= 0 || relativeOffsetMs >= clip.durationMs) return false

        val rawData = clip.rawData
        val bytesPerSample = (clip.bitDepth / 8) * clip.channels
        val samplesPerMs = clip.sampleRate.toDouble() / 1000.0
        val samplesToSplitExact = relativeOffsetMs * samplesPerMs
        var samplesToSplit = round(samplesToSplitExact).toLong()

        if (rawData != null && rawData.isNotEmpty()) {
            // Zero-Crossing Snap nahe der Zielposition
            val mono = pcmToMonoFloats(rawData, clip.bitDepth, clip.channels)
            val radius = 128 // etwas größer für sicheres Einrasten
            samplesToSplit = findNearestZeroCrossingMono(mono, samplesToSplit.toInt(), radius).toLong()
        }

        val bytesToSplit = (samplesToSplit * bytesPerSample).toInt()
        val alignedBytesToSplit = bytesPerSample * (bytesToSplit / bytesPerSample)

        if (rawData == null || rawData.isEmpty() || alignedBytesToSplit <= 0 || alignedBytesToSplit >= rawData.size) {
            val firstDuration = relativeOffsetMs
            val secondDuration = clip.durationMs - relativeOffsetMs
            if (secondDuration <= 0) return false
            val first = clip.copy(startTimeMs = clip.startTimeMs, durationMs = firstDuration, rawData = rawData)
            val second = clip.copy(startTimeMs = cutTimeMs, durationMs = secondDuration, rawData = rawData)
            replaceAudioClip(trackIndex, clip, first, second)
            return true
        }

        // Teile schneiden
        var firstPart = rawData.sliceArray(0 until alignedBytesToSplit)
        var secondPart = rawData.sliceArray(alignedBytesToSplit until rawData.size)

        // Crossfade-Overlap einbauen (z. B. 8ms)
        val (cfLeft, cfRight) = crossfadeEqualPower(firstPart, secondPart, clip.bitDepth, clip.channels, clip.sampleRate, overlapMs = 8)
        firstPart = cfLeft
        secondPart = cfRight

        // Mikro-Fades anwenden, um Klicks weiter zu minimieren
        firstPart = applyEdgeFadesPcm(firstPart, clip.bitDepth, clip.channels, clip.sampleRate, fadeMs = 2)
        secondPart = applyEdgeFadesPcm(secondPart, clip.bitDepth, clip.channels, clip.sampleRate, fadeMs = 2)

        // Sample-exakte Dauer berechnen, sodass keine Lücke entsteht
        val samplesFirst = firstPart.size / bytesPerSample
        val samplesSecond = secondPart.size / bytesPerSample
        val durationFirstMsCalc = (samplesFirst / samplesPerMs).toLong()
        var durationSecondMsCalc = (samplesSecond / samplesPerMs).toLong()

        // Summe der berechneten Dauern auf Originaldauer trimmen/erweitern (falls Rundung)
        val totalCalc = durationFirstMsCalc + durationSecondMsCalc
        val delta = clip.durationMs - totalCalc
        if (delta != 0L) durationSecondMsCalc = (durationSecondMsCalc + delta).coerceAtLeast(1L)
        if (durationFirstMsCalc <= 0 || durationSecondMsCalc <= 0) return false

        val first = AudioEntry(
            startTimeMs = clip.startTimeMs,
            durationMs = durationFirstMsCalc,
            fileName = clip.fileName,
            rawData = firstPart,
            sampleRate = clip.sampleRate,
            channels = clip.channels,
            bitDepth = clip.bitDepth
        )
        val second = AudioEntry(
            startTimeMs = clip.startTimeMs + durationFirstMsCalc,
            durationMs = durationSecondMsCalc,
            fileName = clip.fileName,
            rawData = secondPart,
            sampleRate = clip.sampleRate,
            channels = clip.channels,
            bitDepth = clip.bitDepth
        )
        replaceAudioClip(trackIndex, clip, first, second)
        return true
    }

    private fun cutMidiAtSelection(trackIndex: Int, track: TimelineTrack<*>, cutTimeMs: Long): Boolean {
        val midiTrack = track as TimelineTrack<MidiEntry>
        val clip = midiTrack.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs } ?: return false
        val relativeOffsetMs = cutTimeMs - clip.startTimeMs
        if (relativeOffsetMs <= 0 || relativeOffsetMs >= clip.durationMs) return false

        // Split notes based on cut time
        val notesInFirst = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()
        val notesInSecond = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()
        
        clip.notes.forEach { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs
            
            when {
                // Note entirely in first clip
                noteEnd <= relativeOffsetMs -> notesInFirst.add(note)
                // Note entirely in second clip
                noteStart >= relativeOffsetMs -> {
                    // Adjust note timing relative to second clip start
                    notesInSecond.add(note.copy(startTimeMs = noteStart - relativeOffsetMs))
                }
                // Note spans across cut - split it
                else -> {
                    // Add truncated note to first clip
                    notesInFirst.add(note.copy(durationMs = relativeOffsetMs - noteStart))
                    // Add remaining part to second clip
                    notesInSecond.add(note.copy(
                        startTimeMs = 0,
                        durationMs = noteEnd - relativeOffsetMs
                    ))
                }
            }
        }

        val firstDuration = relativeOffsetMs
        val secondDuration = clip.durationMs - relativeOffsetMs
        
        val first = clip.copy(
            startTimeMs = clip.startTimeMs,
            durationMs = firstDuration,
            notes = notesInFirst
        )
        val second = clip.copy(
            startTimeMs = cutTimeMs,
            durationMs = secondDuration,
            notes = notesInSecond
        )
        
        replaceMidiClip(trackIndex, clip, first, second)
        return true
    }

    private fun replaceAudioClip(trackIndex: Int, original: AudioEntry, first: AudioEntry, second: AudioEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val audioTrack = currentTracks[trackIndex] as? AudioTimelineTrack ?: return
        audioTrack.entries.remove(original.startTimeMs)
        audioTrack.entries[first.startTimeMs] = first
        audioTrack.entries[second.startTimeMs] = second
        val newTrack = AudioTimelineTrack().apply { entries.putAll(audioTrack.entries) }
        currentTracks[trackIndex] = newTrack
        TimelineRepository.tracks.value = currentTracks.toList()

        UndoManager.addAction(
            UndoableAction.TimelineClipSplit(
                trackIndex = trackIndex,
                original = original,
                left = first,
                right = second
            )
        )
    }

    private fun replaceMidiClip(trackIndex: Int, original: MidiEntry, first: MidiEntry, second: MidiEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val track = currentTracks[trackIndex]

        if (track !is MidiTimelineTrack) return
        
        val midiTrack = track as TimelineTrack<MidiEntry>
        midiTrack.entries.remove(original.startTimeMs)
        midiTrack.entries[first.startTimeMs] = first
        midiTrack.entries[second.startTimeMs] = second
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }

        currentTracks[trackIndex] = newTrack
        TimelineRepository.tracks.value = currentTracks.toList()
        
        UndoManager.addAction(
            UndoableAction.MidiTimelineClipSplit(
                trackIndex = trackIndex,
                original = original,
                left = first,
                right = second
            )
        )
    }
}
