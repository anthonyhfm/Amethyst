package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/** Persists decoded library PCM as FLAC while keeping [AudioSource] PCM-only at runtime. */
@OptIn(ExperimentalSerializationApi::class)
internal object AudioSourceFlacSerializer : KSerializer<AudioSource> {
    private val byteArraySerializer = ByteArraySerializer()
    private val stemMetadataSerializer = StemMetadata.serializer().nullable

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "dev.anthonyhfm.amethyst.timeline.data.AudioSource"
    ) {
        element<String>("id")
        element<String>("fileName")
        element("flacData", byteArraySerializer.descriptor)
        element<Int>("sampleRate")
        element<Int>("channels")
        element<Int>("bitDepth")
        element("stemMetadata", stemMetadataSerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: AudioSource) {
        val flacData = try {
            AudioSourceFlacCodec.encode(
                pcmData = value.rawData,
                sampleRate = value.sampleRate,
                channels = value.channels,
                bitDepth = value.bitDepth,
            )
        } catch (cause: Throwable) {
            throw SerializationException("Unable to encode '${value.fileName}' as FLAC", cause)
        }

        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.fileName)
            encodeSerializableElement(descriptor, 2, byteArraySerializer, flacData)
            encodeIntElement(descriptor, 3, value.sampleRate)
            encodeIntElement(descriptor, 4, value.channels)
            encodeIntElement(descriptor, 5, value.bitDepth)
            if (value.stemMetadata != null) {
                encodeNullableSerializableElement(
                    descriptor,
                    6,
                    stemMetadataSerializer,
                    value.stemMetadata,
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): AudioSource {
        var id: String? = null
        var fileName: String? = null
        var flacData: ByteArray? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var stemMetadata: StemMetadata? = null

        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> fileName = decodeStringElement(descriptor, 1)
                    2 -> flacData = decodeSerializableElement(descriptor, 2, byteArraySerializer)
                    3 -> sampleRate = decodeIntElement(descriptor, 3)
                    4 -> channels = decodeIntElement(descriptor, 4)
                    5 -> bitDepth = decodeIntElement(descriptor, 5)
                    6 -> stemMetadata = decodeNullableSerializableElement(
                        descriptor,
                        6,
                        stemMetadataSerializer,
                    )
                    -1 -> break
                    else -> throw SerializationException("Unknown AudioSource field index $index")
                }
            }
        }

        val resolvedId = id ?: missing("id")
        val resolvedFileName = fileName ?: missing("fileName")
        val resolvedFlacData = flacData ?: missing("flacData")
        val resolvedSampleRate = sampleRate ?: missing("sampleRate")
        val resolvedChannels = channels ?: missing("channels")
        val resolvedBitDepth = bitDepth ?: missing("bitDepth")
        val pcmData = try {
            AudioSourceFlacCodec.decode(
                flacData = resolvedFlacData,
                sampleRate = resolvedSampleRate,
                channels = resolvedChannels,
                bitDepth = resolvedBitDepth,
            )
        } catch (cause: Throwable) {
            throw SerializationException("Unable to decode '$resolvedFileName' from FLAC", cause)
        }

        return AudioSource(
            id = resolvedId,
            fileName = resolvedFileName,
            rawData = pcmData,
            sampleRate = resolvedSampleRate,
            channels = resolvedChannels,
            bitDepth = resolvedBitDepth,
            stemMetadata = stemMetadata,
        )
    }

    private fun missing(fieldName: String): Nothing =
        throw SerializationException("Missing required AudioSource field '$fieldName'")
}
