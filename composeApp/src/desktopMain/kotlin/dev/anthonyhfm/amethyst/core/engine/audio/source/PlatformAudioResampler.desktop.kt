package dev.anthonyhfm.amethyst.core.engine.audio.source

internal actual fun platformResampleToPcm24(
    source: PcmAudioSource,
    outputRate: Int,
): PcmAudioSource? = null

internal actual fun useNativeRateForLongSample(sourceFrames: Long, sourceRate: Int): Boolean = false
