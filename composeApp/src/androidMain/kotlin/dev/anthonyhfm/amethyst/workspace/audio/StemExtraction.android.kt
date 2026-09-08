package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.timeline.data.AudioSource

actual fun createStemExtractionPlatformBackend(): StemExtractionPlatformBackend = UnsupportedStemBackend

private object UnsupportedStemBackend : StemExtractionPlatformBackend {
    override val isAvailable: Boolean = false
    override val canInstallCuda: Boolean = false
    override val isCudaReady: Boolean = false
    override fun isModelReady(modelId: String): Boolean = false

    override suspend fun extract(
        source: AudioSource,
        modelId: String,
        forceCpu: Boolean,
        installCuda: Boolean,
        onProgress: (StemExtractionProgress) -> Unit,
    ): List<ExtractedStem> = error("Stem extraction is only available on desktop")
}
