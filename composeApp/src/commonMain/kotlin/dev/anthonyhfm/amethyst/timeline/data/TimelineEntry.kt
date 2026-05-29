package dev.anthonyhfm.amethyst.timeline.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.automation.TimelineTrackAutomationState
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import kotlin.math.abs

interface TimelineEntry {
    val startTimeMs: Long
    val durationMs: Long
    val endTimeMs: Long get() = startTimeMs + durationMs

    fun start(
        startAt: Long? = null,
        automation: TimelineTrackAutomationState = TimelineTrackAutomationState()
    )
    fun updateAutomation(automation: TimelineTrackAutomationState) = Unit
    fun stop()
}

/**
 * A region on the audio timeline that references an [AudioSource] via [sourceId].
 *
 * All editing operations (cut, trim, crop) are **non-destructive**: they only adjust
 * [clipStartSample] / [clipEndSample]. The source PCM data in [AudioSourceLibrary]
 * is never copied or modified.
 *
 * [clipStartSample] and [clipEndSample] are sample-accurate indices into the source's
 * [AudioSource.rawData]. Playback and waveform rendering both use these directly,
 * eliminating the ms↔sample round-trip rounding errors of the old model.
 *
 * --- Migration note ---
 * Old project files embedded the full PCM bytes inline as [legacyRawData].
 * [WorkspaceRepository] detects this on load and converts to the new format.
 */
@Serializable
data class AudioEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
    val fileName: String,
    val sourceId: String,
    val clipStartSample: Long = 0L,
    val clipEndSample: Long,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    var name: String = "",
    // Legacy fields kept for migration — populated only when loading old project files.
    val legacyRawData: ByteArray? = null,
    val legacySourceStartMs: Long = 0L,
    val legacySourceDurationMs: Long = 0L,
    val startTimeUs: Long = msToUs(startTimeMs),
    val durationUs: Long = msToUs(durationMs),
) : TimelineEntry {
    @Transient
    private var audioSourceId: String? = null

    val bytesPerSample: Int get() = (bitDepth / 8) * channels

    /** Returns the [AudioSource] for this entry from the library, or null if missing. */
    fun source(): AudioSource? = AudioSourceLibrary.get(sourceId)

    /** Total number of samples this clip spans in the source. */
    val clipSampleCount: Long get() = clipEndSample - clipStartSample

    /**
     * Builds the [Signal.AudioSignal] for batch-start (used by [TimelineRepository]).
     * Seeks to exact sample position — no ms→sample conversion error.
     */
    internal fun buildPlaybackSignal(startAt: Long?, automation: TimelineTrackAutomationState): Signal.AudioSignal? {
        val src = source() ?: return null
        val startAtUs = startAt?.let(::msToUs)
        val playheadOffsetUs = if (startAtUs != null && startAtUs > startTimeUs) {
            startAtUs - startTimeUs
        } else 0L
        val playheadOffsetSamples = usToSamples(playheadOffsetUs, sampleRate)
        val actualStartSample = (clipStartSample + playheadOffsetSamples).coerceIn(clipStartSample, clipEndSample)
        val startByte = (actualStartSample * bytesPerSample).toInt().coerceIn(0, src.rawData.size)
        val endByte = (clipEndSample * bytesPerSample).toInt().coerceIn(0, src.rawData.size)
        if (startByte >= endByte) return null
        val trimmedData = src.rawData.sliceArray(startByte until endByte)
        val remainingDurationMs = usToRoundedMs(samplesToUs(clipEndSample - actualStartSample, sampleRate))
        return Signal.AudioSignal(
            rawData = trimmedData,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            durationMs = remainingDurationMs,
            gain = automation.volume,
            pan = 0f,
            origin = "AudioEntry_$fileName"
        )
    }

    /** Stores the source ID returned by [AudioOutput.playMultiple]. */
    internal fun receiveSourceId(id: String?) {
        audioSourceId = id
        if (id != null) {
            println("AUDIO: Playing $fileName (source: $id) startTimeMs=$startTimeMs durationMs=$durationMs clipStart=$clipStartSample clipEnd=$clipEndSample")
        } else {
            println("AUDIO: Failed to start $fileName — playMultiple returned no id")
        }
    }

    override fun start(startAt: Long?, automation: TimelineTrackAutomationState) {
        val actualStartTime = startAt ?: startTimeMs
        val src = source()
        if (src == null) {
            println("No audio source in library for: $fileName (sourceId=$sourceId)")
            return
        }
        val startAtUs = startAt?.let(::msToUs)
        val playheadOffsetUs = if (startAtUs != null && startAtUs > startTimeUs) {
            startAtUs - startTimeUs
        } else 0L
        val playheadOffsetSamples = usToSamples(playheadOffsetUs, sampleRate)
        val actualStartSample = (clipStartSample + playheadOffsetSamples).coerceIn(clipStartSample, clipEndSample)
        val startByte = (actualStartSample * bytesPerSample).toInt().coerceIn(0, src.rawData.size)
        val endByte = (clipEndSample * bytesPerSample).toInt().coerceIn(0, src.rawData.size)
        if (startByte >= endByte) {
            println("No audio data for $fileName (startByte=$startByte >= endByte=$endByte)")
            return
        }
        val trimmedData = src.rawData.sliceArray(startByte until endByte)
        val remainingDurationMs = usToRoundedMs(samplesToUs(clipEndSample - actualStartSample, sampleRate))
        audioSourceId = AudioOutput.play(
            audioSignal = Signal.AudioSignal(
                rawData = trimmedData,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                durationMs = remainingDurationMs,
                gain = automation.volume,
                pan = 0f,
                origin = "AudioEntry_$fileName"
            )
        )
        if (audioSourceId != null) {
            println("Started audio entry: $fileName at ${actualStartTime}ms (source: $audioSourceId)")
        } else {
            println("Failed to start audio entry: $fileName")
        }
    }

    override fun updateAutomation(automation: TimelineTrackAutomationState) {
        audioSourceId?.let { sid ->
            AudioOutput.update(sourceId = sid, gain = automation.volume, pan = 0f)
        }
    }

    override fun stop() {
        audioSourceId?.let { sid ->
            AudioOutput.stop(sid)
            println("AUDIO: Stopped $fileName (source: $sid)")
            audioSourceId = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioEntry) return false
        if (startTimeMs != other.startTimeMs) return false
        if (durationMs != other.durationMs) return false
        if (fileName != other.fileName) return false
        if (sourceId != other.sourceId) return false
        if (clipStartSample != other.clipStartSample) return false
        if (clipEndSample != other.clipEndSample) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        if (startTimeUs != other.startTimeUs) return false
        if (durationUs != other.durationUs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = startTimeMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + clipStartSample.hashCode()
        result = 31 * result + clipEndSample.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitDepth
        result = 31 * result + startTimeUs.hashCode()
        result = 31 * result + durationUs.hashCode()
        return result
    }
}

@Serializable
data class MidiEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
    val notes: List<MidiNote> = emptyList(),
    var name: String = "MIDI Clip"
) : TimelineEntry {
    @kotlinx.serialization.Transient
    private val activeSignalOwner = Any()

    @kotlinx.serialization.Transient
    private var lastProcessedOffsetMs: Long? = null

    @kotlinx.serialization.Transient
    private val activeNotes = mutableSetOf<MidiNote>()

    @kotlinx.serialization.Transient
    private val lastSentGradientColor = mutableMapOf<MidiNote, Triple<Float, Float, Float>>()

    private fun pitchToXY(pitch: Int): Pair<Int, Int> {
        val deviceIndex = pitch / 100
        val localPitch = pitch % 100
        val x = localPitch % 10
        val y = 9 - (localPitch / 10)
        
        val device = Heaven.devices.getOrNull(deviceIndex)
        val globalX = x + (device?.position?.value?.x?.toInt() ?: 0)
        val globalY = y + (device?.position?.value?.y?.toInt() ?: 0)
        
        return Pair(globalX, globalY)
    }

    override fun start(startAt: Long?, automation: TimelineTrackAutomationState) {
        val offsetMs = playbackOffsetMs(startAt)
        syncActiveNotesAt(offsetMs)
        lastProcessedOffsetMs = offsetMs
    }

    override fun stop() {
        if (activeNotes.isEmpty()) {
            lastProcessedOffsetMs = null
            return
        }

        activeNotes.toList().forEach(::sendNoteOff)
        activeNotes.clear()
        lastSentGradientColor.clear()
        lastProcessedOffsetMs = null
    }

    fun processAtTime(currentTimeMs: Long) {
        val currentOffsetMs = playbackOffsetMs(currentTimeMs)
        val previousOffsetMs = lastProcessedOffsetMs

        if (previousOffsetMs == null || currentOffsetMs < previousOffsetMs) {
            syncActiveNotesAt(currentOffsetMs)
            lastProcessedOffsetMs = currentOffsetMs
            return
        }

        if (currentOffsetMs == previousOffsetMs) return

        notes.asSequence()
            .filter { note ->
                note.startTimeMs > previousOffsetMs && note.startTimeMs <= currentOffsetMs
            }
            .sortedBy(MidiNote::startTimeMs)
            .forEach { note ->
                if (activeNotes.add(note)) {
                    sendNoteOn(note)
                }
            }

        notes.asSequence()
            .filter { note ->
                note.endTimeMs > previousOffsetMs && note.endTimeMs <= currentOffsetMs
            }
            .sortedBy(MidiNote::endTimeMs)
            .forEach { note ->
                if (activeNotes.remove(note)) {
                    sendNoteOff(note)
                }
            }

        lastProcessedOffsetMs = currentOffsetMs

        // Update gradient notes on every tick
        for (note in activeNotes) {
            if (!note.isGradient) continue
            val noteOffsetMs = currentOffsetMs - note.startTimeMs
            val t = (noteOffsetMs.toFloat() / note.durationMs.toFloat()).coerceIn(0f, 1f)
            val (r, g, b) = GradientInterpolator.interpolate(note.led.gradient!!, t)
            val last = lastSentGradientColor[note]
            val eps = 0.004f  // ~1/255
            if (last == null || abs(last.first - r) > eps || abs(last.second - g) > eps || abs(last.third - b) > eps) {
                val (x, y) = pitchToXY(note.pitch)
                Heaven.midiEnter(listOf(Signal.LED(
                    origin = activeSignalOwner,
                    x = x, y = y,
                    color = Color(r, g, b),
                    layer = note.led.layer,
                    blendingMode = note.led.blendingMode
                )))
                lastSentGradientColor[note] = Triple(r, g, b)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MidiEntry) return false
        if (startTimeMs != other.startTimeMs) return false
        if (durationMs != other.durationMs) return false
        if (notes != other.notes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = startTimeMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + notes.hashCode()
        return result
    }

    private fun sendNoteOn(note: MidiNote) {
        val (x, y) = pitchToXY(note.pitch)
        val color = if (note.isGradient) {
            val (r, g, b) = GradientInterpolator.interpolate(note.led.gradient!!, 0f)
            Color(r, g, b)
        } else {
            Color(note.led.red, note.led.green, note.led.blue)
        }
        val signal = Signal.LED(
            origin = activeSignalOwner,
            x = x,
            y = y,
            color = color,
            layer = note.led.layer,
            blendingMode = note.led.blendingMode
        )
        Heaven.midiEnter(listOf(signal))
        if (note.isGradient) {
            lastSentGradientColor[note] = Triple(color.red, color.green, color.blue)
        }
    }

    private fun sendNoteOff(note: MidiNote) {
        val (x, y) = pitchToXY(note.pitch)
        val signal = Signal.LED(
            origin = activeSignalOwner,
            x = x,
            y = y,
            color = Color.Black,
            layer = note.led.layer,
            blendingMode = note.led.blendingMode
        )
        
        Heaven.midiEnter(listOf(signal))
    }

    private fun playbackOffsetMs(timeMs: Long?): Long {
        val playbackTimeMs = timeMs ?: startTimeMs
        return (playbackTimeMs - startTimeMs).coerceIn(0L, durationMs)
    }

    private fun syncActiveNotesAt(offsetMs: Long) {
        val targetNotes = notes
            .filter { note ->
                note.startTimeMs <= offsetMs && note.endTimeMs > offsetMs
            }
            .toSet()

        activeNotes
            .filterNot(targetNotes::contains)
            .forEach(::sendNoteOff)

        targetNotes
            .asSequence()
            .filterNot(activeNotes::contains)
            .sortedBy(MidiNote::startTimeMs)
            .forEach(::sendNoteOn)

        activeNotes.clear()
        activeNotes.addAll(targetNotes)
    }
}
