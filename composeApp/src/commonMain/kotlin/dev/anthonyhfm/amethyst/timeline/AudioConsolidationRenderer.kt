package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.engine.audio.source.BufferedPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmByteStorage
import dev.anthonyhfm.amethyst.core.engine.audio.source.PolyphaseSincResampler
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.usToSamples
import kotlin.math.roundToLong

internal data class AudioConsolidationTarget(
    val trackIndex: Int,
    val startUs: Long,
    val endUs: Long,
)

internal data class ConsolidatedAudio(
    val source: AudioSource,
    val entry: AudioEntry,
)

internal fun renderConsolidatedAudio(
    target: AudioConsolidationTarget,
    entries: List<AudioEntry>,
    sources: Map<String, AudioSource>,
    checkpoint: (Float) -> Unit = {},
): ConsolidatedAudio {
    require(entries.isNotEmpty() && target.startUs >= 0L && target.endUs > target.startUs)
    val inputs = entries.map { entry ->
        val source = requireNotNull(value = sources[entry.sourceId]) {
            "Audio source is missing: ${entry.fileName}"
        }
        require(source.sampleRate == entry.sampleRate && source.channels == entry.channels && source.bitDepth == entry.bitDepth) {
            "Audio source is still loading: ${entry.fileName}"
        }
        require(entry.clipStartSample >= 0L && entry.clipEndSample in entry.clipStartSample..source.totalSamples) {
            "Invalid audio region: ${entry.fileName}"
        }
        entry to source
    }
    val sampleRate = inputs.maxOf { it.second.sampleRate }
    val channels = inputs.maxOf { it.second.channels }
    val bitDepth = maxOf(24, inputs.maxOf { it.second.bitDepth })
    require(sampleRate > 0 && channels in 1..2 && bitDepth in setOf(24, 32))
    val frames = usToSamples(timeUs = target.endUs - target.startUs, sampleRate = sampleRate)
    val bytesPerSample = bitDepth / 8
    val bytesPerFrame = channels * bytesPerSample
    require(frames in 1L..(Int.MAX_VALUE.toLong() / bytesPerFrame)) {
        "The selected audio is too long to consolidate"
    }
    val cursors = inputs.filterNot { it.first.isMuted }.map { (entry, source) ->
        ConsolidationCursor(
            entry = entry,
            source = source,
            target = target,
            outputRate = sampleRate,
            outputFrames = frames,
        )
    }.sortedBy { it.startFrame }
    checkpoint(0f)
    val storage = ProjectPcmFiles.storeStreaming(expectedBytes = (frames * bytesPerFrame).toInt()) { writer ->
        val blockFrames = 4096
        val mixed = FloatArray(size = blockFrames * channels)
        val bytes = ByteArray(size = blockFrames * bytesPerFrame)
        val copyBuffer = ByteArray(size = bytes.size)
        var position = 0L
        while (position < frames) {
            val count = minOf(blockFrames.toLong(), frames - position).toInt()
            val active = cursors.filter { it.startFrame < position + count && it.endFrame > position }
            bytes.fill(element = 0)
            val canCopy = active.all { it.canCopy(sampleRate = sampleRate, channels = channels, bitDepth = bitDepth) } &&
                active.zipWithNext().all { (left, right) -> left.endFrame <= right.startFrame }
            if (canCopy) {
                active.forEach { cursor ->
                    cursor.copyBlock(
                        position = position,
                        count = count,
                        destination = bytes,
                        scratch = copyBuffer,
                        bytesPerFrame = bytesPerFrame,
                    )
                }
            } else {
                mixed.fill(element = 0f)
                active.forEach { cursor ->
                    cursor.mixBlock(position = position, count = count, destination = mixed, outputChannels = channels)
                }
                val scale = if (bitDepth == 24) 8_388_608.0 else 2_147_483_648.0
                repeat(times = count * channels) { index ->
                    val value = (mixed[index].toDouble() * scale).roundToLong()
                        .coerceIn(minimumValue = -scale.toLong(), maximumValue = scale.toLong() - 1L)
                    repeat(times = bytesPerSample) { byte ->
                        bytes[index * bytesPerSample + byte] = (value shr (byte * 8)).toByte()
                    }
                }
            }
            writer.write(bytes = bytes, offset = 0, length = count * bytesPerFrame)
            position += count
            checkpoint(position.toFloat() / frames)
        }
    }
    try {
        val first = entries.minBy { it.startTimeUs }
        val name = first.name.ifBlank { first.fileName.substringBeforeLast(delimiter = '.') }
        val source = AudioSource(
            id = UUID.randomUUID(),
            fileName = "$name (Consolidated).wav",
            rawData = byteArrayOf(),
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
        ).withPcmStorage(storage = storage)
        return ConsolidatedAudio(
            source = source,
            entry = AudioEntry(
                startTimeMs = 0L,
                durationMs = 0L,
                fileName = source.fileName,
                name = name,
                sourceId = source.id,
                clipStartSample = 0L,
                clipEndSample = frames,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
            ).copyWithPreciseTiming(
                startTimeUs = target.startUs,
                durationUs = target.endUs - target.startUs,
            ),
        )
    } catch (failure: Throwable) {
        discardConsolidationStorage(storage = storage)
        throw failure
    }
}

internal fun discardConsolidationStorage(storage: PcmByteStorage) {
    storage.releaseCachedPages()
    ProjectPcmFiles.discard(paths = setOfNotNull(storage.filePath))
}

private class ConsolidationCursor(
    private val entry: AudioEntry,
    private val source: AudioSource,
    target: AudioConsolidationTarget,
    outputRate: Int,
    outputFrames: Long,
) {
    val startFrame = usToSamples(
        timeUs = (entry.startTimeUs - target.startUs).coerceAtLeast(minimumValue = 0L),
        sampleRate = outputRate,
    ).coerceAtMost(maximumValue = outputFrames)
    val endFrame = usToSamples(
        timeUs = (entry.endTimeUs - target.startUs).coerceAtLeast(minimumValue = 0L),
        sampleRate = outputRate,
    ).coerceIn(minimumValue = startFrame, maximumValue = outputFrames)
    private val sourceStart = entry.clipStartSample + usToSamples(
        timeUs = (target.startUs - entry.startTimeUs).coerceAtLeast(minimumValue = 0L),
        sampleRate = source.sampleRate,
    )
    private val pcm = BufferedPcmAudioSource(source = source.pcmSource())
    private val resampler = PolyphaseSincResampler(
        sourceRate = source.sampleRate,
        outputRate = outputRate,
        channels = source.channels,
    )
    private val frame = FloatArray(size = source.channels)

    fun canCopy(sampleRate: Int, channels: Int, bitDepth: Int): Boolean {
        return source.sampleRate == sampleRate && source.channels == channels && source.bitDepth == bitDepth
    }

    fun copyBlock(position: Long, count: Int, destination: ByteArray, scratch: ByteArray, bytesPerFrame: Int) {
        val first = maxOf(position, startFrame)
        val sourceFrame = sourceStart + first - startFrame
        val last = minOf(position + count, endFrame, first + entry.clipEndSample - sourceFrame)
        if (last <= first) {
            return
        }
        val byteCount = ((last - first) * bytesPerFrame).toInt()
        source.readPcmInto(destination = scratch, sourceOffset = (sourceFrame * bytesPerFrame).toInt(), length = byteCount)
        scratch.copyInto(destination = destination, destinationOffset = ((first - position) * bytesPerFrame).toInt(), endIndex = byteCount)
    }

    fun mixBlock(position: Long, count: Int, destination: FloatArray, outputChannels: Int) {
        val first = maxOf(position, startFrame)
        val last = minOf(position + count, endFrame)
        resampler.reset(sourceFrame = sourceStart + (first - startFrame) * resampler.sourceFramesPerOutputFrame)
        var index = first
        while (index < last) {
            resampler.readFrame(
                source = pcm,
                destination = frame,
                lowerBoundFrame = entry.clipStartSample,
                upperBoundFrameExclusive = entry.clipEndSample,
            )
            repeat(times = outputChannels) { channel ->
                destination[(index - position).toInt() * outputChannels + channel] += frame[minOf(channel, source.channels - 1)]
            }
            resampler.advance()
            index++
        }
    }
}
