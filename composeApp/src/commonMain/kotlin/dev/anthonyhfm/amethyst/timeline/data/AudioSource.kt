@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.InMemoryPcmBytes
import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmByteStorage
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.engine.audio.source.mapPcmFile
import dev.anthonyhfm.amethyst.core.engine.audio.source.MAPPED_PCM_THRESHOLD_BYTES
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Immutable, decoded audio data for a single source file.
 * Stored once in [AudioLibraryRepository] and referenced by [AudioEntry.sourceId].
 * Never modified after creation — all edits (cuts, trims) only adjust the
 * sample indices in [AudioEntry].
 */
@Serializable(with = AudioSourceSerializer::class)
class AudioSource private constructor(
    val id: String,
    val fileName: String,
    internal val pcmBytes: PcmByteStorage,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val stemMetadata: StemMetadata?,
    val isLibraryAsset: Boolean,
) {
    constructor(
        id: String,
        fileName: String,
        rawData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        stemMetadata: StemMetadata? = null,
        isLibraryAsset: Boolean = true,
    ) : this(
        id = id,
        fileName = fileName,
        pcmBytes = ProjectPcmFiles.keep(bytes = rawData),
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        stemMetadata = stemMetadata,
        isLibraryAsset = isLibraryAsset,
    )

    val rawData: ByteArray get() = pcmBytes.readAll()
    val pcmByteCount: Int get() = pcmBytes.size
    internal val residentPcmData: ByteArray? get() = (pcmBytes as? InMemoryPcmBytes)?.bytes
    internal val pcmCacheIdentity: Any get() = residentPcmData ?: pcmBytes
    internal val pcmContentHash: Int by lazy { pcmBytes.contentHash() }
    val bytesPerSample: Int get() = (bitDepth / 8) * channels
    val totalSamples: Long get() = pcmByteCount.toLong() / bytesPerSample
    val totalDurationMs: Long get() = totalSamples * 1000L / sampleRate

    private val playbackSource: PcmAudioSource by lazy {
        val resident = residentPcmData
        if (resident != null) {
            ByteArrayPcmAudioSource(
                id = id,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                rawData = resident,
            )
        } else {
            PcmAudioSource(
                id = id,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                pcmBytes = pcmBytes,
            )
        }
    }

    internal fun hasSamePcm(other: AudioSource): Boolean = pcmBytes.contentEquals(other = other.pcmBytes)

    fun pcmSource(): PcmAudioSource = playbackSource

    internal fun pcmRegionBytes(fromIndex: Int, toIndex: Int): ByteArray = pcmBytes.readRange(
        fromIndex = fromIndex,
        toIndex = toIndex,
    )

    internal fun writePcm(path: String) = pcmBytes.writeTo(path = path)

    internal fun copyFromPcmFile(path: String): AudioSource? {
        val storage = mapPcmFile(path = path, expectedBytes = -1) ?: return null
        return AudioSource(
            id = id,
            fileName = fileName,
            pcmBytes = if (storage.size >= MAPPED_PCM_THRESHOLD_BYTES) {
                storage
            } else {
                InMemoryPcmBytes(bytes = storage.readAll())
            },
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            stemMetadata = stemMetadata,
            isLibraryAsset = isLibraryAsset,
        )
    }

    fun copy(
        id: String = this.id,
        fileName: String = this.fileName,
        rawData: ByteArray? = null,
        sampleRate: Int = this.sampleRate,
        channels: Int = this.channels,
        bitDepth: Int = this.bitDepth,
        stemMetadata: StemMetadata? = this.stemMetadata,
        isLibraryAsset: Boolean = this.isLibraryAsset,
    ): AudioSource = AudioSource(
        id = id,
        fileName = fileName,
        pcmBytes = rawData?.let { ProjectPcmFiles.keep(bytes = it) } ?: pcmBytes,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        stemMetadata = stemMetadata,
        isLibraryAsset = isLibraryAsset,
    )

    override fun equals(other: Any?): Boolean = other is AudioSource && id == other.id
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
@SerialName("dev.anthonyhfm.amethyst.timeline.data.AudioSource")
private data class SerializedAudioSource(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val fileName: String,
    @ProtoNumber(3) val rawData: ByteArray,
    @ProtoNumber(4) val sampleRate: Int,
    @ProtoNumber(5) val channels: Int,
    @ProtoNumber(6) val bitDepth: Int,
    @ProtoNumber(7) val stemMetadata: StemMetadata? = null,
    @ProtoNumber(8) val isLibraryAsset: Boolean = true,
)

object AudioSourceSerializer : KSerializer<AudioSource> {
    private val delegate = SerializedAudioSource.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: AudioSource) {
        delegate.serialize(
            encoder = encoder,
            value = SerializedAudioSource(
                id = value.id,
                fileName = value.fileName,
                rawData = value.rawData,
                sampleRate = value.sampleRate,
                channels = value.channels,
                bitDepth = value.bitDepth,
                stemMetadata = value.stemMetadata,
                isLibraryAsset = value.isLibraryAsset,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AudioSource {
        val value = delegate.deserialize(decoder = decoder)
        return AudioSource(
            id = value.id,
            fileName = value.fileName,
            rawData = value.rawData,
            sampleRate = value.sampleRate,
            channels = value.channels,
            bitDepth = value.bitDepth,
            stemMetadata = value.stemMetadata,
            isLibraryAsset = value.isLibraryAsset,
        )
    }
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
