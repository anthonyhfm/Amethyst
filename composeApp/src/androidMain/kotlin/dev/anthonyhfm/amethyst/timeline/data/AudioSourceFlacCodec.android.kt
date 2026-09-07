package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.nativeengine.decodeFlacPcm
import dev.anthonyhfm.amethyst.nativeengine.encodeFlacPcm

internal actual object AudioSourceFlacCodec {
    actual fun encode(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ): ByteArray = encodeFlacPcm(
        pcmData = pcmData,
        sampleRate = sampleRate.toUInt(),
        channels = channels.toUInt(),
        bitDepth = bitDepth.toUInt(),
    ).requireData("encode")

    actual fun decode(
        flacData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ): ByteArray = decodeFlacPcm(
        flacData = flacData,
        expectedSampleRate = sampleRate.toUInt(),
        expectedChannels = channels.toUInt(),
        expectedBitDepth = bitDepth.toUInt(),
    ).requireData("decode")
}

private fun dev.anthonyhfm.amethyst.nativeengine.FlacCodecResult.requireData(operation: String): ByteArray =
    data ?: error("Unable to $operation FLAC audio: ${error ?: "unknown native codec error"}")
