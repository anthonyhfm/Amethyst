package dev.anthonyhfm.amethyst.devices.audio.sample

import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PolyphaseSincResampler
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import kotlinx.atomicfu.AtomicLongArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.floor
import kotlin.math.pow

/**
 * Stable trigger-time view of a sample.
 *
 * The PCM payload stays in its compact serialized representation. Conversion to
 * Float32 happens only for the frames requested by the audio callback.
 */
internal class SampleRenderSnapshot private constructor(
    val source: ByteArrayPcmAudioSource,
    val startFrame: Long,
    val endFrame: Long,
    val fadeInFrames: Int,
    val fadeOutFrames: Int,
    val volumeGain: Float,
    val volumeAutomationLane: TimelineAutomationLane?,
) {
    val activeFrames: Long
        get() = endFrame - startFrame

    companion object {
        fun from(
            state: SampleChainDeviceState,
            rawData: ByteArray? = state.resolvedRawData(),
        ): SampleRenderSnapshot? {
            rawData ?: return null
            val bytesPerSample = state.bitDepth / 8
            val bytesPerFrame = bytesPerSample * state.channels
            if (
                rawData.isEmpty() ||
                state.sampleRate <= 0 ||
                state.channels !in 1..2 ||
                state.bitDepth !in ByteArrayPcmAudioSource.SUPPORTED_BIT_DEPTHS ||
                bytesPerFrame <= 0 ||
                rawData.size % bytesPerFrame != 0
            ) {
                return null
            }
            val source = ByteArrayPcmAudioSource(
                id = state.fileName.ifBlank { "sample" },
                sampleRate = state.sampleRate,
                channels = state.channels,
                bitDepth = state.bitDepth,
                rawData = rawData,
            )

            val startFrame = (source.frameCount * state.startPosition)
                .toLong()
                .coerceIn(0, source.frameCount)
            val endFrame = (source.frameCount * state.endPosition)
                .toLong()
                .coerceIn(startFrame, source.frameCount)
            if (endFrame <= startFrame) return null

            val activeFrames = endFrame - startFrame
            val volumeDb = state.volumeDb.takeIf { it.isFinite() } ?: 0f
            return SampleRenderSnapshot(
                source = source,
                startFrame = startFrame,
                endFrame = endFrame,
                fadeInFrames = ((state.fadeInMs / 1_000f) * source.sampleRate)
                    .toInt()
                    .coerceIn(0, activeFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                fadeOutFrames = ((state.fadeOutMs / 1_000f) * source.sampleRate)
                    .toInt()
                    .coerceIn(0, activeFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                volumeGain = 10.0.pow(volumeDb / 20.0).toFloat(),
                volumeAutomationLane = state.volumeAutomationLane
                    ?.normalized()
                    ?.takeIf {
                        it.enabled &&
                            it.target == TimelineTrackAutomationTarget.VOLUME
                    },
            )
        }
    }
}

/**
 * Bounded MPSC queue. Its slots are allocated once and only publish references
 * to stable trigger snapshots; the PCM payload is never copied.
 */
internal class SampleTriggerQueue(
    private val capacity: Int = 32,
) {
    private val slots = atomicArrayOfNulls<SampleRenderSnapshot>(capacity)
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

    val droppedTriggers: Long
        get() = droppedCount.value

    fun offer(snapshot: SampleRenderSnapshot): Boolean {
        while (true) {
            val write = writeSequence.value
            val slot = (write % capacity).toInt()
            val difference = sequences[slot].value - write
            when {
                difference == 0L -> {
                    if (writeSequence.compareAndSet(write, write + 1L)) {
                        slots[slot].value = snapshot
                        sequences[slot].value = write + 1L
                        return true
                    }
                }
                difference < 0L -> {
                    droppedCount.incrementAndGet()
                    return false
                }
            }
        }
    }

    fun poll(): SampleRenderSnapshot? {
        val read = readSequence.value
        val slot = (read % capacity).toInt()
        if (sequences[slot].value - (read + 1L) != 0L) return null
        val snapshot = slots[slot].getAndSet(null)
        sequences[slot].value = read + capacity
        readSequence.value = read + 1L
        return snapshot
    }

    fun clear() {
        while (poll() != null) {
            // Drain without allocating or publishing stale triggers.
        }
    }

}

/**
 * Preallocated sample voice. The voice state is retained for the lifetime of
 * the device and is reset on retrigger rather than allocated by the render
 * thread.
 */
internal class SampleVoiceRenderer {
    private var configuration = AudioConfiguration(
        sampleRate = 44_100,
        channels = 2,
        periodFrames = 128,
    )
    private var snapshot: SampleRenderSnapshot? = null
    private var sourcePosition = 0.0
    private var resampler: PolyphaseSincResampler? = null
    private var preparedSourceRate = 0
    private var preparedSourceChannels = 0
    private val resampledFrame = FloatArray(2)
    private var transitionFramesRemaining = 0
    private var transitionFramesTotal = 0
    private var transitionLeft = 0f
    private var transitionRight = 0f
    private var lastLeft = 0f
    private var lastRight = 0f

    val isActive: Boolean
        get() = snapshot != null

    val sourceFrame: Long
        get() = snapshot?.let {
            floor(resampler?.sourcePosition ?: sourcePosition).toLong().coerceIn(
                it.startFrame,
                it.endFrame,
            )
        } ?: -1L

    fun prepare(configuration: AudioConfiguration) {
        this.configuration = configuration
        resampler = null
        preparedSourceRate = 0
        preparedSourceChannels = 0
        stop()
    }

    /**
     * Allocates a resampler, when necessary, on the control thread before a
     * trigger is published to the realtime audio thread.
     */
    fun prepareSnapshot(snapshot: SampleRenderSnapshot) {
        val source = snapshot.source
        if (
            preparedSourceRate == source.sampleRate &&
            preparedSourceChannels == source.channels
        ) {
            return
        }
        resampler = if (source.sampleRate == configuration.sampleRate) {
            null
        } else {
            PolyphaseSincResampler(
                sourceRate = source.sampleRate,
                outputRate = configuration.sampleRate,
                channels = source.channels,
            )
        }
        preparedSourceRate = source.sampleRate
        preparedSourceChannels = source.channels
    }

    fun trigger(snapshot: SampleRenderSnapshot) {
        check(
            preparedSourceRate == snapshot.source.sampleRate &&
                preparedSourceChannels == snapshot.source.channels
        ) {
            "Sample voice must be prepared before triggering"
        }
        transitionLeft = lastLeft
        transitionRight = lastRight
        transitionFramesTotal = (
            configuration.sampleRate * RETRIGGER_TRANSITION_SECONDS
        ).toInt().coerceAtLeast(1)
        transitionFramesRemaining = transitionFramesTotal
        this.snapshot = snapshot
        sourcePosition = snapshot.startFrame.toDouble()
        resampler?.reset(snapshot.startFrame.toDouble())
    }

    fun render(block: AudioProcessingBlock) {
        val activeSnapshot = snapshot ?: return
        var frame = 0
        while (frame < block.frameCount && snapshot != null) {
            val currentSourcePosition = resampler?.sourcePosition
                ?: sourcePosition
            if (currentSourcePosition >= activeSnapshot.endFrame) {
                stop()
                break
            }
            sourcePosition = currentSourcePosition
            val relativeFrame = (currentSourcePosition - activeSnapshot.startFrame)
                .toLong()
                .coerceAtLeast(0L)

            val gain = gainAt(activeSnapshot, relativeFrame)
            readSourceFrame(activeSnapshot)
            val left = resampledFrame[0] * gain
            val right = resampledFrame[1] * gain
            val transitionProgress = if (transitionFramesRemaining > 0) {
                1f - (
                    transitionFramesRemaining.toFloat() /
                        transitionFramesTotal.toFloat()
                    )
            } else {
                1f
            }
            val renderedLeft = transitionLeft +
                ((left - transitionLeft) * transitionProgress)
            val renderedRight = transitionRight +
                ((right - transitionRight) * transitionProgress)

            val destination = frame * block.channels
            if (block.channels > 0) {
                block.samples[destination] += renderedLeft
            }
            if (block.channels > 1) {
                block.samples[destination + 1] += renderedRight
            }
            for (channel in 2 until block.channels) {
                block.samples[destination + channel] += if (channel % 2 == 0) {
                    renderedLeft
                } else {
                    renderedRight
                }
            }

            lastLeft = renderedLeft
            lastRight = renderedRight
            if (transitionFramesRemaining > 0) transitionFramesRemaining--
            resampler?.advance()
                ?: run { sourcePosition += 1.0 }
            frame++
        }
    }

    fun stop() {
        snapshot = null
        sourcePosition = 0.0
        transitionFramesRemaining = 0
        transitionFramesTotal = 0
        transitionLeft = 0f
        transitionRight = 0f
        lastLeft = 0f
        lastRight = 0f
    }

    private fun readSourceFrame(snapshot: SampleRenderSnapshot) {
        val activeResampler = resampler
        if (activeResampler == null) {
            resampledFrame[0] = snapshot.source.sample(sourcePosition.toLong(), 0)
            resampledFrame[1] = if (snapshot.source.channels == 1) {
                resampledFrame[0]
            } else {
                snapshot.source.sample(sourcePosition.toLong(), 1)
            }
            return
        }
        activeResampler.readFrame(
            source = snapshot.source,
            destination = resampledFrame,
            lowerBoundFrame = snapshot.startFrame,
            upperBoundFrameExclusive = snapshot.endFrame,
        )
        if (snapshot.source.channels == 1) {
            resampledFrame[1] = resampledFrame[0]
        }
    }

    private fun gainAt(
        snapshot: SampleRenderSnapshot,
        relativeFrame: Long,
    ): Float {
        var gain = snapshot.volumeGain
        if (
            snapshot.fadeInFrames > 0 &&
            relativeFrame < snapshot.fadeInFrames
        ) {
            gain *= relativeFrame.toFloat() / snapshot.fadeInFrames.toFloat()
        }
        val fadeOutStart = snapshot.activeFrames - snapshot.fadeOutFrames
        if (
            snapshot.fadeOutFrames > 0 &&
            relativeFrame >= fadeOutStart
        ) {
            gain *= (snapshot.activeFrames - relativeFrame).toFloat() /
                snapshot.fadeOutFrames.toFloat()
        }
        val automation = snapshot.volumeAutomationLane
        if (automation != null) {
            val timeMs = (
                relativeFrame.toDouble() * 1_000.0 /
                    snapshot.source.sampleRate.toDouble()
                ).toLong()
            gain *= automation.valueAt(
                timeMs = timeMs,
                defaultValue = TimelineTrackAutomationTarget.VOLUME.defaultValue,
            )
        }
        return gain
    }

    private companion object {
        private const val RETRIGGER_TRANSITION_SECONDS = 0.00133f
    }
}

/**
 * Fixed-size polyphonic voice pool. No voices or PCM buffers are allocated by
 * the realtime render thread. When all voices are busy, the oldest round-robin
 * slot is stolen with the renderer's short click-free transition.
 */
internal class SampleVoicePool(
    maximumVoices: Int = DEFAULT_MAXIMUM_VOICES,
) {
    private val voices = Array(maximumVoices) { SampleVoiceRenderer() }
    private var nextVoiceIndex = 0
    private var latestVoiceIndex = -1

    init {
        require(maximumVoices > 0)
    }

    val sourceFrame: Long
        get() = voices.getOrNull(latestVoiceIndex)?.sourceFrame ?: -1L

    fun prepare(configuration: AudioConfiguration) {
        voices.forEach { it.prepare(configuration) }
        nextVoiceIndex = 0
        latestVoiceIndex = -1
    }

    fun prepareSnapshot(snapshot: SampleRenderSnapshot) {
        voices.forEach { it.prepareSnapshot(snapshot) }
    }

    fun trigger(snapshot: SampleRenderSnapshot) {
        var selectedIndex = -1
        var offset = 0
        while (offset < voices.size) {
            val index = (nextVoiceIndex + offset) % voices.size
            if (!voices[index].isActive) {
                selectedIndex = index
                break
            }
            offset++
        }
        if (selectedIndex < 0) selectedIndex = nextVoiceIndex

        voices[selectedIndex].trigger(snapshot)
        latestVoiceIndex = selectedIndex
        nextVoiceIndex = (selectedIndex + 1) % voices.size
    }

    fun render(block: AudioProcessingBlock) {
        voices.forEach { voice ->
            if (voice.isActive) voice.render(block)
        }
    }

    fun stop() {
        voices.forEach(SampleVoiceRenderer::stop)
        nextVoiceIndex = 0
        latestVoiceIndex = -1
    }

    private companion object {
        const val DEFAULT_MAXIMUM_VOICES = 16
    }
}
