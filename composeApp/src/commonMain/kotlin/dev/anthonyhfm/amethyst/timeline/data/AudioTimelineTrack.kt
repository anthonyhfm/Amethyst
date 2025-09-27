package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path

class AudioTimelineTrack : TimelineTrack<AudioEntry>() {
    override val entries: MutableMap<Long, AudioEntry> = mutableMapOf(

    )

    suspend fun addFromFile(file: PlatformFile, at: Long = 0) {
        println("AudioTimelineTrack: Adding file ${file.path} at position $at")

        if (!file.exists()) {
            println("AudioTimelineTrack: File does not exist: ${file.path}")
            return
        }

        val audio = AudioDecoder.decodeAudioFile(file.path)

        if (audio == null) {
            println("AudioTimelineTrack: Failed to decode audio file: ${file.path}")
            return
        }

        println("AudioTimelineTrack: Successfully decoded audio - duration: ${audio.durationMs}ms, sample rate: ${audio.sampleRate}")

        entries[at] = AudioEntry(
            startTimeMs = at,
            durationMs = audio.durationMs,
            fileName = file.path.substringAfterLast('/'),
            rawData = audio.rawData,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
            bitDepth = audio.bitDepth
        )

        println("AudioTimelineTrack: Audio entry added to entries map. Total entries: ${entries.size}")
    }
}