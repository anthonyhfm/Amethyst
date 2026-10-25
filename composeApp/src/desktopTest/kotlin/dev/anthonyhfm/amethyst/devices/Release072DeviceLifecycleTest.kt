@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import java.io.File
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.FlipAdapter
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.PlaybackMode
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDevice
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class Release072DeviceLifecycleTest {
    @Test
    fun loopChokeClearsEachLayerAndOriginIndependently(): Unit = runBlocking {
        val loop = LoopChainDevice().apply {
            state.value = state.value.copy(repeat = 1, timing = Timing.Duration(duration = 20.milliseconds))
        }
        val output = CopyOnWriteArrayList<Signal.LED>()
        loop.signalExit = { output.addAll(elements = it.filterIsInstance<Signal.LED>()) }
        val first = Signal.LED(origin = "first", x = 1, y = 1, color = Color.Red, layer = 1)
        val second = first.copy(origin = "second", layer = 2)
        try {
            loop.signalEnter(n = listOf(first, second))
            Heaven.waitUntilIdleForTesting()
            assertEquals(expected = 2, actual = output.count { it.color != Color.Black })
            loop.onChoke()
            assertEquals(expected = setOf("first" to 1, "second" to 2), actual = output.filter { it.color == Color.Black }.map { it.origin to it.layer }.toSet())
        } finally {
            loop.dispose()
        }
    }

    @Test
    fun realSuimaPageThreeRapidTriggersAndPageChangesLeaveNoScheduledPlayback(): Unit = runBlocking {
        val fixture = File(System.getenv("AMETHYST_SUIMA_APOLLO_FIXTURE") ?: "/Users/anthony/Downloads/Suima Project/Suima Lights.approj")
        if (!fixture.isFile) {
            return@runBlocking
        }
        val converted = ApolloConverter.convertBytesToWorkspace(bytes = fixture.readBytes())
        val previousDevices = Heaven.devices
        val launchpad = ViewportLaunchpadPro().apply {
            launchpadId = converted.launchpadDevices.first().id
        }
        val output = CopyOnWriteArrayList<Signal>()
        val chain = converted.lights.unpack()
        try {
            Heaven.devices = listOf(launchpad)
            val patterns = chain.devicesDepthFirst().filterIsInstance<KeyframesChainDevice>()
            assertEquals(expected = 588, actual = patterns.size)
            patterns.forEach { it.renderAnimation() }
            chain.signalExit = { output.addAll(elements = it) }
            for (page in listOf(2, 0, 2)) {
                for (pad in 11..88) {
                    if (pad % 10 !in 1..8) {
                        continue
                    }
                    val press = Signal.LED(
                        origin = launchpad,
                        x = pad % 10,
                        y = pad / 10,
                        color = Color.White,
                        macroValues = listOf(page),
                    )
                    chain.signalEnter(n = listOf(press))
                    chain.signalEnter(n = listOf(press.copy(color = Color.Black)))
                }
            }
            Thread.sleep(60L)
            assertTrue(actual = output.isNotEmpty())
            chain.stopOutput()
            Heaven.waitUntilIdleForTesting()
            val afterStop = output.size
            Thread.sleep(100L)
            assertEquals(expected = afterStop, actual = output.size)
            println("Suima Page 3: 588 patterns, rapid pad triggers and page 3/1/3 routing completed; no playback after cleanup")
        } finally {
            chain.dispose()
            Heaven.devices = previousDevices
            launchpad.close()
        }
    }

    @Test
    fun convertedFlipPreservesMidiPressReleaseAndConfigurationAcrossReload() {
        val state = assertIs<FlipChainDeviceState>(value = FlipAdapter(
            blob = """{"number":[1.0],"pictctrl":[0.0],"umenu":[0.0],"umenu[1]":[0.0]}""",
        ).toDeviceStates().single())
        val saved = StateChain(devices = listOf(state))
        val restored = AmethystProtoBuf.decodeFromByteArray(
            deserializer = StateChain.serializer(),
            bytes = AmethystProtoBuf.encodeToByteArray(serializer = StateChain.serializer(), value = saved),
        ).unpackAudio()
        val flip = assertIs<FlipChainDevice>(value = restored.devices.value.single())
        val output = mutableListOf<Signal>()
        restored.signalExit = { output.addAll(elements = it) }
        val press = Signal.Midi(origin = null, x = 1, y = 2, velocity = 91, extras = mapOf("test" to 7))
        try {
            restored.signalEnter(n = listOf(press, press.copy(velocity = 0)))
            assertEquals(expected = listOf<Signal>(press.copy(x = 8), press.copy(x = 8, velocity = 0)), actual = output)
            assertEquals(expected = state, actual = flip.state.value)
        } finally {
            restored.dispose()
        }
    }

    @Test
    fun nestedChokeStopsDescendantsWithoutTouchingOtherGroups() {
        val probe = ChokeProbe()
        val nested = Chain().apply { add(device = probe, fromUser = false) }
        val group = GroupChainDevice().apply {
            state.value = state.value.copy(groups = listOf(Group(name = "Nested", chain = nested)))
        }
        val receiver = ChokeChainDevice().apply {
            state.value = state.value.copy(target = 29, mode = ChokeChainDeviceState.ChokeMode.Receive)
            state.value.chain.add(device = group, fromUser = false)
        }
        val unrelated = ChokeProbe()
        val other = ChokeChainDevice().apply {
            state.value = state.value.copy(target = 28)
            state.value.chain.add(device = unrelated, fromUser = false)
        }
        val sender = ChokeChainDevice().apply { state.value = state.value.copy(target = 29, mode = ChokeChainDeviceState.ChokeMode.Start) }
        try {
            repeat(times = 20) {
                sender.signalEnter(n = listOf(Signal.Midi(origin = null, x = 1, y = 1, velocity = 127)))
                sender.signalEnter(n = listOf(Signal.Midi(origin = null, x = 1, y = 1, velocity = 0)))
            }
            assertEquals(expected = 20, actual = probe.stops)
            assertEquals(expected = 0, actual = unrelated.stops)
        } finally {
            sender.dispose()
            receiver.dispose()
            other.dispose()
        }
    }

    @Test
    fun loopChokeEmitsOffAndCancelsPendingFixedRepeats(): Unit = runBlocking {
        val loop = LoopChainDevice().apply {
            state.value = LoopChainDeviceState(repeat = 4, timing = Timing.Duration(duration = 40.milliseconds))
        }
        val first = CountDownLatch(1)
        val output = CopyOnWriteArrayList<Signal.Midi>()
        loop.signalExit = { signals ->
            output.addAll(elements = signals.filterIsInstance<Signal.Midi>())
            first.countDown()
        }
        val press = Signal.Midi(origin = null, x = 1, y = 1, velocity = 100)
        try {
            loop.signalEnter(n = listOf(press))
            assertTrue(actual = first.await(2, TimeUnit.SECONDS))
            loop.onChoke()
            Heaven.waitUntilIdleForTesting()
            assertEquals(expected = 0, actual = output.last().velocity)
            val afterChoke = output.size
            Thread.sleep(180L)
            assertEquals(expected = afterChoke, actual = output.size)
        } finally {
            loop.dispose()
        }
    }

    @Test
    fun finiteLoopPairsPressesAndReleasesAfterReload(): Unit = runBlocking {
        val state = LoopChainDeviceState(repeat = 4, timing = Timing.Duration(duration = 10.milliseconds))
        val decoded = AmethystProtoBuf.decodeFromByteArray(
            deserializer = LoopChainDeviceState.serializer(),
            bytes = AmethystProtoBuf.encodeToByteArray(serializer = LoopChainDeviceState.serializer(), value = state),
        )
        val loop = LoopChainDevice().apply { this.state.value = decoded }
        val completed = CountDownLatch(8)
        val output = CopyOnWriteArrayList<Signal.Midi>()
        loop.signalExit = { signals ->
            output.addAll(elements = signals.filterIsInstance<Signal.Midi>())
            signals.forEach { completed.countDown() }
        }
        val press = Signal.Midi(origin = null, x = 1, y = 1, velocity = 100)
        try {
            loop.signalEnter(n = listOf(press))
            loop.signalEnter(n = listOf(press.copy(velocity = 0)))
            assertTrue(actual = completed.await(2, TimeUnit.SECONDS))
            Heaven.waitUntilIdleForTesting()
            assertEquals(expected = 4, actual = output.count { it.velocity > 0 })
            assertEquals(expected = 4, actual = output.count { it.velocity == 0 })
        } finally {
            loop.dispose()
        }
    }

    @Test
    fun heldKeyframeLoopStopsAtOneAndFourRepeatsAndRetriggers(): Unit = runBlocking {
        for (repeats in listOf(1, 4)) {
            val device = KeyframesChainDevice().apply {
                state.value = state.value.copy(
                    playbackMode = PlaybackMode.Loop,
                    repeats = repeats,
                    frames = listOf(Frame(
                        timing = Timing.Duration(duration = 20.milliseconds),
                        entries = listOf(KeyframesEntry(x = 1, y = 1, r = 1f, g = 0f, b = 0f)),
                    )),
                )
                renderAnimation()
            }
            val output = CopyOnWriteArrayList<Signal.LED>()
            val completed = CountDownLatch(1)
            device.onPlaybackEnd = { completed.countDown() }
            device.signalExit = { output.addAll(elements = it.filterIsInstance<Signal.LED>()) }
            val press = Signal.LED(origin = null, x = 1, y = 1, color = Color.Red)
            try {
                device.signalEnter(n = listOf(press))
                assertTrue(actual = completed.await(2, TimeUnit.SECONDS))
                Heaven.waitUntilIdleForTesting()
                assertEquals(expected = repeats, actual = output.count { it.color != Color.Black })
                val count = output.size
                Thread.sleep(100L)
                assertEquals(expected = count, actual = output.size)
                device.signalEnter(n = listOf(press.copy(color = Color.Black)))
                device.signalEnter(n = listOf(press))
                Heaven.waitUntilIdleForTesting()
                assertEquals(expected = repeats * 2, actual = output.count { it.color != Color.Black })
            } finally {
                device.dispose()
            }
        }
    }
}

private class ChokeProbe : GenericChainDevice<LoopChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(value = LoopChainDeviceState())
    var stops = 0
    @Composable
    override fun Content() = Unit
    override fun signalEnter(n: List<Signal>) = Unit
    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration = TimelineDuration.None
    override fun onChoke() { stops++ }
}
