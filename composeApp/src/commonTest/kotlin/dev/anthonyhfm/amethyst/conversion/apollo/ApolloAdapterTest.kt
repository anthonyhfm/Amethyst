package dev.anthonyhfm.amethyst.conversion.apollo

import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloMoveAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloOutputAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloPatternAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.data.bindApolloLaunchpads
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApolloAdapterTest {
    @Test
    fun patternPreservesRootModeLightAndGateAcrossSaveReload() {
        val state = ApolloPatternAdapter(model = apolloPattern(rootKey = 55, gate = 0.6)).toDeviceState() as KeyframesChainDeviceState
        val original = StateChain(devices = listOf(state))
        val restored = AmethystProtoBuf.decodeFromByteArray<StateChain>(
            bytes = AmethystProtoBuf.encodeToByteArray(value = original),
        ).devices.single() as KeyframesChainDeviceState

        assertEquals(45, restored.rootKey)
        assertEquals(0.3f, restored.frames.single().gate)
        assertEquals(0.3f, restored.terminalGate)
        assertTrue(restored.apolloPattern)
        val modeLight = restored.frames.single().entries.single { it.apolloIndex == 100 }
        assertEquals(9, modeLight.x)
        assertEquals(0, modeLight.y)
        assertEquals(1f, modeLight.b)
        assertFalse(restored.frames.single().entries.any { it.apolloIndex == 99 })
    }

    @Test
    fun disabledChainsBlockWhileDisabledDevicesRemainBypassedAfterSaveReload() {
        val chain = ApolloModel.Chain(
            devices = listOf(
                ApolloModel.DeviceWrapper(
                    collapsed = false,
                    enabled = false,
                    device = ApolloModel.Device.Paint(color = ApolloModel.Color(r = 63, g = 0, b = 0)),
                ),
                ApolloModel.DeviceWrapper(
                    collapsed = false,
                    enabled = true,
                    device = ApolloModel.Device.Output(target = 1),
                ),
            ),
            name = "Disabled branch",
            enabled = false,
            secretMultiFilter = List(size = 101) { false },
        )
        val state = ApolloAdapter.resolveChain(model = chain).bindApolloLaunchpads(
            launchpadId = "source",
            launchpadIds = listOf("source", "target"),
        )
        val restored = AmethystProtoBuf.decodeFromByteArray<StateChain>(
            bytes = AmethystProtoBuf.encodeToByteArray(value = state),
        )

        assertEquals(3, restored.devices.size)
        assertEquals(listOf(1), restored.mutedDeviceIndices)
        val blocker = restored.devices.first() as CoordinateFilterChainDeviceState
        assertTrue(blocker.filters.isEmpty())
        assertTrue(blocker.followSignalLaunchpad)
        val output = restored.devices.last() as OffsetChainDeviceState
        assertEquals("target", output.targetLaunchpadId)
        assertEquals(1, output.targetLaunchpadIndex)
    }

    @Test
    fun moveRetainsAbsoluteDestinationAndUsesFullGridRatherThanUnbounded() {
        val move = ApolloMoveAdapter(
            model = ApolloModel.Device.Move(
                offset = ApolloModel.Offset(x = 2, y = -2, isAbsolute = true, absoluteX = 5, absoluteY = 6),
                gridMode = 0,
                wrap = true,
            ),
        ).toDeviceState() as OffsetChainDeviceState

        assertEquals(-2, move.offsetY)
        assertEquals(OffsetChainDeviceState.GridMode.FULL, move.gridMode)
        assertTrue(move.isAbsolute)
        assertEquals(5, move.absoluteX)
        assertEquals(3, move.absoluteY)
        assertTrue(move.wrap)
        assertTrue(move.apolloMove)
    }

    @Test
    fun outputPreservesTargetsBeyondTransmitChannelLimit() {
        val output = ApolloOutputAdapter(model = ApolloModel.Device.Output(target = 19)).toDeviceState() as OffsetChainDeviceState
        assertEquals(19, output.targetLaunchpadIndex)
    }
}

internal fun apolloPattern(rootKey: Int? = null, gate: Double = 1.0, frameCount: Int = 1): ApolloModel.Device.Pattern =
    ApolloModel.Device.Pattern(
        repeats = 1,
        gate = gate,
        pinch = 0.0,
        bilateral = false,
        frames = List(size = frameCount) { frameIndex ->
            ApolloModel.Frame(
                time = ApolloModel.Time(free = false, length = ApolloModel.Length(step = 5), divisor = 100),
                colors = List(size = 101) { index ->
                    when {
                        index == 55 && frameIndex % 2 == 0 -> ApolloModel.Color(r = 63, g = 0, b = 0)
                        index == 55 -> ApolloModel.Color(r = 0, g = 63, b = 0)
                        index == 99 -> ApolloModel.Color(r = 63, g = 63, b = 63)
                        index == 100 -> ApolloModel.Color(r = 0, g = 0, b = 63)
                        else -> ApolloModel.Color(r = 0, g = 0, b = 0)
                    }
                },
            )
        },
        playbackMode = 0,
        infinite = false,
        rootKey = rootKey,
        wrap = false,
        expandedIndex = 0,
    )
