package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import kotlinx.serialization.Serializable

@Serializable
class AudioTimelineTrack : TimelineTrack<AudioEntry>() {
    override var volume: Float = 1f
    override var isMuted: Boolean = false
    override var isSoloed: Boolean = false
    override val automationLanes: MutableList<TimelineAutomationLane> = mutableListOf()
    override val entries: MutableMap<Long, AudioEntry> = mutableMapOf()
    override var trackId: String = UUID.randomUUID()
    override val kind: TimelineTrackKind = TimelineTrackKind.AUDIO

    fun copyWithEntries(
        entriesToCopy: Map<Long, AudioEntry> = entries,
        preserveTrackIdentity: Boolean = true
    ): AudioTimelineTrack {
        return AudioTimelineTrack().apply {
            if (preserveTrackIdentity) {
                trackId = this@AudioTimelineTrack.trackId
            }
            name = this@AudioTimelineTrack.name
            copyMixerStateFrom(other = this@AudioTimelineTrack)
            entries.putAll(entriesToCopy)
        }
    }

    suspend fun addFromFile(file: PlatformFile, at: Long = 0) {
        println("AudioTimelineTrack: Adding file ${file.path} at position $at")

        if (!file.exists()) {
            println("AudioTimelineTrack: File does not exist: ${file.path}")
            return
        }

        val audio = Echo.decodeAudioFile(file.path)

        if (audio == null) {
            println("AudioTimelineTrack: Failed to decode audio file: ${file.path}")
            return
        }

        println("AudioTimelineTrack: Successfully decoded audio - duration: ${audio.durationMs}ms, sample rate: ${audio.sampleRate}")

        val source = AudioSource(
            id = UUID.randomUUID(),
            fileName = file.path.substringAfterLast('/'),
            rawData = audio.rawData ?: return,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
            bitDepth = audio.bitDepth
        )
        AudioSourceLibrary.add(source)

        val bytesPerSample = (source.bitDepth / 8) * source.channels
        val totalSamples = source.rawData.size.toLong() / bytesPerSample

        entries[at] = AudioEntry(
            startTimeMs = at,
            durationMs = audio.durationMs,
            fileName = source.fileName,
            sourceId = source.id,
            clipStartSample = 0L,
            clipEndSample = totalSamples,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            startTimeUs = msToUs(at),
            durationUs = samplesToUs(totalSamples, source.sampleRate)
        )

        println("AudioTimelineTrack: Audio entry added. totalSamples=$totalSamples, sourceId=${source.id}")
    }
}
