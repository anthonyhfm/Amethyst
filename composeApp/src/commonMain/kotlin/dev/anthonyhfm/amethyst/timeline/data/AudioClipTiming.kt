package dev.anthonyhfm.amethyst.timeline.data

import kotlin.math.roundToLong

private const val MICROS_PER_MILLISECOND = 1_000L
private const val MICROS_PER_SECOND = 1_000_000L

fun msToUs(timeMs: Long): Long = timeMs * MICROS_PER_MILLISECOND

fun usToRoundedMs(timeUs: Long): Long = (timeUs.toDouble() / MICROS_PER_MILLISECOND.toDouble()).roundToLong()

fun samplesToUs(sampleCount: Long, sampleRate: Int): Long {
    if (sampleRate <= 0 || sampleCount <= 0L) return 0L
    return ((sampleCount.toDouble() * MICROS_PER_SECOND.toDouble()) / sampleRate.toDouble()).roundToLong()
}

fun usToSamples(timeUs: Long, sampleRate: Int): Long {
    if (sampleRate <= 0 || timeUs <= 0L) return 0L
    return ((timeUs.toDouble() * sampleRate.toDouble()) / MICROS_PER_SECOND.toDouble()).roundToLong()
}

val AudioEntry.endTimeUs: Long
    get() = startTimeUs + durationUs

val AudioEntry.clipSpanUs: Long
    get() = samplesToUs(clipSampleCount, sampleRate)

fun AudioEntry.copyWithPreciseTiming(
    startTimeUs: Long = this.startTimeUs,
    durationUs: Long = this.durationUs,
    clipStartSample: Long = this.clipStartSample,
    clipEndSample: Long = this.clipEndSample,
    name: String = this.name
): AudioEntry {
    val normalizedStartUs = startTimeUs.coerceAtLeast(0L)
    val normalizedDurationUs = durationUs.coerceAtLeast(0L)
    return copy(
        startTimeMs = usToRoundedMs(normalizedStartUs),
        durationMs = usToRoundedMs(normalizedDurationUs),
        clipStartSample = clipStartSample,
        clipEndSample = clipEndSample,
        name = name,
        startTimeUs = normalizedStartUs,
        durationUs = normalizedDurationUs
    )
}

fun AudioEntry.copyWithShiftedStartMs(startTimeMs: Long): AudioEntry =
    copyWithPreciseTiming(startTimeUs = msToUs(startTimeMs))

fun AudioEntry.cropAudioEntryEnd(newEndMs: Long): AudioEntry? {
    val newEndUs = msToUs(newEndMs)
    if (newEndUs <= startTimeUs) return null
    val newDurationUs = newEndUs - startTimeUs
    val newEndSample = (clipStartSample + usToSamples(newDurationUs, sampleRate))
        .coerceIn(clipStartSample, clipEndSample)
    if (newEndSample <= clipStartSample) return null
    return copyWithPreciseTiming(
        durationUs = newDurationUs,
        clipEndSample = newEndSample
    )
}

fun AudioEntry.buildSegment(segStartMs: Long, segEndMs: Long): AudioEntry? {
    val segStartUs = msToUs(segStartMs)
    val segEndUs = msToUs(segEndMs)
    if (segEndUs <= segStartUs) return null

    val relativeStartUs = (segStartUs - startTimeUs).coerceAtLeast(0L)
    val relativeEndUs = (segEndUs - startTimeUs).coerceAtLeast(relativeStartUs)
    val newStartSample = (clipStartSample + usToSamples(relativeStartUs, sampleRate))
        .coerceIn(clipStartSample, clipEndSample)
    val newEndSample = (clipStartSample + usToSamples(relativeEndUs, sampleRate))
        .coerceIn(newStartSample, clipEndSample)
    if (newEndSample <= newStartSample) return null

    return copyWithPreciseTiming(
        startTimeUs = segStartUs,
        durationUs = segEndUs - segStartUs,
        clipStartSample = newStartSample,
        clipEndSample = newEndSample
    )
}

fun AudioEntry.trimAudioEntry(trimStart: Long, trimEnd: Long): AudioEntry? {
    return buildSegment(trimStart, trimEnd)
}
