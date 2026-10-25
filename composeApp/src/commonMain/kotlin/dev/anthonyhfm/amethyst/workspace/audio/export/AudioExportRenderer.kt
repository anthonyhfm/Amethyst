package dev.anthonyhfm.amethyst.workspace.audio.export

import dev.anthonyhfm.amethyst.core.engine.audio.dsp.StereoLinkedLookaheadLimiter
import dev.anthonyhfm.amethyst.core.engine.audio.source.PolyphaseSincResampler
import dev.anthonyhfm.amethyst.timeline.automation.TimelineAutomationEvaluator
import dev.anthonyhfm.amethyst.timeline.data.usToSamples

internal fun renderAudioExport(
    snapshot: AudioExportSnapshot,
    options: AudioExportOptions,
    checkpoint: (Float) -> Unit,
    consume: (FloatArray, Int, Int) -> Unit,
) {
    require(options.sampleRate > 0 && snapshot.durationUs > 0L)
    require(snapshot.channels in 1..2)
    require(!snapshot.hasMissingSources) { "An audio source is missing. Reimport it and try again." }
    val frames = audioExportFrameCount(snapshot = snapshot, sampleRate = options.sampleRate)
    require(frames > 0L) { "The selected audio is shorter than one sample." }
    val cursors = snapshot.clips.filter { it.audible }.map { clip ->
        ExportCursor(clip = clip, snapshot = snapshot, sampleRate = options.sampleRate)
    }
    val limiter = if (snapshot.isTimeline) {
        StereoLinkedLookaheadLimiter().apply { prepare(sampleRate = options.sampleRate) }
    } else {
        null
    }
    val latency = limiter?.lookaheadFrames ?: 0
    val buffer = FloatArray(size = 4096 * snapshot.channels)
    var position = 0L
    checkpoint(0f)
    while (position < frames + latency) {
        val count = minOf(4096L, frames + latency - position).toInt()
        buffer.fill(element = 0f)
        cursors.forEach { cursor ->
            cursor.mix(position = position, count = count, output = buffer, channels = snapshot.channels)
        }
        limiter?.processInterleaved(samples = buffer, frameCount = count)
        val skip = (latency - position).coerceIn(minimumValue = 0L, maximumValue = count.toLong()).toInt()
        if (skip < count) {
            consume(buffer, skip * snapshot.channels, count - skip)
        }
        position += count
        checkpoint((position.toDouble() / (frames + latency)).toFloat())
    }
}

internal fun audioExportFrameCount(snapshot: AudioExportSnapshot, sampleRate: Int): Long {
    val source = snapshot.clips.singleOrNull()?.source
    return if (!snapshot.isTimeline && source != null && source.sampleRate == sampleRate) {
        source.totalSamples
    } else {
        usToSamples(timeUs = snapshot.durationUs, sampleRate = sampleRate)
    }
}

private class ExportCursor(
    private val clip: AudioExportClip,
    private val snapshot: AudioExportSnapshot,
    private val sampleRate: Int,
) {
    private val source = requireNotNull(value = clip.source)
    private val pcm = source.pcmSource()
    private val startFrame = usToSamples(timeUs = clip.startUs - snapshot.startUs, sampleRate = sampleRate)
    private val endFrame = if (!snapshot.isTimeline && source.sampleRate == sampleRate) {
        source.totalSamples
    } else {
        usToSamples(timeUs = clip.endUs - snapshot.startUs, sampleRate = sampleRate)
    }
    private val resampler = PolyphaseSincResampler(
        sourceRate = source.sampleRate,
        outputRate = sampleRate,
        channels = source.channels,
    )
    private val frame = FloatArray(size = source.channels)
    private var lastTimeMs = Long.MIN_VALUE
    private var gain = 1f

    init {
        require(clip.entry.clipStartSample >= 0L && clip.entry.clipEndSample <= source.totalSamples) {
            "An audio clip has invalid source boundaries: ${clip.entry.fileName}"
        }
        require(source.sampleRate == clip.entry.sampleRate && source.channels == clip.entry.channels) {
            "An audio source is still loading: ${clip.entry.fileName}"
        }
    }

    fun mix(position: Long, count: Int, output: FloatArray, channels: Int) {
        val first = maxOf(position, startFrame)
        val last = minOf(position + count, endFrame)
        if (last <= first) {
            return
        }
        resampler.reset(
            sourceFrame = clip.entry.clipStartSample +
                usToSamples(timeUs = clip.startUs - clip.entry.startTimeUs, sampleRate = source.sampleRate).toDouble() +
                (first - startFrame) * resampler.sourceFramesPerOutputFrame,
        )
        var index = first
        while (index < last) {
            val timeMs = (snapshot.startUs + index * 1_000_000L / sampleRate) / 1000L
            if (timeMs != lastTimeMs) {
                lastTimeMs = timeMs
                gain = clip.track?.let { TimelineAutomationEvaluator.evaluate(track = it, timeMs = timeMs).volume } ?: 1f
            }
            resampler.readFrame(
                source = pcm,
                destination = frame,
                lowerBoundFrame = clip.entry.clipStartSample,
                upperBoundFrameExclusive = clip.entry.clipEndSample,
            )
            repeat(times = channels) { channel ->
                output[(index - position).toInt() * channels + channel] += frame[minOf(channel, source.channels - 1)] * gain
            }
            resampler.advance()
            index++
        }
    }
}
