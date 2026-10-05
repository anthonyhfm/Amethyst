package dev.anthonyhfm.amethyst.core.engine.audio.source

/** Platform acceleration for offline sample preparation; null selects the shared sinc fallback. */
internal expect fun platformResampleToPcm24(
    source: PcmAudioSource,
    outputRate: Int,
): PcmAudioSource?

/** Long iOS samples can be interpolated by the real-time Sample voice without an offline copy. */
internal expect fun useNativeRateForLongSample(sourceFrames: Long, sourceRate: Int): Boolean
