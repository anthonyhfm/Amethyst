package dev.anthonyhfm.amethyst.core.engine.audio

import dev.anthonyhfm.amethyst.core.engine.audio.command.AudioCommandQueue
import dev.anthonyhfm.amethyst.core.engine.audio.command.AudioRenderCommand
import dev.anthonyhfm.amethyst.core.engine.audio.dsp.StereoLinkedLookaheadLimiter
import dev.anthonyhfm.amethyst.core.engine.audio.dsp.AdaptiveMixHeadroom
import dev.anthonyhfm.amethyst.core.engine.audio.voice.AudioVoice
import dev.anthonyhfm.amethyst.core.engine.audio.voice.VoiceId
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import kotlinx.atomicfu.atomic
import kotlin.math.min

/**
 * Common Float32 master renderer shared by live chains and scheduled voices.
 *
 * Prepare and command submission are control-thread operations. [render] is
 * single-consumer and uses only buffers allocated by [prepare].
 */
class AudioRenderer(
    val chain: AudioChain,
    val commandQueue: AudioCommandQueue = AudioCommandQueue(),
    val maximumVoices: Int = DEFAULT_MAXIMUM_VOICES,
    val adaptiveHeadroom: AdaptiveMixHeadroom = AdaptiveMixHeadroom(),
    val limiter: StereoLinkedLookaheadLimiter = StereoLinkedLookaheadLimiter(),
    val limiterEnabled: Boolean = true,
) {
    init {
        require(maximumVoices > 0)
    }

    private val activeVoices = arrayOfNulls<AudioVoice>(maximumVoices)
    private val pendingCommands = arrayOfNulls<AudioRenderCommand>(commandQueue.capacity)
    private var pendingCommandCount = 0
    private var scratchBlock: AudioProcessingBlock? = null
    private var renderContext: AudioRenderContext? = null
    private var preparedConfiguration: AudioConfiguration? = null
    private val renderedFrame = atomic(0L)
    private val schedulerDrops = atomic(0L)

    private var masterGain = 1f
    private var targetMasterGain = 1f
    private var masterGainStep = 0f
    private var masterRampFramesRemaining = 0

    val configuration: AudioConfiguration?
        get() = preparedConfiguration

    val absoluteFrame: Long
        get() = renderedFrame.value

    val latencyFrames: Int
        get() = if (limiterEnabled) limiter.lookaheadFrames else 0

    val activeVoiceCount: Int
        get() {
            var count = 0
            var index = 0
            while (index < activeVoices.size) {
                if (activeVoices[index] != null) count++
                index++
            }
            return count
        }

    val droppedSchedulingCommandCount: Long
        get() = schedulerDrops.value

    fun prepare(
        configuration: AudioConfiguration,
        initialAbsoluteFrame: Long = 0L,
    ) {
        require(configuration.sampleRate > 0)
        require(configuration.channels == 2) { "The AudioRenderer master bus is stereo" }
        require(configuration.periodFrames > 0)
        require(configuration.maximumBlockFrames >= configuration.periodFrames)
        require(initialAbsoluteFrame >= 0L)

        preparedConfiguration = configuration
        scratchBlock = AudioProcessingBlock(
            samples = FloatArray(configuration.maximumBlockFrames * configuration.channels),
            channels = configuration.channels,
            maximumFrames = configuration.maximumBlockFrames,
        )
        renderContext = AudioRenderContext(
            sampleRate = configuration.sampleRate,
            absoluteFrame = initialAbsoluteFrame,
            transportFrame = initialAbsoluteFrame,
        )
        limiter.prepare(configuration.sampleRate)
        adaptiveHeadroom.prepare(configuration.sampleRate)
        chain.prepareAudio(configuration)
        clearVoices()
        commandQueue.clear()
        clearPendingCommands()
        renderedFrame.value = initialAbsoluteFrame
        masterGain = 1f
        targetMasterGain = 1f
        masterGainStep = 0f
        masterRampFramesRemaining = 0
    }

    /**
     * Prepares StartVoice commands before publishing them to the real-time ring.
     */
    fun enqueue(command: AudioRenderCommand): Boolean {
        val configuration = preparedConfiguration
            ?: error("AudioRenderer must be prepared before commands are submitted")
        if (command is AudioRenderCommand.StartVoice) {
            command.voice.prepare(configuration)
        }
        return commandQueue.offer(command)
    }

    fun requestEmergencyStop() {
        commandQueue.requestEmergencyStop()
    }

    /**
     * Renders [frameCount] interleaved stereo frames into [output].
     */
    fun render(
        output: FloatArray,
        frameCount: Int,
        transportFrame: Long = absoluteFrame,
    ) {
        val configuration = preparedConfiguration
            ?: error("AudioRenderer must be prepared before rendering")
        require(frameCount in 0..configuration.maximumBlockFrames)
        require(output.size >= frameCount * configuration.channels)
        require(transportFrame >= 0L)

        val blockStartFrame = renderedFrame.value
        if (commandQueue.consumeEmergencyStop()) {
            stopAllVoices(fadeOutFrames = 0)
        }
        drainCommandQueue()

        var renderedInBlock = 0
        while (renderedInBlock < frameCount) {
            val segmentAbsoluteFrame = blockStartFrame + renderedInBlock
            applyCommandsAtOrBefore(segmentAbsoluteFrame)

            val nextCommandFrame = peekPendingCommand()?.targetFrame ?: Long.MAX_VALUE
            val framesUntilCommand = (nextCommandFrame - segmentAbsoluteFrame)
                .coerceAtLeast(0L)
                .coerceAtMost((frameCount - renderedInBlock).toLong())
                .toInt()
            val segmentFrames = if (framesUntilCommand == 0) {
                // A newly due command was added while applying another command.
                applyCommandsAtOrBefore(segmentAbsoluteFrame)
                min(frameCount - renderedInBlock, configuration.maximumBlockFrames)
            } else {
                framesUntilCommand
            }

            renderSegment(
                destination = output,
                destinationFrameOffset = renderedInBlock,
                frameCount = segmentFrames,
                absoluteFrame = segmentAbsoluteFrame,
                transportFrame = transportFrame + renderedInBlock,
            )
            renderedInBlock += segmentFrames
        }

        // Commands exactly at the end belong to the next block, preserving a
        // single unambiguous application boundary.
        renderedFrame.value = blockStartFrame + frameCount
    }

    fun render(
        block: AudioProcessingBlock,
        transportFrame: Long = block.frameOffset,
    ) {
        render(block.samples, block.frameCount, transportFrame)
    }

    /**
     * Lifecycle-only reset. Command producers must be quiescent.
     */
    fun reset(absoluteFrame: Long = 0L) {
        require(absoluteFrame >= 0L)
        commandQueue.clear()
        clearPendingCommands()
        clearVoices()
        chain.resetAudio()
        limiter.reset()
        adaptiveHeadroom.reset()
        renderedFrame.value = absoluteFrame
        masterGain = 1f
        targetMasterGain = 1f
        masterGainStep = 0f
        masterRampFramesRemaining = 0
    }

    fun release() {
        reset()
        chain.releaseAudio()
        preparedConfiguration = null
        scratchBlock = null
        renderContext = null
    }

    private fun renderSegment(
        destination: FloatArray,
        destinationFrameOffset: Int,
        frameCount: Int,
        absoluteFrame: Long,
        transportFrame: Long,
    ) {
        if (frameCount <= 0) return
        val block = checkNotNull(scratchBlock)
        val configuration = checkNotNull(preparedConfiguration)
        block.configure(frameCount, absoluteFrame)
        block.clear()
        val context = checkNotNull(renderContext)
        context.configure(
            sampleRate = configuration.sampleRate,
            absoluteFrame = absoluteFrame,
            transportFrame = transportFrame,
        )

        chain.processAudio(block, context)
        renderVoices(block, context)
        adaptiveHeadroom.processInterleaved(
            samples = block.samples,
            frameCount = frameCount,
            contributionEnergy = context.mixContributionEnergy,
        )
        applyMasterGain(block.samples, frameCount)
        if (limiterEnabled) {
            limiter.processInterleaved(block.samples, frameCount)
        }
        block.samples.copyInto(
            destination = destination,
            destinationOffset = destinationFrameOffset * configuration.channels,
            startIndex = 0,
            endIndex = frameCount * configuration.channels,
        )
    }

    private fun renderVoices(
        block: AudioProcessingBlock,
        context: AudioRenderContext,
    ) {
        var index = 0
        while (index < activeVoices.size) {
            val voice = activeVoices[index]
            if (voice != null) {
                voice.render(block, context)
                if (voice.isFinished) {
                    activeVoices[index] = null
                }
            }
            index++
        }
    }

    private fun applyMasterGain(samples: FloatArray, frameCount: Int) {
        var frame = 0
        while (frame < frameCount) {
            if (masterRampFramesRemaining > 0) {
                masterGain += masterGainStep
                masterRampFramesRemaining--
                if (masterRampFramesRemaining == 0) masterGain = targetMasterGain
            }
            val index = frame * 2
            samples[index] *= masterGain
            samples[index + 1] *= masterGain
            frame++
        }
    }

    private fun drainCommandQueue() {
        while (pendingCommandCount < pendingCommands.size) {
            val command = commandQueue.poll() ?: break
            pushPendingCommand(command)
        }
    }

    private fun pushPendingCommand(command: AudioRenderCommand) {
        if (pendingCommandCount >= pendingCommands.size) {
            schedulerDrops.incrementAndGet()
            return
        }

        var index = pendingCommandCount
        pendingCommandCount++
        while (index > 0) {
            val parent = (index - 1) / 2
            val parentCommand = pendingCommands[parent] ?: break
            if (parentCommand.targetFrame <= command.targetFrame) break
            pendingCommands[index] = parentCommand
            index = parent
        }
        pendingCommands[index] = command
    }

    private fun peekPendingCommand(): AudioRenderCommand? =
        if (pendingCommandCount == 0) null else pendingCommands[0]

    private fun popPendingCommand(): AudioRenderCommand? {
        if (pendingCommandCount == 0) return null
        val result = pendingCommands[0]
        pendingCommandCount--
        val replacement = pendingCommands[pendingCommandCount]
        pendingCommands[pendingCommandCount] = null
        if (pendingCommandCount == 0 || replacement == null) return result

        var index = 0
        while (true) {
            val left = index * 2 + 1
            if (left >= pendingCommandCount) break
            val right = left + 1
            var child = left
            if (
                right < pendingCommandCount &&
                checkNotNull(pendingCommands[right]).targetFrame <
                checkNotNull(pendingCommands[left]).targetFrame
            ) {
                child = right
            }
            val childCommand = checkNotNull(pendingCommands[child])
            if (replacement.targetFrame <= childCommand.targetFrame) break
            pendingCommands[index] = childCommand
            index = child
        }
        pendingCommands[index] = replacement
        return result
    }

    private fun applyCommandsAtOrBefore(frame: Long) {
        while (true) {
            val command = peekPendingCommand() ?: return
            if (command.targetFrame > frame) return
            popPendingCommand()
            applyCommand(command)
        }
    }

    private fun applyCommand(command: AudioRenderCommand) {
        when (command) {
            is AudioRenderCommand.StartVoice -> startVoice(command.voice)
            is AudioRenderCommand.StopVoice ->
                findVoice(command.voiceId)?.requestStop(command.fadeOutFrames)

            is AudioRenderCommand.UpdateVoiceMix ->
                findVoice(command.voiceId)?.updateMix(
                    gain = command.gain,
                    pan = command.pan,
                    rampFrames = command.rampFrames,
                )

            is AudioRenderCommand.StopAll -> stopAllVoices(command.fadeOutFrames)
            is AudioRenderCommand.ResetAll -> {
                stopAllVoices(fadeOutFrames = 0)
                chain.resetAudio()
                limiter.reset()
            }
            is AudioRenderCommand.SetMasterGain -> {
                require(command.gain.isFinite() && command.gain >= 0f)
                if (command.rampFrames <= 0) {
                    masterGain = command.gain
                    targetMasterGain = command.gain
                    masterGainStep = 0f
                    masterRampFramesRemaining = 0
                } else {
                    targetMasterGain = command.gain
                    masterRampFramesRemaining = command.rampFrames
                    masterGainStep = (targetMasterGain - masterGain) / command.rampFrames
                }
            }
        }
    }

    private fun startVoice(voice: AudioVoice) {
        var freeIndex = -1
        var oldestIndex = 0
        var oldestStartFrame = Long.MAX_VALUE
        var index = 0
        while (index < activeVoices.size) {
            val existing = activeVoices[index]
            if (existing == null) {
                freeIndex = index
                break
            }
            if (existing.id == voice.id) {
                existing.requestStop(0)
                freeIndex = index
                break
            }
            if (existing.startFrame < oldestStartFrame) {
                oldestStartFrame = existing.startFrame
                oldestIndex = index
            }
            index++
        }
        activeVoices[if (freeIndex >= 0) freeIndex else oldestIndex] = voice
    }

    private fun findVoice(id: VoiceId): AudioVoice? {
        var index = 0
        while (index < activeVoices.size) {
            val voice = activeVoices[index]
            if (voice?.id == id) return voice
            index++
        }
        return null
    }

    private fun stopAllVoices(fadeOutFrames: Int) {
        var index = 0
        while (index < activeVoices.size) {
            activeVoices[index]?.requestStop(fadeOutFrames)
            if (fadeOutFrames <= 0) activeVoices[index] = null
            index++
        }
    }

    private fun clearVoices() {
        var index = 0
        while (index < activeVoices.size) {
            activeVoices[index]?.reset()
            activeVoices[index] = null
            index++
        }
    }

    private fun clearPendingCommands() {
        var index = 0
        while (index < pendingCommandCount) {
            pendingCommands[index] = null
            index++
        }
        pendingCommandCount = 0
    }

    companion object {
        const val DEFAULT_MAXIMUM_VOICES = 64
    }
}
