package dev.anthonyhfm.amethyst.devices.audio.effects

import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioFrameTriggerQueue
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
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
    fun duckerAttackReleaseAndStrengthAreSampleAccurate() {
        val device = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = "kick", attackMs = 4f, releaseMs = 10f, strength = 1f)
            prepareAudio(configuration)
            enqueueSidechainTrigger("kick", 2)
        }
        val output = process(device, FloatArray(40) { 1f })

        assertEquals(1f, output[0], 0.0001f)
        assertEquals(1f, output[2], 0.0001f)
        assertEquals(0.75f, output[4], 0.0001f)
        assertEquals(0f, output[10], 0.0001f)
        assertTrue(output[12] > output[10])
        assertTrue(output.all(Float::isFinite))
    }

    @Test
    fun triggerRoutingOnlyAllowsSelectedUpstreamSample() {
        val upstream = loadedSample("kick")
        val allowed = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = upstream.selectionUUID, attackMs = 0f, strength = 1f)
        }
        val allowedChain = AudioChain().apply {
            add(upstream, fromUser = false)
            add(allowed, fromUser = false)
            prepareAudio(configuration)
        }
        upstream.signalEnter(listOf(Signal.Midi("pad", 0, 0, 127)))
        process(allowedChain)
        assertTrue(allowed.currentGainReduction > 0f)

        val downstream = loadedSample("late-kick")
        val rejected = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = downstream.selectionUUID, attackMs = 0f, strength = 1f)
        }
        val rejectedChain = AudioChain().apply {
            add(rejected, fromUser = false)
            add(downstream, fromUser = false)
            prepareAudio(configuration)
        }
        downstream.signalEnter(listOf(Signal.Midi("pad", 0, 0, 127)))
        process(rejectedChain)
        assertEquals(0f, rejected.currentGainReduction)
    }

    @Test
    fun missingSourceAndBoundedTriggerQueueFailSafely() {
        val device = DuckerChainDevice().apply {
            state.value = DuckerChainDeviceState(sidechainSourceId = "missing")
            prepareAudio(configuration)
            enqueueSidechainTrigger("other", 0)
        }
        process(device, FloatArray(16) { 1f })
        assertEquals(0f, device.currentGainReduction)

        val queue = AudioFrameTriggerQueue(2)
        assertTrue(queue.offer(1))
        assertTrue(queue.offer(2))
        assertTrue(!queue.offer(3))
        assertEquals(1L, queue.droppedCount)
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

    private fun loadedSample(name: String): SampleChainDevice = SampleChainDevice().apply {
        selectionUUID = name
        state.value = SampleChainDeviceState(
            fileName = "$name.raw",
            rawData = pcm16Mono(64, 0.5f),
            sampleRate = configuration.sampleRate,
            channels = 1,
            bitDepth = 16,
            totalDurationMs = 64,
            isLoaded = true,
        )
    }

    private fun process(device: AudioChainDevice<*>, input: FloatArray): FloatArray {
        val output = input.copyOf()
        val frames = output.size / 2
        val block = AudioProcessingBlock(output, 2, frames).apply { configure(frames, 0) }
        device.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
        return output
    }

    private fun process(chain: AudioChain) {
        val block = AudioProcessingBlock(FloatArray(64), 2, 32).apply { configure(32, 0) }
        chain.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
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
