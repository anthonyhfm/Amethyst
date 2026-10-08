package dev.anthonyhfm.amethyst.conversion.apollo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloMoveAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloPatternAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import java.io.File
import dev.anthonyhfm.amethyst.conversion.apollo.utils.APOLLO_MODE_LIGHT
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ApolloRuntimeTest {
    @Test
    fun importedGateScalesSingleFrameFinalHoldAndEveryTransition() {
        val device = KeyframesChainDevice()
        try {
            device.state.value = ApolloPatternAdapter(model = apolloPattern(gate = 0.6)).toDeviceState() as KeyframesChainDeviceState
            device.renderAnimation()
            assertEquals(listOf(0, 60), device.state.value.renderedAnimation.map { it.first })
            assertEquals(TimelineDuration.Finite(milliseconds = 60), device.timelineDuration(context = TimelineDurationContext(bpm = 120.0)))

            device.state.value = ApolloPatternAdapter(model = apolloPattern(gate = 0.6, frameCount = 2)).toDeviceState() as KeyframesChainDeviceState
            device.renderAnimation()
            assertEquals(listOf(0, 60, 120), device.state.value.renderedAnimation.map { it.first })
            assertEquals(TimelineDuration.Finite(milliseconds = 120), device.timelineDuration(context = TimelineDurationContext(bpm = 120.0)))

            device.state.value = KeyframesChainDeviceState(
                repeats = 2,
                terminalGate = 0.3f,
                frames = listOf(Frame(timing = Timing.Rythm(timing = Timing.Rythm.RythmTiming._1_4), gate = 0.3f)),
            )
            assertEquals(TimelineDuration.Finite(milliseconds = 600), device.timelineDuration(context = TimelineDurationContext(bpm = 120.0)))
            assertEquals(TimelineDuration.Finite(milliseconds = 300), device.timelineDuration(context = TimelineDurationContext(bpm = 240.0)))

            device.state.value = KeyframesChainDeviceState(
                frames = listOf(
                    Frame(timing = Timing.Duration(duration = 100.milliseconds), gate = 1f),
                    Frame(timing = Timing.Duration(duration = 200.milliseconds), gate = 0.25f),
                ),
            )
            device.renderAnimation()
            assertEquals(listOf(0, 50, 250), device.state.value.renderedAnimation.map { it.first })
        } finally {
            device.dispose()
        }
    }

    @Test
    fun moveUsesLocalBoundsAbsoluteTargetsAndModeLightPassthrough() {
        val source = ViewportLaunchpadPro().apply {
            launchpadId = "source"
            position.value = Offset(x = 20f, y = 10f)
        }
        val received = mutableListOf<Signal.LED>()
        val move = OffsetChainDevice().apply {
            state.value = importedMove(y = -2)
            signalExit = { signals -> received.addAll(signals.filterIsInstance<Signal.LED>()) }
        }
        val input = Signal.LED(origin = source, x = 24, y = 15, color = Color.Red)
        try {
            move.ledSignalEnter(n = listOf(input))
            assertEquals(input.copy(y = 17), received.single())

            received.clear()
            move.state.value = importedMove(y = -2, wrap = true)
            move.ledSignalEnter(n = listOf(input.copy(y = 19)))
            assertEquals(11, received.single().y)

            received.clear()
            move.state.value = importedMove(absolute = true)
            move.ledSignalEnter(n = listOf(input, input.copy(x = 27, y = 12)))
            assertEquals(listOf(25 to 14, 25 to 14), received.map { it.x to it.y })

            received.clear()
            val mode = input.copy(x = 29, y = 10, extras = mapOf(APOLLO_MODE_LIGHT to 1))
            move.ledSignalEnter(n = listOf(mode))
            assertEquals(mode, received.single())

            received.clear()
            move.state.value = importedMove(y = 0, grid = 1, wrap = true)
            move.ledSignalEnter(n = listOf(input.copy(x = 20)))
            assertTrue(received.isEmpty())
        } finally {
            move.dispose()
            source.close()
        }
    }

    @Test
    fun outputContinuesThroughEffectsOnTargetWithoutRetriggeringTrackInput() {
        val previous = ViewportRepository.devices.value
        val source = ViewportLaunchpadPro().apply { launchpadId = "source" }
        val target = ViewportLaunchpadPro().apply {
            launchpadId = "target"
            position.value = Offset(x = 20f, y = 10f)
        }
        val received = CopyOnWriteArrayList<Signal.LED>()
        val completion = CountDownLatch(1)
        val pattern = KeyframesChainDevice().apply {
            state.value = ApolloPatternAdapter(model = apolloPattern()).toDeviceState() as KeyframesChainDeviceState
            renderAnimation()
            signalExit = { signals ->
                received.addAll(signals.filterIsInstance<Signal.LED>())
                completion.countDown()
            }
        }
        val filter = CoordinateFilterChainDevice().apply {
            state.value = CoordinateFilterChainDeviceState(filters = listOf(5 to 4), followSignalLaunchpad = true)
            signalExit = { signals -> pattern.signalEnter(n = signals) }
        }
        val output = OffsetChainDevice().apply {
            state.value = OffsetChainDeviceState(targetLaunchpadIndex = 1, targetLaunchpadId = "target")
            signalExit = { signals -> filter.signalEnter(n = signals) }
        }
        try {
            ViewportRepository.setDevices(newDevices = listOf(source, target))
            output.signalEnter(n = listOf(Signal.LED(origin = source, x = 5, y = 4, color = Color.Red)))
            assertTrue(completion.await(2, TimeUnit.SECONDS))
            assertTrue(received.any { it.x == 25 && it.y == 14 && it.color == Color.Red })
            assertTrue(received.any { it.x == 29 && it.y == 10 && it.extras[APOLLO_MODE_LIGHT] == 1 })
            assertTrue(received.all { it.origin === target })
        } finally {
            output.dispose()
            filter.dispose()
            pattern.dispose()
            ViewportRepository.setDevices(newDevices = previous)
            source.close()
            target.close()
        }
    }

    @Test
    fun disabledBranchDropsInputButMutedIndividualEffectBypasses() {
        val received = mutableListOf<Signal>()
        val model = ApolloModel.Chain(
            devices = listOf(
                ApolloModel.DeviceWrapper(
                    collapsed = false,
                    enabled = false,
                    device = ApolloModel.Device.Paint(color = ApolloModel.Color(r = 0, g = 63, b = 0)),
                ),
            ),
            name = "branch",
            enabled = false,
            secretMultiFilter = List(size = 101) { false },
        )
        val disabled = ApolloAdapter.resolveChain(model = model).unpack().apply {
            signalExit = { signals -> received.addAll(signals) }
        }
        val enabled = ApolloAdapter.resolveChain(model = model.copy(enabled = true)).unpack().apply {
            signalExit = { signals -> received.addAll(signals) }
        }
        val input = Signal.LED(origin = null, x = 5, y = 4, color = Color.Red)
        try {
            disabled.signalEnter(n = listOf(input))
            assertTrue(received.isEmpty())
            enabled.signalEnter(n = listOf(input))
            assertEquals<List<Signal>>(listOf(input), received)
        } finally {
            disabled.devices.value.forEach { it.dispose() }
            enabled.devices.value.forEach { it.dispose() }
        }
    }

    @Test
    fun realSuimaPreservesAllPatternsGatesRootsAndMutedDevicesWhenFixtureAvailable() {
        val fixture = File(System.getenv("AMETHYST_SUIMA_APOLLO_FIXTURE") ?: "/Users/anthony/Downloads/Suima Project/Suima Lights.approj")
        if (!fixture.isFile) {
            return
        }
        val converted = ApolloConverter.convertBytesToWorkspace(bytes = fixture.readBytes())
        val patterns = mutableListOf<KeyframesChainDeviceState>()
        var mutedDevices = 0
        fun visit(chain: StateChain) {
            mutedDevices += chain.mutedDeviceIndices.size
            chain.devices.forEach { device ->
                when (device) {
                    is KeyframesChainDeviceState -> patterns.add(element = device)
                    is GroupChainDeviceState -> device.groups.forEach { visit(chain = it.stateChain) }
                    is MultiGroupChainDeviceState -> {
                        visit(chain = device.preprocessChain)
                        device.groups.forEach { visit(chain = it.stateChain) }
                    }
                    is ChokeChainDeviceState -> visit(chain = device.stateChain)
                }
            }
        }
        visit(chain = converted.lights)
        assertEquals(588, patterns.size)
        assertEquals(129, patterns.count { it.terminalGate != 0.5f })
        assertEquals(20, patterns.count { it.rootKey != null })
        assertEquals(73, mutedDevices)
        assertEquals(22, patterns.count { state ->
            val entries = state.frames.flatMap { it.entries }
            entries.isNotEmpty() && entries.all { it.apolloIndex == 100 && it.localX == 9 && it.localY == 0 }
        })
    }

    private fun importedMove(y: Int = 0, absolute: Boolean = false, wrap: Boolean = false, grid: Int = 0): OffsetChainDeviceState =
        ApolloMoveAdapter(
            model = ApolloModel.Device.Move(
                offset = ApolloModel.Offset(x = 0, y = y, isAbsolute = absolute, absoluteX = 5, absoluteY = 5),
                gridMode = grid,
                wrap = wrap,
            ),
        ).toDeviceState() as OffsetChainDeviceState
}
