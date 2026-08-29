package dev.anthonyhfm.amethyst.devices.audio.sample

import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PolyphaseSincResampler
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.PadTriggerKey
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import kotlinx.atomicfu.AtomicLongArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class SampleRenderSnapshot private constructor(
    val source: ByteArrayPcmAudioSource,
    val startFrame: Long,
    val endFrame: Long,
    val loopStartFrame: Long,
    val loopEndFrame: Long,
    val playbackMode: SamplePlaybackMode,
    val fadeInFrames: Int,
    val fadeOutFrames: Int,
    val volumeGain: Float,
    val panLeftGain: Float,
    val panRightGain: Float,
    val volumeAutomationLane: TimelineAutomationLane?,
    val warpMode: SampleWarpMode,
    val tempoRatio: Double,
) {
    val activeFrames: Long get() = endFrame - startFrame

    companion object {
        fun prepareSource(
            state: SampleChainDeviceState,
            outputSampleRate: Int,
            rawData: ByteArray? = state.resolvedRawData(),
        ): ByteArrayPcmAudioSource? {
            rawData ?: return null
            val bytesPerSample = state.bitDepth / 8
            val bytesPerFrame = bytesPerSample * state.channels
            if (
                rawData.isEmpty() || state.sampleRate <= 0 || state.channels !in 1..2 ||
                state.bitDepth !in ByteArrayPcmAudioSource.SUPPORTED_BIT_DEPTHS ||
                bytesPerFrame <= 0 || rawData.size % bytesPerFrame != 0
            ) return null

            val original = ByteArrayPcmAudioSource(
                id = state.fileName.ifBlank { "sample" },
                sampleRate = state.sampleRate,
                channels = state.channels,
                bitDepth = state.bitDepth,
                rawData = rawData,
            )
            if (state.sampleRate == outputSampleRate) return original

            val outputFrames = (
                original.frameCount.toDouble() * outputSampleRate / original.sampleRate
            ).toLong().coerceAtLeast(1L)
            require(outputFrames <= Int.MAX_VALUE / (state.channels * 3)) {
                "Prepared sample is too large"
            }
            val preparedBytes = ByteArray(outputFrames.toInt() * state.channels * 3)
            val frame = FloatArray(state.channels)
            val resampler = PolyphaseSincResampler(
                sourceRate = original.sampleRate,
                outputRate = outputSampleRate,
                channels = original.channels,
            )
            var outputFrame = 0
            while (outputFrame < outputFrames.toInt()) {
                resampler.readFrame(
                    source = original,
                    destination = frame,
                    lowerBoundFrame = 0L,
                    upperBoundFrameExclusive = original.frameCount,
                )
                var channel = 0
                while (channel < state.channels) {
                    writePcm24(preparedBytes, outputFrame * state.channels + channel, frame[channel])
                    channel++
                }
                resampler.advance()
                outputFrame++
            }
            return ByteArrayPcmAudioSource(
                id = original.id,
                sampleRate = outputSampleRate,
                channels = original.channels,
                bitDepth = 24,
                rawData = preparedBytes,
            )
        }

        fun from(
            state: SampleChainDeviceState,
            source: ByteArrayPcmAudioSource,
            workspaceBpm: Double = state.sourceBpm?.toDouble() ?: 120.0,
        ): SampleRenderSnapshot? {
            val startFrame = (source.frameCount * state.startPosition).toLong()
                .coerceIn(0, source.frameCount)
            val endFrame = (source.frameCount * state.endPosition).toLong()
                .coerceIn(startFrame, source.frameCount)
            if (endFrame <= startFrame) return null

            val requestedLoopStart = state.loopStartPosition ?: state.startPosition
            val requestedLoopEnd = state.loopEndPosition ?: state.endPosition
            val loopStart = (source.frameCount * requestedLoopStart).toLong()
                .coerceIn(startFrame, endFrame - 1)
            val loopEnd = (source.frameCount * requestedLoopEnd).toLong()
                .coerceIn(loopStart + 1, endFrame)
            val activeFrames = endFrame - startFrame
            val volumeDb = state.volumeDb.takeIf(Float::isFinite) ?: 0f
            val pan = ((state.pan.takeIf(Float::isFinite) ?: 0f) / 100f).coerceIn(-1f, 1f)
            val angle = (pan + 1f) * (PI.toFloat() / 4f)
            val centerCompensation = sqrt(2f)

            return SampleRenderSnapshot(
                source = source,
                startFrame = startFrame,
                endFrame = endFrame,
                loopStartFrame = loopStart,
                loopEndFrame = loopEnd,
                playbackMode = state.playbackMode,
                fadeInFrames = ((state.fadeInMs / 1_000f) * source.sampleRate).toInt()
                    .coerceIn(0, activeFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                fadeOutFrames = ((state.fadeOutMs / 1_000f) * source.sampleRate).toInt()
                    .coerceIn(0, activeFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                volumeGain = 10.0.pow(volumeDb / 20.0).toFloat(),
                panLeftGain = cos(angle) * centerCompensation,
                panRightGain = sin(angle) * centerCompensation,
                volumeAutomationLane = state.volumeAutomationLane?.normalized()?.takeIf {
                    it.enabled && it.target == TimelineTrackAutomationTarget.VOLUME
                },
                warpMode = state.warpMode,
                tempoRatio = sampleTempoRatio(state.warpMode, state.sourceBpm, workspaceBpm),
            )
        }

        private fun writePcm24(destination: ByteArray, sampleIndex: Int, sample: Float) {
            val normalized = sample.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
            val value = if (normalized <= -1f) -8_388_608 else (normalized * 8_388_607f).toInt()
            val offset = sampleIndex * 3
            destination[offset] = (value and 0xff).toByte()
            destination[offset + 1] = ((value ushr 8) and 0xff).toByte()
            destination[offset + 2] = ((value ushr 16) and 0xff).toByte()
        }
    }
}

internal sealed interface SampleVoiceCommand {
    val targetFrame: Long

    data class Start(
        override val targetFrame: Long,
        val key: PadTriggerKey,
        val snapshot: SampleRenderSnapshot,
    ) : SampleVoiceCommand

    data class Release(
        override val targetFrame: Long,
        val key: PadTriggerKey,
        val fadeFrames: Int,
    ) : SampleVoiceCommand

    data class Choke(
        override val targetFrame: Long,
        val fadeFrames: Int,
    ) : SampleVoiceCommand
}

/** Preallocated block-rate modulation shared by all active sample voices. */
internal class SampleVoiceModulationBuffer(maximumFrames: Int) {
    val volumeGain = FloatArray(maximumFrames) { 1f }
    val panLeftGain = FloatArray(maximumFrames) { 1f }
    val panRightGain = FloatArray(maximumFrames) { 1f }
    val fadeInFrames = IntArray(maximumFrames)
    val fadeOutFrames = IntArray(maximumFrames)
}

internal class SampleTriggerQueue(private val capacity: Int = 32) {
    private val slots = atomicArrayOfNulls<SampleVoiceCommand>(capacity)
    private val sequences = AtomicLongArray(capacity)
    private val writeSequence = atomic(0L)
    private val readSequence = atomic(0L)
    private val droppedCount = atomic(0L)

    init {
        require(capacity > 0)
        var index = 0
        while (index < capacity) {
            sequences[index].value = index.toLong()
            index++
        }
    }

    val droppedTriggers: Long get() = droppedCount.value

    fun offer(command: SampleVoiceCommand): Boolean {
        while (true) {
            val write = writeSequence.value
            val slot = (write % capacity).toInt()
            val difference = sequences[slot].value - write
            when {
                difference == 0L -> if (writeSequence.compareAndSet(write, write + 1L)) {
                    slots[slot].value = command
                    sequences[slot].value = write + 1L
                    return true
                }
                difference < 0L -> {
                    droppedCount.incrementAndGet()
                    return false
                }
            }
        }
    }

    fun poll(): SampleVoiceCommand? {
        val read = readSequence.value
        val slot = (read % capacity).toInt()
        if (sequences[slot].value - (read + 1L) != 0L) return null
        val command = slots[slot].getAndSet(null)
        sequences[slot].value = read + capacity
        readSequence.value = read + 1L
        return command
    }

    fun clear() {
        while (poll() != null) {
            // Drain without retaining stale commands.
        }
    }
}

internal class SampleVoiceRenderer {
    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var snapshot: SampleRenderSnapshot? = null
    private var key: PadTriggerKey? = null
    private var sourcePosition = 0.0
    private val sourceFrameBuffer = FloatArray(2)
    private var transitionFramesRemaining = 0
    private var transitionFramesTotal = 0
    private var transitionLeft = 0f
    private var transitionRight = 0f
    private var releaseFramesRemaining = 0
    private var releaseFramesTotal = 0
    private var lastLeft = 0f
    private var lastRight = 0f
    private var tempoRatio = 1.0
    private var targetTempoRatio = 1.0
    private var warpLatencyRemaining = 0
    private var warpOutputFrames = 0L
    private var warpConsumedSourceFrames = 0.0
    private var warpGrainPhase = 0
    private var warpCurrentGrainStart = 0.0
    private var warpPreviousGrainStart = 0.0
    private var warpPreviousGrainValid = false

    var startSequence: Long = -1L
        private set
    val isActive: Boolean get() = snapshot != null
    val sourceFrame: Long get() = snapshot?.let {
        floor(sourcePosition).toLong().coerceIn(it.startFrame, it.endFrame)
    } ?: -1L

    fun prepare(configuration: AudioConfiguration) {
        this.configuration = configuration
        stopImmediately()
    }

    fun trigger(snapshot: SampleRenderSnapshot, key: PadTriggerKey, sequence: Long) {
        check(snapshot.source.sampleRate == configuration.sampleRate)
        transitionLeft = lastLeft
        transitionRight = lastRight
        transitionFramesTotal = (configuration.sampleRate * RETRIGGER_TRANSITION_SECONDS)
            .toInt().coerceAtLeast(1)
        transitionFramesRemaining = transitionFramesTotal
        releaseFramesRemaining = 0
        releaseFramesTotal = 0
        this.snapshot = snapshot
        this.key = key
        startSequence = sequence
        sourcePosition = snapshot.startFrame.toDouble()
        tempoRatio = snapshot.tempoRatio
        targetTempoRatio = snapshot.tempoRatio
        warpLatencyRemaining = if (snapshot.warpMode == SampleWarpMode.Warp) WARP_LATENCY_FRAMES else 0
        warpOutputFrames = 0L
        warpConsumedSourceFrames = 0.0
        warpGrainPhase = 0
        warpCurrentGrainStart = snapshot.startFrame.toDouble()
        warpPreviousGrainStart = warpCurrentGrainStart
        warpPreviousGrainValid = false
    }

    fun updateTempoRatio(ratio: Double) {
        targetTempoRatio = ratio.coerceIn(MINIMUM_TEMPO_RATIO, MAXIMUM_TEMPO_RATIO)
    }

    fun requestRelease(key: PadTriggerKey, fadeFrames: Int) {
        val active = snapshot ?: return
        if (active.playbackMode == SamplePlaybackMode.GateLoop && this.key == key) {
            requestStop(fadeFrames)
        }
    }

    fun requestStop(fadeFrames: Int) {
        if (!isActive) return
        if (fadeFrames <= 0) {
            stopImmediately()
        } else if (releaseFramesRemaining == 0 || fadeFrames < releaseFramesRemaining) {
            releaseFramesTotal = fadeFrames
            releaseFramesRemaining = fadeFrames
        }
    }

    fun render(
        block: AudioProcessingBlock,
        destinationFrameOffset: Int,
        frameCount: Int,
        modulation: SampleVoiceModulationBuffer? = null,
    ) {
        var rendered = 0
        while (rendered < frameCount) {
            val active = snapshot ?: break
            rampTempoRatio()
            if (active.warpMode != SampleWarpMode.Warp && sourcePosition >= playbackBoundary(active)) {
                if (active.playbackMode == SamplePlaybackMode.GateLoop && releaseFramesRemaining == 0) {
                    val overshoot = sourcePosition - active.loopEndFrame
                    sourcePosition = active.loopStartFrame + overshoot
                } else {
                    stopImmediately()
                    break
                }
            }

            if (active.warpMode == SampleWarpMode.Warp && warpFinished(active)) {
                if (active.playbackMode == SamplePlaybackMode.GateLoop && releaseFramesRemaining == 0) {
                    resetWarpLoop(active)
                } else {
                    stopImmediately()
                    break
                }
            }

            val relativeFrame = if (active.warpMode == SampleWarpMode.Warp) {
                (warpOutputFrames * tempoRatio).toLong().coerceAtLeast(0L)
            } else {
                (sourcePosition - active.startFrame).toLong().coerceAtLeast(0L)
            }
            if (active.warpMode == SampleWarpMode.Warp) readWarpFrame(active) else readSourceFrame(active)
            val modulationFrame = destinationFrameOffset + rendered
            var gain = gainAt(
                active,
                relativeFrame,
                modulation?.volumeGain?.get(modulationFrame) ?: active.volumeGain,
                modulation?.fadeInFrames?.get(modulationFrame) ?: active.fadeInFrames,
                modulation?.fadeOutFrames?.get(modulationFrame) ?: active.fadeOutFrames,
            )
            if (releaseFramesRemaining > 0) {
                gain *= releaseFramesRemaining.toFloat() / releaseFramesTotal.toFloat()
            }
            val latencyGain = if (warpLatencyRemaining > 0) 0f else 1f
            val leftPan = modulation?.panLeftGain?.get(modulationFrame) ?: active.panLeftGain
            val rightPan = modulation?.panRightGain?.get(modulationFrame) ?: active.panRightGain
            val left = sourceFrameBuffer[0] * gain * leftPan * latencyGain
            val right = sourceFrameBuffer[1] * gain * rightPan * latencyGain
            val transitionProgress = if (transitionFramesRemaining > 0) {
                1f - transitionFramesRemaining.toFloat() / transitionFramesTotal.toFloat()
            } else 1f
            val renderedLeft = transitionLeft + (left - transitionLeft) * transitionProgress
            val renderedRight = transitionRight + (right - transitionRight) * transitionProgress
            val destination = (destinationFrameOffset + rendered) * block.channels
            block.samples[destination] += renderedLeft
            if (block.channels > 1) block.samples[destination + 1] += renderedRight

            lastLeft = renderedLeft
            lastRight = renderedRight
            if (transitionFramesRemaining > 0) transitionFramesRemaining--
            if (releaseFramesRemaining > 0) {
                releaseFramesRemaining--
                if (releaseFramesRemaining == 0) {
                    stopImmediately()
                    break
                }
            }
            when (active.warpMode) {
                SampleWarpMode.Off -> sourcePosition += 1.0
                SampleWarpMode.Repitch -> sourcePosition += tempoRatio
                SampleWarpMode.Warp -> {
                    if (warpLatencyRemaining > 0) {
                        warpLatencyRemaining--
                    } else {
                        advanceWarp(active)
                    }
                }
            }
            if (active.playbackMode == SamplePlaybackMode.OneShot) {
                val finished = if (active.warpMode == SampleWarpMode.Warp) {
                    warpFinished(active)
                } else {
                    sourcePosition >= active.endFrame
                }
                if (finished) stopImmediately()
            }
            rendered++
        }
    }

    fun stopImmediately() {
        snapshot = null
        key = null
        sourcePosition = 0.0
        transitionFramesRemaining = 0
        transitionFramesTotal = 0
        transitionLeft = 0f
        transitionRight = 0f
        releaseFramesRemaining = 0
        releaseFramesTotal = 0
        lastLeft = 0f
        lastRight = 0f
        tempoRatio = 1.0
        targetTempoRatio = 1.0
        warpLatencyRemaining = 0
        warpOutputFrames = 0L
        warpConsumedSourceFrames = 0.0
        warpGrainPhase = 0
        warpCurrentGrainStart = 0.0
        warpPreviousGrainStart = 0.0
        warpPreviousGrainValid = false
        startSequence = -1L
    }

    private fun playbackBoundary(snapshot: SampleRenderSnapshot): Long =
        if (snapshot.playbackMode == SamplePlaybackMode.GateLoop && releaseFramesRemaining == 0) {
            snapshot.loopEndFrame
        } else {
            snapshot.endFrame
        }

    private fun readSourceFrame(snapshot: SampleRenderSnapshot) {
        sourceFrameBuffer[0] = interpolatedSample(snapshot, sourcePosition, 0)
        sourceFrameBuffer[1] = if (snapshot.source.channels == 1) {
            sourceFrameBuffer[0]
        } else {
            interpolatedSample(snapshot, sourcePosition, 1)
        }
    }

    /**
     * Dependency-free synchronous granular OLA. Each grain reads at 1x (pitch lock), while
     * grain anchors follow the tempo ratio. The implementation is allocation-free and lives
     * in commonMain, so every supported desktop target uses identical DSP and project state.
     */
    private fun readWarpFrame(snapshot: SampleRenderSnapshot) {
        if (warpLatencyRemaining > 0) {
            sourceFrameBuffer[0] = 0f
            sourceFrameBuffer[1] = 0f
            return
        }
        val blend = warpGrainPhase.toFloat() / WARP_GRAIN_HOP.toFloat()
        val currentPosition = wrapWarpPosition(snapshot, warpCurrentGrainStart + warpGrainPhase)
        val previousPosition = wrapWarpPosition(
            snapshot,
            warpPreviousGrainStart + warpGrainPhase + WARP_GRAIN_HOP,
        )
        var channel = 0
        while (channel < 2) {
            val sourceChannel = if (snapshot.source.channels == 1) 0 else channel
            val current = interpolatedSample(snapshot, currentPosition, sourceChannel)
            val previous = if (warpPreviousGrainValid) {
                interpolatedSample(snapshot, previousPosition, sourceChannel)
            } else current
            sourceFrameBuffer[channel] = previous + (current - previous) * blend
            channel++
        }
        sourcePosition = currentPosition
    }

    private fun advanceWarp(snapshot: SampleRenderSnapshot) {
        warpOutputFrames++
        warpConsumedSourceFrames += tempoRatio
        warpGrainPhase++
        if (warpGrainPhase >= WARP_GRAIN_HOP) {
            warpGrainPhase = 0
            warpPreviousGrainStart = warpCurrentGrainStart
            warpPreviousGrainValid = true
            warpCurrentGrainStart += WARP_GRAIN_HOP * tempoRatio
            if (snapshot.playbackMode == SamplePlaybackMode.GateLoop) {
                warpCurrentGrainStart = wrapWarpPosition(snapshot, warpCurrentGrainStart)
            }
        }
    }

    private fun warpFinished(snapshot: SampleRenderSnapshot): Boolean =
        warpLatencyRemaining == 0 &&
            warpConsumedSourceFrames >= if (
                snapshot.playbackMode == SamplePlaybackMode.GateLoop && releaseFramesRemaining == 0
            ) {
                snapshot.loopEndFrame - snapshot.loopStartFrame
            } else {
                snapshot.activeFrames
            }

    private fun resetWarpLoop(snapshot: SampleRenderSnapshot) {
        warpOutputFrames = 0L
        warpConsumedSourceFrames = 0.0
        warpGrainPhase = 0
        warpCurrentGrainStart = snapshot.loopStartFrame.toDouble()
        warpPreviousGrainStart = warpCurrentGrainStart
        warpPreviousGrainValid = false
        sourcePosition = warpCurrentGrainStart
    }

    private fun wrapWarpPosition(snapshot: SampleRenderSnapshot, position: Double): Double {
        val looping = snapshot.playbackMode == SamplePlaybackMode.GateLoop && releaseFramesRemaining == 0
        val lower = if (looping) snapshot.loopStartFrame else snapshot.startFrame
        val upper = if (looping) snapshot.loopEndFrame else snapshot.endFrame
        val length = (upper - lower).toDouble().coerceAtLeast(1.0)
        return if (looping) {
            lower + ((position - lower) % length + length) % length
        } else {
            position.coerceIn(lower.toDouble(), (upper - 1).toDouble())
        }
    }

    private fun interpolatedSample(snapshot: SampleRenderSnapshot, position: Double, channel: Int): Float {
        val lowerFrame = floor(position).toLong()
        val fraction = (position - lowerFrame).toFloat()
        val upperFrame = (lowerFrame + 1L).coerceAtMost(snapshot.endFrame - 1L)
        val lower = snapshot.source.sample(lowerFrame.coerceIn(snapshot.startFrame, snapshot.endFrame - 1L), channel)
        val upper = snapshot.source.sample(upperFrame, channel)
        return lower + (upper - lower) * fraction
    }

    private fun rampTempoRatio() {
        val frames = (configuration.sampleRate * TEMPO_RAMP_SECONDS).coerceAtLeast(1f)
        tempoRatio += (targetTempoRatio - tempoRatio) / frames
    }

    private fun gainAt(
        snapshot: SampleRenderSnapshot,
        relativeFrame: Long,
        volumeGain: Float,
        fadeInFrames: Int,
        fadeOutFrames: Int,
    ): Float {
        var gain = volumeGain
        if (fadeInFrames > 0 && relativeFrame < fadeInFrames) {
            gain *= relativeFrame.toFloat() / fadeInFrames.toFloat()
        }
        val fadeOutStart = snapshot.activeFrames - fadeOutFrames
        if (fadeOutFrames > 0 && relativeFrame >= fadeOutStart) {
            gain *= (snapshot.activeFrames - relativeFrame).toFloat() / fadeOutFrames.toFloat()
        }
        snapshot.volumeAutomationLane?.let { automation ->
            val timeMs = (relativeFrame.toDouble() * 1_000.0 / snapshot.source.sampleRate).toLong()
            gain *= automation.valueAt(timeMs, TimelineTrackAutomationTarget.VOLUME.defaultValue)
        }
        return gain
    }

    private companion object {
        const val RETRIGGER_TRANSITION_SECONDS = 0.00133f
        const val TEMPO_RAMP_SECONDS = 0.02f
        const val WARP_GRAIN_HOP = 128
        const val WARP_LATENCY_FRAMES = WARP_GRAIN_HOP
        const val MINIMUM_TEMPO_RATIO = 0.25
        const val MAXIMUM_TEMPO_RATIO = 4.0
    }
}

internal class SampleVoicePool(maximumVoices: Int = DEFAULT_MAXIMUM_VOICES) {
    private val voices = Array(maximumVoices) { SampleVoiceRenderer() }
    private val publishedActiveVoices = atomic(0)
    private val steals = atomic(0L)
    private var triggerSequence = 0L
    private var latestVoiceIndex = -1

    init {
        require(maximumVoices > 0)
    }

    val sourceFrame: Long get() = voices.getOrNull(latestVoiceIndex)?.sourceFrame ?: -1L
    val activeVoiceCount: Int get() = publishedActiveVoices.value
    val voiceStealCount: Long get() = steals.value

    fun prepare(configuration: AudioConfiguration) {
        voices.forEach { it.prepare(configuration) }
        triggerSequence = 0L
        latestVoiceIndex = -1
        publishedActiveVoices.value = 0
    }

    fun apply(command: SampleVoiceCommand) {
        when (command) {
            is SampleVoiceCommand.Start -> start(command)
            is SampleVoiceCommand.Release -> voices.forEach {
                it.requestRelease(command.key, command.fadeFrames)
            }
            is SampleVoiceCommand.Choke -> voices.forEach { it.requestStop(command.fadeFrames) }
        }
    }

    fun updateTempoRatio(ratio: Double) {
        voices.forEach { voice -> if (voice.isActive) voice.updateTempoRatio(ratio) }
    }

    private fun start(command: SampleVoiceCommand.Start) {
        var targetIndex = -1
        var oldestIndex = 0
        var oldestSequence = Long.MAX_VALUE
        var index = 0
        while (index < voices.size) {
            val voice = voices[index]
            if (!voice.isActive) {
                targetIndex = index
                break
            }
            if (voice.startSequence < oldestSequence) {
                oldestSequence = voice.startSequence
                oldestIndex = index
            }
            index++
        }
        if (targetIndex < 0) {
            targetIndex = oldestIndex
            steals.incrementAndGet()
        }
        triggerSequence++
        voices[targetIndex].trigger(command.snapshot, command.key, triggerSequence)
        latestVoiceIndex = targetIndex
        publishActiveCount()
    }

    fun render(
        block: AudioProcessingBlock,
        destinationFrameOffset: Int,
        frameCount: Int,
        modulation: SampleVoiceModulationBuffer? = null,
    ) {
        voices.forEach { voice ->
            if (voice.isActive) voice.render(block, destinationFrameOffset, frameCount, modulation)
        }
        publishActiveCount()
    }

    fun stop() {
        voices.forEach(SampleVoiceRenderer::stopImmediately)
        latestVoiceIndex = -1
        publishedActiveVoices.value = 0
    }

    private fun publishActiveCount() {
        var count = 0
        var index = 0
        while (index < voices.size) {
            if (voices[index].isActive) count++
            index++
        }
        publishedActiveVoices.value = count
    }

    private companion object {
        const val DEFAULT_MAXIMUM_VOICES = 16
    }
}

internal fun sampleTempoRatio(mode: SampleWarpMode, sourceBpm: Float?, workspaceBpm: Double): Double {
    if (mode == SampleWarpMode.Off) return 1.0
    val source = sourceBpm?.toDouble()?.takeIf { it.isFinite() && it > 0.0 } ?: return 1.0
    val workspace = workspaceBpm.takeIf { it.isFinite() && it > 0.0 } ?: return 1.0
    return (workspace / source).coerceIn(0.25, 4.0)
}
