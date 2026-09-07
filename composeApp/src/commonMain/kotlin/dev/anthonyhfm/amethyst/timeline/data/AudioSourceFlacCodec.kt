package dev.anthonyhfm.amethyst.timeline.data

internal expect object AudioSourceFlacCodec {
    fun encode(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ): ByteArray

    fun decode(
        flacData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ): ByteArray
}
