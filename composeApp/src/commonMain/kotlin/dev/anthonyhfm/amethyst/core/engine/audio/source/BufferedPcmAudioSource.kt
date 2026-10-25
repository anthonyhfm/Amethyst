package dev.anthonyhfm.amethyst.core.engine.audio.source

internal class BufferedPcmAudioSource(private val source: PcmAudioSource) : AudioSource {
    override val id: String get() = source.id
    override val sampleRate: Int get() = source.sampleRate
    override val channels: Int get() = source.channels
    override val frameCount: Long get() = source.frameCount

    internal val samples = FloatArray(size = WINDOW_FRAMES * source.channels)
    internal var firstCachedFrame = 0L
        private set
    private var cachedFrames = 0

    override fun sample(frameIndex: Long, channel: Int): Float {
        if (frameIndex !in 0 until frameCount || channel !in 0 until channels) {
            return 0f
        }
        if (frameIndex < firstCachedFrame || frameIndex >= firstCachedFrame + cachedFrames) {
            loadWindow(frameIndex = frameIndex)
        }
        return samples[(frameIndex - firstCachedFrame).toInt() * channels + channel]
    }

    internal fun prepareWindow(firstFrame: Long, lastFrame: Long) {
        val first = firstFrame.coerceAtLeast(minimumValue = 0L)
        val last = lastFrame.coerceAtMost(maximumValue = frameCount - 1L)
        if (first < firstCachedFrame || last >= firstCachedFrame + cachedFrames) {
            loadWindow(frameIndex = first)
        }
    }

    private fun loadWindow(frameIndex: Long) {
        val start = (frameIndex - PolyphaseSincResampler.DEFAULT_TAPS).coerceAtLeast(minimumValue = 0L)
        val count = minOf(WINDOW_FRAMES.toLong(), frameCount - start).toInt()
        val bytes = source.pcmBytes.readRange(
            fromIndex = (start * source.bytesPerFrame).toInt(),
            toIndex = ((start + count) * source.bytesPerFrame).toInt(),
        )
        val window = ByteArrayPcmAudioSource(
            id = source.id,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            rawData = bytes,
            floatingPoint = source.floatingPoint,
        )
        var frame = 0
        while (frame < count) {
            var channel = 0
            while (channel < channels) {
                samples[frame * channels + channel] = window.sample(frameIndex = frame.toLong(), channel = channel)
                channel++
            }
            frame++
        }
        firstCachedFrame = start
        cachedFrames = count
    }

    private companion object {
        const val WINDOW_FRAMES = 8192
    }
}
