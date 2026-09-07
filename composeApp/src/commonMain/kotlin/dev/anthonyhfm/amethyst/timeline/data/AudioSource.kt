@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Immutable, decoded audio data for a single source file.
 * Stored once in [AudioLibraryRepository] and referenced by [AudioEntry.sourceId].
 * Never modified after creation — all edits (cuts, trims) only adjust the
 * sample indices in [AudioEntry].
 */
@Serializable
data class AudioSource(
    @ProtoNumber(1)
    val id: String,
    @ProtoNumber(2)
    val fileName: String,
    @ProtoNumber(3)
    val rawData: ByteArray,
    @ProtoNumber(4)
    val sampleRate: Int,
    @ProtoNumber(5)
    val channels: Int,
    @ProtoNumber(6)
    val bitDepth: Int,
    @ProtoNumber(7)
    val stemMetadata: StemMetadata? = null,
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

/** Provenance for an audio source produced by local stem separation. */
@Serializable
data class StemMetadata(
    @ProtoNumber(1)
    val parentSourceId: String,
    @ProtoNumber(2)
    val kind: StemKind,
    @ProtoNumber(3)
    val modelId: String,
)

@Serializable
enum class StemKind {
    VOCALS,
    DRUMS,
    BASS,
    OTHER,
}

/** A non-destructive, end-exclusive region inside a full project audio asset. */
@Serializable
data class AudioRegion(
    @ProtoNumber(1)
    val sourceId: String,
    @ProtoNumber(2)
    val startFrame: Long = 0L,
    @ProtoNumber(3)
    val endFrameExclusive: Long,
) {
    init {
        require(startFrame >= 0L)
        require(endFrameExclusive >= startFrame)
    }

    val frameCount: Long get() = endFrameExclusive - startFrame
}
