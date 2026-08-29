@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.devices.audio.sample

import dev.anthonyhfm.amethyst.core.engine.audio.trigger.PadTriggerKey
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeSourceRegistration
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplePlaybackTest {
    private val configuration = AudioConfiguration(1_000, 2, 16, 64)
    private val keyA = PadTriggerKey("launchpad-a", 1, 2)
    private val keyB = PadTriggerKey("launchpad-b", 1, 2)

    @Test
    fun constantPowerPanPreservesStereoPower() {
        val center = renderSnapshot(snapshot(state(pan = 0f)), frames = 8)
        val halfRight = renderSnapshot(snapshot(state(pan = 50f)), frames = 8)
        val left = renderSnapshot(snapshot(state(pan = -100f)), frames = 8)
        val centerPower = center[12] * center[12] + center[13] * center[13]
        val halfRightPower = halfRight[12] * halfRight[12] + halfRight[13] * halfRight[13]
        val leftPower = left[12] * left[12] + left[13] * left[13]

        assertTrue(halfRight[13] > halfRight[12])
        assertTrue(abs(centerPower - halfRightPower) < 0.0001f)
        assertTrue(abs(centerPower - leftPower) < 0.0001f)
        assertEquals(0f, left[13], 0.0001f)
    }

    @Test
    fun oneShotIgnoresPadUpAndSupportsPolyphony() {
        val pool = SampleVoicePool(maximumVoices = 4)
        pool.prepare(configuration)
        val snapshot = snapshot(state(playbackMode = SamplePlaybackMode.OneShot))
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot))
        pool.apply(SampleVoiceCommand.Start(0, keyB, snapshot))
        pool.apply(SampleVoiceCommand.Release(0, keyA, fadeFrames = 1))

        render(pool, 4)

        assertEquals(2, pool.activeVoiceCount)
    }

    @Test
    fun gateLoopRunsUntilMatchingPadUp() {
        val pool = SampleVoicePool(maximumVoices = 4)
        pool.prepare(configuration)
        val snapshot = snapshot(
            state(
                playbackMode = SamplePlaybackMode.GateLoop,
                loopStartPosition = 0.25f,
                loopEndPosition = 0.5f,
            ),
        )
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot))
        render(pool, 40)
        assertEquals(1, pool.activeVoiceCount)

        pool.apply(SampleVoiceCommand.Release(40, keyB, fadeFrames = 2))
        render(pool, 4)
        assertEquals(1, pool.activeVoiceCount)

        pool.apply(SampleVoiceCommand.Release(44, keyA, fadeFrames = 2))
        render(pool, 4)
        assertEquals(0, pool.activeVoiceCount)
    }

    @Test
    fun fullVoicePoolStealsOldestAndPublishesDiagnostic() {
        val pool = SampleVoicePool(maximumVoices = 1)
        pool.prepare(configuration)
        val snapshot = snapshot(state())
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot))
        pool.apply(SampleVoiceCommand.Start(1, keyB, snapshot))

        assertEquals(1, pool.voiceStealCount)
        assertEquals(1, pool.activeVoiceCount)
    }

    @Test
    fun workspaceLocalChokeGroupStopsOtherSampleAndSelfChokesBeforeStart() {
        val first = SampleChainDevice().apply {
            state.value = state(chokeGroup = 3)
        }
        val second = SampleChainDevice().apply {
            state.value = state(chokeGroup = 3)
        }
        val runtime = prepareRuntime(first, second)
        val block = AudioProcessingBlock(FloatArray(128), 2, 64)

        first.signalEnter(listOf(Signal.Midi("launchpad-a", 1, 1, 127)))
        processSources(block, runtime, 0, first, second)
        assertEquals(1, first.activeVoiceCount)

        second.signalEnter(listOf(Signal.Midi("launchpad-b", 1, 1, 127)))
        processSources(block, runtime, 8, first, second)

        assertEquals(0, first.activeVoiceCount)
        assertEquals(1, second.activeVoiceCount)
    }

    @Test
    fun chokeOffLeavesOtherSamplesPlaying() {
        val first = SampleChainDevice().apply { state.value = state(chokeGroup = 0) }
        val second = SampleChainDevice().apply { state.value = state(chokeGroup = 0) }
        val runtime = prepareRuntime(first, second)
        val block = AudioProcessingBlock(FloatArray(128), 2, 64)

        first.signalEnter(listOf(Signal.Midi("launchpad-a", 1, 1, 127)))
        processSources(block, runtime, 0, first, second)
        second.signalEnter(listOf(Signal.Midi("launchpad-b", 1, 1, 127)))
        processSources(block, runtime, 8, first, second)

        assertEquals(1, first.activeVoiceCount)
        assertEquals(1, second.activeVoiceCount)
    }

    @Test
    fun selfChokeKeepsOnlyNewestVoice() {
        val sample = SampleChainDevice().apply { state.value = state(chokeGroup = 7) }
        val runtime = prepareRuntime(sample)
        val block = AudioProcessingBlock(FloatArray(128), 2, 64)

        sample.signalEnter(listOf(Signal.Midi("launchpad-a", 1, 1, 127)))
        processSources(block, runtime, 0, sample)
        sample.signalEnter(listOf(Signal.Midi("launchpad-b", 2, 2, 127)))
        processSources(block, runtime, 8, sample)

        assertEquals(1, sample.activeVoiceCount)
    }

    @Test
    fun legacySampleStateKeepsOriginalFieldsAndUsesNewDefaults() {
        val legacy = LegacySampleState(
            fileName = "legacy.wav",
            volumeDb = 3f,
            startPosition = 0.2f,
            endPosition = 0.8f,
            sourceId = "source-a",
        )

        val bytes = ProtoBuf.encodeToByteArray(LegacySampleState.serializer(), legacy)
        val restored = ProtoBuf.decodeFromByteArray(SampleChainDeviceState.serializer(), bytes)

        assertEquals("legacy.wav", restored.fileName)
        assertEquals(3f, restored.volumeDb)
        assertEquals(0.2f, restored.startPosition)
        assertEquals(0.8f, restored.endPosition)
        assertEquals("source-a", restored.sourceId)
        assertEquals(0f, restored.pan)
        assertEquals(SamplePlaybackMode.OneShot, restored.playbackMode)
        assertEquals(0, restored.chokeGroup)
        assertEquals(SampleWarpMode.Off, restored.warpMode)
        assertEquals(null, restored.sourceBpm)
    }

    @Test
    fun samplerRegistersEveryPerformanceParameter() {
        val descriptors = SampleChainDevice().parameterDescriptors
        val ids = descriptors.map { it.id }.toSet()
        assertEquals(descriptors.size, ids.size)
        assertTrue(
            ids.containsAll(
                setOf("gain", "pan", "fadeIn", "fadeOut", "start", "end", "loopStart", "loopEnd", "mode", "sourceBpm"),
            ),
        )
    }

    @Test
    fun tempoRatioIsDeterministicAndMissingSourceBpmFallsBackSafely() {
        assertEquals(0.75, sampleTempoRatio(SampleWarpMode.Repitch, 120f, 90.0))
        assertEquals(2.0, sampleTempoRatio(SampleWarpMode.Warp, 60f, 120.0))
        assertEquals(1.0, sampleTempoRatio(SampleWarpMode.Off, 60f, 240.0))
        assertEquals(1.0, sampleTempoRatio(SampleWarpMode.Warp, null, 120.0))
    }

    @Test
    fun repitchFollowsBeatLengthAndChangesPitch() {
        val sampleState = state(
            warpMode = SampleWarpMode.Repitch,
            sourceBpm = 100f,
            frames = 200,
            sample = { frame -> sin(2.0 * kotlin.math.PI * 25.0 * frame / configuration.sampleRate).toFloat() },
        )
        val pool = SampleVoicePool(1).apply { prepare(configuration) }
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot(sampleState, workspaceBpm = 200.0)))
        val output = render(pool, 100)

        assertEquals(0, pool.activeVoiceCount)
        assertTrue(positiveZeroCrossings(output, fromFrame = 10) in 4..6)
    }

    @Test
    fun warpPreservesPitchAndReportsItsLatency() {
        val sampleState = state(
            warpMode = SampleWarpMode.Warp,
            sourceBpm = 200f,
            frames = 512,
            sample = { frame -> sin(2.0 * kotlin.math.PI * 31.25 * frame / configuration.sampleRate).toFloat() },
        )
        val pool = SampleVoicePool(1).apply { prepare(configuration) }
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot(sampleState, workspaceBpm = 100.0)))
        val output = render(pool, 1_152)

        assertEquals(0, pool.activeVoiceCount)
        assertTrue(output.take(SampleChainDevice.WARP_LATENCY_FRAMES * 2).all { it == 0f })
        assertTrue(positiveZeroCrossings(output, fromFrame = 256) in 25..38)
        assertEquals(SampleChainDevice.WARP_LATENCY_FRAMES, SampleChainDevice().apply {
            state.value = sampleState
        }.latencyFrames)
    }

    @Test
    fun liveTempoChangeRampsWithoutResettingVoice() {
        val sampleState = state(warpMode = SampleWarpMode.Repitch, sourceBpm = 100f, frames = 512)
        val pool = SampleVoicePool(1).apply { prepare(configuration) }
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot(sampleState, workspaceBpm = 100.0)))
        render(pool, 16)
        val before = pool.sourceFrame
        pool.updateTempoRatio(2.0)
        render(pool, 16)

        assertTrue(pool.sourceFrame > before)
        assertTrue(pool.sourceFrame - before < 32L)
        assertEquals(1, pool.activeVoiceCount)
    }

    @Test
    fun scheduledPadTriggerStartsAtPublishedAudioFrame() {
        val sample = SampleChainDevice().apply { state.value = state(frames = 128) }
        val runtime = prepareRuntime(sample)
        runtime.publishFrame(10)
        sample.signalEnter(listOf(Signal.Midi("launchpad-a", 1, 1, 127)))
        val block = AudioProcessingBlock(FloatArray(32), 2, 16).apply { configure(16, 0) }
        sample.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))

        assertTrue(block.samples.take(20).all { it == 0f })
        assertTrue(block.samples.drop(20).any { abs(it) > 0.0001f })
    }

    @Test
    fun macroMappingModulatesSamplerGainInTheAudioCallback() {
        val previousMacros = WorkspaceRepository.macros.value
        val previousMappings = WorkspaceRepository.parameterMappings.value
        val macro = Macro(value = 0)
        val sample = SampleChainDevice().apply { state.value = state(frames = 128) }
        try {
            WorkspaceRepository.setMacros(listOf(macro), undoable = false)
            WorkspaceRepository.setParameterMappings(
                listOf(ParameterMapping(
                    macroId = macro.id,
                    target = ParameterAddress(sample.selectionUUID, "gain"),
                )),
                undoable = false,
            )
            val runtime = prepareRuntime(sample)
            val block = AudioProcessingBlock(FloatArray(128), 2, 64)

            runtime.publishFrame(0)
            sample.signalEnter(listOf(Signal.Midi("pad", 1, 1, 127)))
            block.configure(32, 0)
            block.clear()
            sample.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
            val quietPeak = block.samples.maxOf { abs(it) }

            sample.resetAudio()
            WorkspaceRepository.setMacroValue(0, macro.copy(value = 127), undoable = false)
            runtime.publishFrame(64)
            sample.signalEnter(listOf(Signal.Midi("pad", 1, 1, 127)))
            block.configure(32, 64)
            block.clear()
            sample.processAudio(block, AudioRenderContext(configuration.sampleRate, 64))
            val loudPeak = block.samples.maxOf { abs(it) }

            assertTrue(loudPeak > quietPeak * 20f, "Expected macro gain mapping to affect rendered PCM")
        } finally {
            WorkspaceRepository.setParameterMappings(previousMappings, undoable = false)
            WorkspaceRepository.setMacros(previousMacros, undoable = false)
        }
    }

    private fun state(
        pan: Float = 0f,
        playbackMode: SamplePlaybackMode = SamplePlaybackMode.OneShot,
        loopStartPosition: Float? = null,
        loopEndPosition: Float? = null,
        chokeGroup: Int = 0,
        warpMode: SampleWarpMode = SampleWarpMode.Off,
        sourceBpm: Float? = null,
        frames: Int = 128,
        sample: (Int) -> Float = { 0.5f },
    ): SampleChainDeviceState = SampleChainDeviceState(
        fileName = "test.raw",
        rawData = pcm16Mono(frames, sample),
        sampleRate = configuration.sampleRate,
        channels = 1,
        bitDepth = 16,
        totalDurationMs = frames.toLong(),
        isLoaded = true,
        pan = pan,
        playbackMode = playbackMode,
        loopStartPosition = loopStartPosition,
        loopEndPosition = loopEndPosition,
        chokeGroup = chokeGroup,
        warpMode = warpMode,
        sourceBpm = sourceBpm,
    )

    private fun snapshot(state: SampleChainDeviceState, workspaceBpm: Double = state.sourceBpm?.toDouble() ?: 120.0): SampleRenderSnapshot {
        val source = checkNotNull(SampleRenderSnapshot.prepareSource(state, configuration.sampleRate))
        return checkNotNull(SampleRenderSnapshot.from(state, source, workspaceBpm))
    }

    private fun renderSnapshot(snapshot: SampleRenderSnapshot, frames: Int): FloatArray {
        val pool = SampleVoicePool(maximumVoices = 2)
        pool.prepare(configuration)
        pool.apply(SampleVoiceCommand.Start(0, keyA, snapshot))
        return render(pool, frames)
    }

    private fun render(pool: SampleVoicePool, frames: Int): FloatArray {
        val block = AudioProcessingBlock(FloatArray(frames * 2), 2, frames)
        block.configure(frames, 0)
        block.clear()
        pool.render(block, 0, frames)
        return block.samples
    }

    private fun prepareRuntime(vararg sources: SampleChainDevice): AudioTriggerRuntime =
        AudioTriggerRuntime().also { runtime ->
            sources.forEach { source ->
                source.audioTriggerRuntime = runtime
                source.prepareAudio(configuration)
            }
            runtime.replaceSources(sources.map(::ChokeSourceRegistration).toTypedArray())
        }

    private fun processSources(
        block: AudioProcessingBlock,
        runtime: AudioTriggerRuntime,
        absoluteFrame: Long,
        vararg sources: SampleChainDevice,
    ) {
        runtime.publishFrame(absoluteFrame)
        block.configure(8, absoluteFrame)
        block.clear()
        sources.forEach { source ->
            source.processAudio(block, AudioRenderContext(configuration.sampleRate, absoluteFrame))
        }
    }

    private fun positiveZeroCrossings(samples: FloatArray, fromFrame: Int): Int {
        var crossings = 0
        var frame = fromFrame.coerceAtLeast(1)
        while (frame < samples.size / 2) {
            val previous = samples[(frame - 1) * 2]
            val current = samples[frame * 2]
            if (previous <= 0f && current > 0f) crossings++
            frame++
        }
        return crossings
    }
}

private fun pcm16Mono(frameCount: Int, sample: (Int) -> Float): ByteArray {
    val result = ByteArray(frameCount * 2)
    var frame = 0
    while (frame < frameCount) {
        val value = (sample(frame).coerceIn(-1f, 1f) * 32_767f).toInt()
        result[frame * 2] = (value and 0xff).toByte()
        result[frame * 2 + 1] = ((value ushr 8) and 0xff).toByte()
        frame++
    }
    return result
}

@Serializable
private data class LegacySampleState(
    @ProtoNumber(1) val fileName: String = "",
    @ProtoNumber(2) val rawData: ByteArray? = null,
    @ProtoNumber(3) val sampleRate: Int = 44_100,
    @ProtoNumber(4) val channels: Int = 2,
    @ProtoNumber(5) val bitDepth: Int = 16,
    @ProtoNumber(6) val totalDurationMs: Long = 0,
    @ProtoNumber(7) val isLoaded: Boolean = false,
    @ProtoNumber(8) val fadeInMs: Float = 0f,
    @ProtoNumber(9) val fadeOutMs: Float = 0f,
    @ProtoNumber(10) val volumeDb: Float = 0f,
    @ProtoNumber(11) val startPosition: Float = 0f,
    @ProtoNumber(12) val endPosition: Float = 1f,
    @ProtoNumber(14) val sourceId: String? = null,
)
