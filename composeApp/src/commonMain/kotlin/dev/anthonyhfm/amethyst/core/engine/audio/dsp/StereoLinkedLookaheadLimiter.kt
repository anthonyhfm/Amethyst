package dev.anthonyhfm.amethyst.core.engine.audio.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Stereo-linked brick-wall safety limiter with an exact lookahead delay.
 *
 * A monotonic peak deque supplies the maximum over the complete lookahead
 * window. Gain reduction attacks immediately and releases exponentially.
 */
class StereoLinkedLookaheadLimiter(
    val lookaheadMillis: Double = DEFAULT_LOOKAHEAD_MILLIS,
    val ceilingDb: Float = DEFAULT_CEILING_DB,
    val releaseMillis: Double = DEFAULT_RELEASE_MILLIS,
) {
    init {
        require(lookaheadMillis >= 0.0)
        require(ceilingDb <= 0f && ceilingDb.isFinite())
        require(releaseMillis > 0.0)
    }

    var lookaheadFrames: Int = 0
        private set

    val ceilingLinear: Float = 10.0.pow(ceilingDb / 20.0).toFloat()

    var currentGain: Float = 1f
        private set

    private var releaseCoefficient = 0.0
    private var delayLeft = FloatArray(1)
    private var delayRight = FloatArray(1)
    private var delayCursor = 0
    private var peakValues = FloatArray(2)
    private var peakIndices = LongArray(2)
    private var peakHead = 0
    private var peakSize = 0
    private var inputFrameIndex = 0L

    fun prepare(sampleRate: Int) {
        require(sampleRate > 0)
        lookaheadFrames = ceil(sampleRate * lookaheadMillis / 1000.0).toInt()
        val delaySize = lookaheadFrames.coerceAtLeast(1)
        delayLeft = FloatArray(delaySize)
        delayRight = FloatArray(delaySize)
        peakValues = FloatArray(lookaheadFrames + 2)
        peakIndices = LongArray(lookaheadFrames + 2)
        releaseCoefficient = exp(-1.0 / (sampleRate * releaseMillis / 1000.0))
        reset()
    }

    fun reset() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayCursor = 0
        peakHead = 0
        peakSize = 0
        inputFrameIndex = 0L
        currentGain = 1f
    }

    fun processInterleaved(samples: FloatArray, frameCount: Int) {
        require(frameCount >= 0 && frameCount * 2 <= samples.size)
        var frame = 0
        while (frame < frameCount) {
            val sampleIndex = frame * 2
            val inputLeft = samples[sampleIndex]
            val inputRight = samples[sampleIndex + 1]
            val peak = max(abs(inputLeft), abs(inputRight))

            discardExpiredPeaks(inputFrameIndex - lookaheadFrames)
            appendPeak(inputFrameIndex, peak)
            val windowPeak = if (peakSize == 0) 0f else peakValues[peakHead]
            val requiredGain = if (windowPeak > ceilingLinear) {
                ceilingLinear / windowPeak
            } else {
                1f
            }

            currentGain = if (requiredGain < currentGain) {
                requiredGain
            } else {
                (requiredGain + releaseCoefficient * (currentGain - requiredGain)).toFloat()
            }

            val delayedLeft: Float
            val delayedRight: Float
            if (lookaheadFrames == 0) {
                delayedLeft = inputLeft
                delayedRight = inputRight
            } else {
                delayedLeft = delayLeft[delayCursor]
                delayedRight = delayRight[delayCursor]
                delayLeft[delayCursor] = inputLeft
                delayRight[delayCursor] = inputRight
                delayCursor++
                if (delayCursor == delayLeft.size) delayCursor = 0
            }

            samples[sampleIndex] = delayedLeft * currentGain
            samples[sampleIndex + 1] = delayedRight * currentGain
            inputFrameIndex++
            frame++
        }
    }

    private fun discardExpiredPeaks(firstAllowedIndex: Long) {
        while (peakSize > 0 && peakIndices[peakHead] < firstAllowedIndex) {
            peakHead = (peakHead + 1) % peakValues.size
            peakSize--
        }
    }

    private fun appendPeak(index: Long, value: Float) {
        while (peakSize > 0) {
            val last = (peakHead + peakSize - 1) % peakValues.size
            if (peakValues[last] > value) break
            peakSize--
        }
        val destination = (peakHead + peakSize) % peakValues.size
        peakIndices[destination] = index
        peakValues[destination] = value
        peakSize++
    }

    companion object {
        const val DEFAULT_LOOKAHEAD_MILLIS = 1.0
        const val DEFAULT_CEILING_DB = 0f
        const val DEFAULT_RELEASE_MILLIS = 50.0
    }
}
