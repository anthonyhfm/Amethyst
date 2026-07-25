package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.atomicfu.atomic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Stateful, band-limited sample-rate converter for real-time voices.
 *
 * Coefficients are built during construction. [readFrame] and [advance] are
 * allocation-free and keep phase continuity across audio blocks.
 */
class PolyphaseSincResampler(
    val sourceRate: Int,
    val outputRate: Int,
    val channels: Int,
    val taps: Int = DEFAULT_TAPS,
    val phaseCount: Int = DEFAULT_PHASE_COUNT,
) {
    init {
        require(sourceRate > 0 && outputRate > 0) { "Sample rates must be positive" }
        require(channels in 1..2) { "Only mono and stereo sources are supported" }
        require(taps >= 8 && taps % 2 == 0) { "Tap count must be even and at least 8" }
        require(phaseCount >= 2) { "At least two filter phases are required" }
    }

    val isEqualRate: Boolean = sourceRate == outputRate
    val sourceFramesPerOutputFrame: Double = sourceRate.toDouble() / outputRate

    private val coefficients = if (isEqualRate) {
        FloatArray(0)
    } else {
        SharedResamplingKernels.getOrCreate(
            sourceRate = sourceRate,
            outputRate = outputRate,
            taps = taps,
            phaseCount = phaseCount,
            factory = ::buildCoefficientTable,
        )
    }

    var sourcePosition: Double = 0.0
        private set

    fun reset(sourceFrame: Double = 0.0) {
        require(sourceFrame >= 0.0 && sourceFrame.isFinite())
        sourcePosition = sourceFrame
    }

    /**
     * Reads the current source position without advancing it.
     *
     * [lowerBoundFrame] and [upperBoundFrameExclusive] provide hard clip
     * boundaries, preventing a filter kernel from leaking adjacent source PCM.
     */
    fun readFrame(
        source: AudioSource,
        destination: FloatArray,
        destinationOffset: Int = 0,
        lowerBoundFrame: Long = 0L,
        upperBoundFrameExclusive: Long = source.frameCount,
    ): Boolean {
        require(source.sampleRate == sourceRate)
        require(source.channels == channels)
        require(destinationOffset >= 0 && destinationOffset + channels <= destination.size)
        require(lowerBoundFrame in 0..source.frameCount)
        require(upperBoundFrameExclusive in lowerBoundFrame..source.frameCount)

        if (sourcePosition < lowerBoundFrame || sourcePosition >= upperBoundFrameExclusive) {
            repeat(channels) { destination[destinationOffset + it] = 0f }
            return false
        }

        if (isEqualRate) {
            val frame = sourcePosition.toLong()
            repeat(channels) { channel ->
                destination[destinationOffset + channel] =
                    if (frame in lowerBoundFrame until upperBoundFrameExclusive) {
                        source.sample(frame, channel)
                    } else {
                        0f
                    }
            }
            return true
        }

        val integralPosition = floor(sourcePosition).toLong()
        val fraction = sourcePosition - integralPosition
        val phase = (fraction * phaseCount).roundToInt().coerceIn(0, phaseCount - 1)
        val firstFrame = integralPosition - taps / 2 + 1
        val coefficientOffset = phase * taps

        repeat(channels) { channel ->
            var sum = 0.0
            var tap = 0
            while (tap < taps) {
                val frame = firstFrame + tap
                if (frame in lowerBoundFrame until upperBoundFrameExclusive) {
                    sum += source.sample(frame, channel) * coefficients[coefficientOffset + tap]
                }
                tap++
            }
            destination[destinationOffset + channel] = sum.toFloat()
        }
        return true
    }

    fun advance(outputFrames: Int = 1) {
        require(outputFrames >= 0)
        sourcePosition += sourceFramesPerOutputFrame * outputFrames
    }

    private fun buildCoefficientTable(): FloatArray {
        val result = FloatArray(phaseCount * taps)
        // Leave a small transition band to strongly suppress downsampling aliases.
        val cutoff = min(1.0, outputRate.toDouble() / sourceRate) * CUTOFF_GUARD
        val denominator = besselI0(KAISER_BETA)

        var phase = 0
        while (phase < phaseCount) {
            val fraction = phase.toDouble() / phaseCount
            var sum = 0.0
            var tap = 0
            while (tap < taps) {
                val centered = tap - taps / 2 + 1 - fraction
                val normalizedWindowPosition = (2.0 * tap / (taps - 1)) - 1.0
                val window = besselI0(
                    KAISER_BETA * sqrt((1.0 - normalizedWindowPosition * normalizedWindowPosition).coerceAtLeast(0.0))
                ) / denominator
                val coefficient = cutoff * sinc(cutoff * centered) * window
                result[phase * taps + tap] = coefficient.toFloat()
                sum += coefficient
                tap++
            }

            // Unity DC gain for every phase.
            if (abs(sum) > 1e-12) {
                tap = 0
                while (tap < taps) {
                    val index = phase * taps + tap
                    result[index] = (result[index] / sum).toFloat()
                    tap++
                }
            }
            phase++
        }
        return result
    }

    private fun sinc(value: Double): Double =
        if (abs(value) < 1e-12) 1.0 else kotlin.math.sin(PI * value) / (PI * value)

    private fun besselI0(value: Double): Double {
        // Stable power series; beta is fixed and small enough for rapid convergence.
        val quarterSquare = value * value / 4.0
        var sum = 1.0
        var term = 1.0
        var k = 1
        while (k <= 32) {
            term *= quarterSquare / (k.toDouble() * k)
            sum += term
            if (term < sum * 1e-15) break
            k++
        }
        return sum
    }

    companion object {
        const val DEFAULT_TAPS = 32
        const val DEFAULT_PHASE_COUNT = 1024

        private const val CUTOFF_GUARD = 0.94
        private const val KAISER_BETA = 8.6
    }
}

private data class ResamplingKernelKey(
    val sourceRate: Int,
    val outputRate: Int,
    val taps: Int,
    val phaseCount: Int,
)

/**
 * Coefficient tables are immutable and commonly shared by hundreds of voices
 * using the same source/output rate pair.
 */
private object SharedResamplingKernels {
    private val kernels = atomic<Map<ResamplingKernelKey, FloatArray>>(emptyMap())

    fun getOrCreate(
        sourceRate: Int,
        outputRate: Int,
        taps: Int,
        phaseCount: Int,
        factory: () -> FloatArray,
    ): FloatArray {
        val key = ResamplingKernelKey(sourceRate, outputRate, taps, phaseCount)
        kernels.value[key]?.let { return it }
        val created = factory()
        while (true) {
            val current = kernels.value
            current[key]?.let { return it }
            if (kernels.compareAndSet(current, current + (key to created))) return created
        }
    }
}
