package dev.anthonyhfm.amethyst.core.engine.audio.graph

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioChainDeviceRole
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioExecutionPlanTest {
    private val configuration = AudioConfiguration(
        sampleRate = 48_000,
        channels = 2,
        periodFrames = 8,
        maximumBlockFrames = 8,
    )

    @Test
    fun branchEffectOnlyProcessesItsOwnBranchAndMasterProcessesTheSum() {
        val branchA = Chain().apply {
            add(ConstantDevice(1f), fromUser = false)
            add(GainDevice(0.5f), fromUser = false)
        }
        val branchB = Chain().apply {
            add(ConstantDevice(1f), fromUser = false)
        }
        val chain = AudioChain().apply {
            add(TestContainerDevice(listOf(branchA, branchB)), fromUser = false)
            add(GainDevice(2f), fromUser = false)
            prepareAudio(configuration)
        }

        val output = render(chain)

        assertTrue(output.all { it == 3f })
    }

    @Test
    fun planSwapDoesNotReprepareUnchangedDevices() {
        val source = ConstantDevice(1f)
        val chain = AudioChain().apply {
            add(source, fromUser = false)
            prepareAudio(configuration)
        }
        assertEquals(1, source.prepareCount)
        assertTrue(render(chain).all { it == 1f })

        chain.add(GainDevice(0.25f), fromUser = false)

        assertEquals(1, source.prepareCount)
        assertTrue(render(chain).all { it == 0.25f })
    }

    @Test
    fun nonFiniteDeviceOutputIsSilencedAtItsBoundary() {
        val chain = AudioChain().apply {
            add(ConstantDevice(Float.NaN), fromUser = false)
            prepareAudio(configuration)
        }

        assertTrue(render(chain).all { it == 0f })
    }

    @Test
    fun latencyAndTailAreAggregatedAcrossSerialAndParallelNodes() {
        val branchA = Chain().apply { add(ConstantDevice(0f, 4, 10), fromUser = false) }
        val branchB = Chain().apply { add(ConstantDevice(0f, 8, 20), fromUser = false) }
        val chain = AudioChain().apply {
            add(TestContainerDevice(listOf(branchA, branchB)), fromUser = false)
            add(GainDevice(1f, latencyFrames = 2, tailFrames = 5), fromUser = false)
            prepareAudio(configuration)
        }

        assertEquals(10, chain.latencyFrames)
        assertEquals(25L, chain.tailFrames)
    }

    private fun render(chain: AudioChain): FloatArray {
        val block = AudioProcessingBlock(FloatArray(16), channels = 2, maximumFrames = 8)
        block.configure(frameCount = 8, frameOffset = 0)
        block.clear()
        chain.processAudio(block, AudioRenderContext(48_000, 0))
        return block.samples
    }
}

@Serializable
private data class TestAudioState(val placeholder: Int = 0) : DeviceState()

private class ConstantDevice(
    private val value: Float,
    override val latencyFrames: Int = 0,
    override val tailFrames: Long = 0L,
) : AudioChainDevice<TestAudioState>() {
    override val state = MutableStateFlow(TestAudioState())
    override val audioRole = AudioChainDeviceRole.Generator
    var prepareCount = 0

    override fun prepareAudio(configuration: AudioConfiguration) {
        prepareCount++
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        var index = 0
        while (index < block.frameCount * block.channels) {
            block.samples[index] += value
            index++
        }
    }

    @Composable override fun Content() = Unit
    override fun signalEnter(n: List<Signal>) = Unit
}

private class GainDevice(
    private val gain: Float,
    override val latencyFrames: Int = 0,
    override val tailFrames: Long = 0L,
) : AudioChainDevice<TestAudioState>() {
    override val state = MutableStateFlow(TestAudioState())

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        var index = 0
        while (index < block.frameCount * block.channels) {
            block.samples[index] *= gain
            index++
        }
    }

    @Composable override fun Content() = Unit
    override fun signalEnter(n: List<Signal>) = Unit
}

private class TestContainerDevice(
    private val chains: List<Chain>,
) : GenericChainDevice<TestAudioState>(), NestedChainDevice {
    override val state = MutableStateFlow(TestAudioState())
    override val capabilities = setOf(DeviceCapability.Container)
    override fun nestedChains(): List<Chain> = chains
    @Composable override fun Content() = Unit
    override fun timelineDuration(context: TimelineDurationContext) = TimelineDuration.None
    override fun signalEnter(n: List<Signal>) = Unit
}
