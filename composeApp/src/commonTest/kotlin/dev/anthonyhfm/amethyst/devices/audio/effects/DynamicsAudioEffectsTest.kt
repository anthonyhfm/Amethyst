package dev.anthonyhfm.amethyst.devices.audio.effects

import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.SidechainAudioProvider
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicsAudioEffectsTest {
    private val configuration = AudioConfiguration(1_000, 2, 32, 128)

    @Test
    fun compressorFollowsRealAudioWithAttackReleaseAndStrength() {
        val device = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = "kick", attackMs = 4f, releaseMs = 10f, strength = 1f)
            prepareAudio(configuration)
            replaceEligibleSidechainSources(setOf("kick"))
        }
        val sidechain = FloatArray(40) { index ->
            if (index / 2 in 2..9) 1f else 0f
        }
        val output = process(device, FloatArray(40) { 1f }, "kick" to sidechain)

        assertEquals(1f, output[0], 0.0001f)
        assertEquals(1f, output[2], 0.0001f)
        assertTrue(output[4] in 0f..1f)
        assertTrue(output[14] < output[4])
        assertTrue(output[20] > output[18])
        assertTrue(output.all(Float::isFinite))

        device.resetAudio()
        device.state.value = device.state.value.copy(attackMs = 0f, strength = 0.5f)
        val halfDuck = process(device, FloatArray(8) { 1f }, "kick" to FloatArray(8) { 1f })
        assertTrue(halfDuck.all { abs(it - 0.5f) < 0.0001f })
    }

    @Test
    fun externalSourceCanAppearAfterCompressorButProgramSourceCannotSelfDuck() {
        val upstream = loadedSample("kick")
        val rejected = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = upstream.selectionUUID, attackMs = 0f, strength = 1f)
        }
        val rejectedChain = AudioChain().apply {
            add(upstream, fromUser = false)
            add(rejected, fromUser = false)
            prepareAudio(configuration)
        }
        upstream.signalEnter(listOf(Signal.Midi("pad", 0, 0, 127)))
        process(rejectedChain)
        assertEquals(0f, rejected.currentGainReduction)

        val downstream = loadedSample("late-kick", 1.0f)
        val program = loadedSample("music")
        val allowed = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = downstream.selectionUUID, attackMs = 0f, strength = 1f)
        }
        val allowedChain = AudioChain().apply {
            add(program, fromUser = false)
            add(allowed, fromUser = false)
            add(downstream, fromUser = false)
            prepareAudio(configuration)
        }
        program.signalEnter(listOf(Signal.Midi("pad", 0, 1, 127)))
        downstream.signalEnter(listOf(Signal.Midi("pad", 0, 0, 127)))
        val mixed = process(allowedChain)
        assertTrue(allowed.currentGainReduction > 0f)
        // The selected source remains dry in its later graph position while the
        // earlier program is fully ducked. The first frame is the voice ramp.
        assertTrue(mixed.drop(2).all { it in 0.99f..1.01f }, mixed.toList().toString())
    }

    @Test
    fun missingOrSilentSourceFailsSafely() {
        val device = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = "missing")
            prepareAudio(configuration)
            replaceEligibleSidechainSources(setOf("missing"))
        }
        process(device, FloatArray(16) { 1f })
        assertEquals(0f, device.currentGainReduction)

        val silent = process(device, FloatArray(16) { 1f }, "missing" to FloatArray(16))
        assertTrue(silent.all { it == 1f })
    }

    @Test
    fun saturatorSoftClipsExtremeInputAndDryMixIsExact() {
        val saturated = SaturatorChainDevice().apply {
            state.value = SaturatorChainDeviceState(driveDb = 36f, outputDb = 0f, dryWet = 1f)
            prepareAudio(configuration)
        }
        val hot = process(saturated, FloatArray(64) { if (it % 2 == 0) 1_000_000f else -1_000_000f })
        assertTrue(hot.all(Float::isFinite))
        assertTrue(hot.all { abs(it) <= 1.001f })

        val bypass = SaturatorChainDevice().apply {
            state.value = SaturatorChainDeviceState(driveDb = 36f, dryWet = 0f)
            prepareAudio(configuration)
        }
        val input = FloatArray(64) { (it - 32) / 16f }
        assertEquals(input.toList(), process(bypass, input).toList())
    }

    @Test
    fun dynamicsStatesRoundTripThroughRegistry() {
        val ducker = DuckerChainDeviceState("kick-id", 12f, 420f, 0.7f)
        val saturator = SaturatorChainDeviceState(18f, -3f, 0.6f, true)
        assertEquals(ducker, DeviceRegistry.deepCopyState(ducker))
        assertEquals(saturator, DeviceRegistry.deepCopyState(saturator))
    }

    private fun loadedSample(name: String, value: Float = 0.5f): SampleChainDevice = SampleChainDevice().apply {
        selectionUUID = name
        state.value = SampleChainDeviceState(
            fileName = "$name.raw",
            rawData = pcm16Mono(64, value),
            sampleRate = configuration.sampleRate,
            channels = 1,
            bitDepth = 16,
            totalDurationMs = 64,
            isLoaded = true,
        )
    }

    private fun process(
        device: AudioChainDevice<*>,
        input: FloatArray,
        sidechain: Pair<String, FloatArray>? = null,
    ): FloatArray {
        val output = input.copyOf()
        val frames = output.size / 2
        val block = AudioProcessingBlock(output, 2, frames).apply { configure(frames, 0) }
        val context = AudioRenderContext(configuration.sampleRate, 0)
        sidechain?.let { (sourceId, samples) ->
            val sourceBlock = AudioProcessingBlock(samples, 2, frames).apply { configure(frames, 0) }
            context.sidechainAudioProvider = SidechainAudioProvider { requested ->
                sourceBlock.takeIf { requested == sourceId }
            }
        }
        device.processAudio(block, context)
        return output
    }

    private fun process(chain: AudioChain): FloatArray {
        val block = AudioProcessingBlock(FloatArray(64), 2, 32).apply { configure(32, 0) }
        chain.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
        return block.samples
    }
}

private fun pcm16Mono(frames: Int, value: Float): ByteArray = ByteArray(frames * 2).also { bytes ->
    val sample = (value.coerceIn(-1f, 1f) * 32_767).toInt()
    var frame = 0
    while (frame < frames) {
        bytes[frame * 2] = (sample and 0xff).toByte()
        bytes[frame * 2 + 1] = ((sample ushr 8) and 0xff).toByte()
        frame++
    }
}
