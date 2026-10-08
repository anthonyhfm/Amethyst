package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.engine.echo.AudioSourcePlayback
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionClipContext
import dev.anthonyhfm.amethyst.timeline.automation.TimelineAutomationEvaluator
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.usToSamples
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal fun buildCompositionAudioRequest(
    entry: AudioEntry,
    totalSamples: Long,
    positionMs: Long,
    rangeEndMs: Long,
    gain: Float,
    origin: Any?,
): AudioSourcePlayback? {
    val startUs = maxOf(a = msToUs(timeMs = positionMs), b = entry.startTimeUs)
    val endUs = minOf(a = msToUs(timeMs = rangeEndMs), b = entry.endTimeUs)
    if (endUs <= startUs || totalSamples <= 0L || entry.sampleRate <= 0) {
        return null
    }
    val startFrame = (entry.clipStartSample + usToSamples(
        timeUs = startUs - entry.startTimeUs,
        sampleRate = entry.sampleRate,
    )).coerceAtMost(maximumValue = minOf(a = entry.clipEndSample, b = totalSamples))
    val endFrame = minOf(
        entry.clipStartSample + usToSamples(
            timeUs = endUs - entry.startTimeUs,
            sampleRate = entry.sampleRate,
        ),
        entry.clipEndSample,
        totalSamples,
    )
    if (startFrame >= endFrame) {
        return null
    }
    return AudioSourcePlayback(
        sourceId = entry.sourceId,
        startFrame = startFrame,
        endFrameExclusive = endFrame,
        gain = gain,
        origin = origin,
    )
}

internal class CompositionAudioPreview(
    private val tracksProvider: () -> List<TimelineTrack<*>> = { TimelineRepository.tracks.value },
    private val totalSamplesProvider: (AudioEntry) -> Long? = { it.source()?.totalSamples },
    private val prepareSources: (List<String>) -> Unit = { Echo.prepareSources(sourceIds = it) },
    private val playSources: (List<AudioSourcePlayback>) -> List<String?> = { Echo.playSources(sources = it) },
    private val updateVoice: (String, Float) -> Unit = { id, gain -> Echo.update(sourceId = id, gain = gain, pan = 0f) },
    private val stopVoice: (String) -> Unit = { Echo.stop(sourceId = it) },
    private val stopOrigin: (Any) -> Unit = { Echo.stopByOrigin(origin = it) },
) {
    private data class ClipKey(val trackId: String, val startTimeUs: Long)
    private data class Voice(val entry: AudioEntry, val id: String)

    private val lock = SynchronizedObject()
    private val origin = Any()
    private val activeVoices = mutableMapOf<ClipKey, Voice>()

    fun sync(context: CompositionClipContext, positionMs: Long) {
        synchronized(lock = lock) {
            if (positionMs < context.startTimeMs || positionMs >= context.endTimeMs) {
                stop()
                return
            }
            val tracks = tracksProvider()
            val anySoloedTrack = tracks.any { it.isSoloed }
            val positionUs = msToUs(timeMs = positionMs)
            val audibleClips = tracks.filterIsInstance<AudioTimelineTrack>()
                .filter { track ->
                    TimelineAutomationEvaluator.isPlaybackEnabled(track = track, anySoloedTrack = anySoloedTrack)
                }
                .flatMap { track ->
                    track.entries.values.filter { entry ->
                        positionUs >= entry.startTimeUs && positionUs < entry.endTimeUs
                    }.map { entry -> Triple(ClipKey(trackId = track.trackId, startTimeUs = entry.startTimeUs), track, entry) }
                }
            val clipsByKey = audibleClips.associateBy { it.first }
            val iterator = activeVoices.iterator()
            while (iterator.hasNext()) {
                val (key, voice) = iterator.next()
                if (clipsByKey[key]?.third != voice.entry) {
                    stopVoice(voice.id)
                    iterator.remove()
                }
            }
            val pending = audibleClips.mapNotNull { (key, track, entry) ->
                val gain = TimelineAutomationEvaluator.evaluate(track = track, timeMs = positionMs).volume
                val voice = activeVoices[key]
                if (voice != null) {
                    updateVoice(voice.id, gain)
                    null
                } else {
                    val totalSamples = totalSamplesProvider(entry) ?: return@mapNotNull null
                    buildCompositionAudioRequest(
                        entry = entry,
                        totalSamples = totalSamples,
                        positionMs = positionMs,
                        rangeEndMs = context.endTimeMs,
                        gain = gain,
                        origin = origin,
                    )?.let { request -> Triple(key, entry, request) }
                }
            }
            if (pending.isNotEmpty()) {
                prepareSources(pending.map { it.third.sourceId }.distinct())
                val ids = playSources(pending.map { it.third })
                pending.forEachIndexed { index, (key, entry) ->
                    ids.getOrNull(index = index)?.let { id ->
                        activeVoices[key] = Voice(entry = entry, id = id)
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock = lock) {
            if (activeVoices.isNotEmpty()) {
                stopOrigin(origin)
                activeVoices.clear()
            }
        }
    }
}
