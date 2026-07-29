package dev.anthonyhfm.amethyst.core.engine.audio.voice

import dev.anthonyhfm.amethyst.core.engine.audio.source.AudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PolyphaseSincResampler
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext

data class VoiceId(val value: Long)

/**
 * A preallocated renderer mixed alongside the AudioChain generators.
 *
 * [prepare] is called on the command-producing thread before the voice enters
 * the real-time queue. All remaining methods must be allocation-free.
 */
interface AudioVoice {
    val id: VoiceId
    val startFrame: Long
    val isFinished: Boolean

    fun prepare(configuration: AudioConfiguration)
    fun render(block: AudioProcessingBlock, context: AudioRenderContext)
    fun updateMix(gain: Float, pan: Float, rampFrames: Int)
    fun requestStop(fadeOutFrames: Int)
    fun reset()
}

/**
 * Streaming PCM voice with high-quality resampling and hard clip boundaries.
 */
class PcmAudioVoice(
    override val id: VoiceId,
    val source: AudioSource,
    override val startFrame: Long,
    val sourceStartFrame: Long = 0L,
    val sourceEndFrameExclusive: Long = source.frameCount,
    gain: Float = 1f,
    pan: Float = 0f,
) : AudioVoice {
    private val initialGain = gain
    private val initialPan = pan

    init {
        require(startFrame >= 0L)
        require(sourceStartFrame in 0..source.frameCount)
        require(sourceEndFrameExclusive in sourceStartFrame..source.frameCount)
        require(gain.isFinite() && gain >= 0f)
        require(pan.isFinite() && pan in -1f..1f)
    }

    val gain: Float get() = targetGain
    val pan: Float get() = targetPan

    override var isFinished: Boolean = sourceStartFrame >= sourceEndFrameExclusive
        private set

    private var configuration: AudioConfiguration? = null
    private var resampler: PolyphaseSincResampler? = null
    private val sourceFrame = FloatArray(source.channels)
    private var stopFramesRemaining = Int.MAX_VALUE
    private var stopFadeLength = Int.MAX_VALUE
    private var currentGain = initialGain
    private var targetGain = initialGain
    private var gainStep = 0f
    private var currentPan = initialPan
    private var targetPan = initialPan
    private var panStep = 0f
    private var mixRampFramesRemaining = 0

    override fun prepare(configuration: AudioConfiguration) {
        require(configuration.channels == 2) { "PcmAudioVoice currently renders to stereo" }
        if (this.configuration == configuration && resampler != null) return
        this.configuration = configuration
        resampler = PolyphaseSincResampler(
            sourceRate = source.sampleRate,
            outputRate = configuration.sampleRate,
            channels = source.channels,
        ).also { it.reset(sourceStartFrame.toDouble()) }
        stopFramesRemaining = Int.MAX_VALUE
        stopFadeLength = Int.MAX_VALUE
        isFinished = sourceStartFrame >= sourceEndFrameExclusive
    }

    override fun render(block: AudioProcessingBlock, context: AudioRenderContext) {
        val activeResampler = resampler ?: return
        if (isFinished) return

        var outputFrame = 0
        if (context.absoluteFrame < startFrame) {
            outputFrame = (startFrame - context.absoluteFrame)
                .coerceAtMost(block.frameCount.toLong())
                .toInt()
        }

        while (outputFrame < block.frameCount && !isFinished) {
            val hasFrame = activeResampler.readFrame(
                source = source,
                destination = sourceFrame,
                lowerBoundFrame = sourceStartFrame,
                upperBoundFrameExclusive = sourceEndFrameExclusive,
            )
            if (!hasFrame) {
                isFinished = true
                break
            }

            val fadeGain = if (stopFramesRemaining == Int.MAX_VALUE) {
                1f
            } else {
                stopFramesRemaining.toFloat() / stopFadeLength.coerceAtLeast(1)
            }
            if (mixRampFramesRemaining > 0) {
                currentGain += gainStep
                currentPan += panStep
                mixRampFramesRemaining--
                if (mixRampFramesRemaining == 0) {
                    currentGain = targetGain
                    currentPan = targetPan
                }
            }
            val leftPanGain = if (currentPan > 0f) 1f - currentPan else 1f
            val rightPanGain = if (currentPan < 0f) 1f + currentPan else 1f
            val outputIndex = outputFrame * 2
            if (source.channels == 1) {
                val sample = sourceFrame[0] * currentGain * fadeGain
                block.samples[outputIndex] += sample * leftPanGain
                block.samples[outputIndex + 1] += sample * rightPanGain
            } else {
                block.samples[outputIndex] += sourceFrame[0] * currentGain * fadeGain * leftPanGain
                block.samples[outputIndex + 1] += sourceFrame[1] * currentGain * fadeGain * rightPanGain
            }

            activeResampler.advance()
            if (stopFramesRemaining != Int.MAX_VALUE) {
                stopFramesRemaining--
                if (stopFramesRemaining <= 0) {
                    isFinished = true
                }
            }
            outputFrame++
        }
    }

    override fun updateMix(gain: Float, pan: Float, rampFrames: Int) {
        require(gain.isFinite() && gain >= 0f)
        require(pan.isFinite() && pan in -1f..1f)
        if (rampFrames <= 0) {
            currentGain = gain
            targetGain = gain
            gainStep = 0f
            currentPan = pan
            targetPan = pan
            panStep = 0f
            mixRampFramesRemaining = 0
            return
        }
        targetGain = gain
        targetPan = pan
        gainStep = (targetGain - currentGain) / rampFrames
        panStep = (targetPan - currentPan) / rampFrames
        mixRampFramesRemaining = rampFrames
    }

    override fun requestStop(fadeOutFrames: Int) {
        if (isFinished) return
        if (fadeOutFrames <= 0) {
            isFinished = true
            return
        }
        if (stopFramesRemaining == Int.MAX_VALUE || fadeOutFrames < stopFramesRemaining) {
            stopFramesRemaining = fadeOutFrames
            stopFadeLength = fadeOutFrames
        }
    }

    override fun reset() {
        resampler?.reset(sourceStartFrame.toDouble())
        stopFramesRemaining = Int.MAX_VALUE
        stopFadeLength = Int.MAX_VALUE
        currentGain = targetGain
        currentPan = targetPan
        gainStep = 0f
        panStep = 0f
        mixRampFramesRemaining = 0
        isFinished = sourceStartFrame >= sourceEndFrameExclusive
    }
}
