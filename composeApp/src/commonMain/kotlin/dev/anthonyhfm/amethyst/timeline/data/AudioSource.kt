package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.serialization.Serializable

/**
 * Immutable, decoded audio data for a single source file.
 * Stored once in [AudioSourceLibrary] and referenced by [AudioEntry.sourceId].
 * Never modified after creation — all edits (cuts, trims) only adjust the
 * sample indices in [AudioEntry].
 */
@Serializable
data class AudioSource(
    val id: String,
    val fileName: String,
    val rawData: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
) {
    val bytesPerSample: Int get() = (bitDepth / 8) * channels
    val totalSamples: Long get() = rawData.size.toLong() / bytesPerSample
    val totalDurationMs: Long get() = totalSamples * 1000L / sampleRate

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSource) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
