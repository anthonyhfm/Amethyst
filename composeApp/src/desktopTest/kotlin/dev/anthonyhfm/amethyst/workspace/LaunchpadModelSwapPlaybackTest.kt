package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
import dev.anthonyhfm.amethyst.core.midi.FakeMidiAccess
import dev.anthonyhfm.amethyst.core.midi.FakeMidiDevice
import dev.anthonyhfm.amethyst.core.midi.FakeMidiInput
import dev.anthonyhfm.amethyst.core.midi.FakeMidiOutput
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ableton.AbletonNoteSpace
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitcherChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDeviceState
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LaunchpadModelSwapPlaybackTest {
    @Test
    fun pitchedKeyframesAndPitcherStayOnTheSameMainGridPadAcrossLayouts() {
        WorkspaceRepository.clean()
        val keyframes = KeyframesChainDevice()
        val pitcher = AbletonPitcherChainDevice()
        try {
            val original = ViewportLaunchpadPro().apply {
                launchpadId = "target"
                position.value = Offset(x = 20f, y = 30f)
            }
            ViewportRepository.setDevices(newDevices = listOf(original))
            keyframes.state.value = KeyframesChainDeviceState(
                frames = listOf(
                    Frame(
                        timing = Timing.Duration(duration = 100.milliseconds),
                        entries = listOf(
                            KeyframesEntry(
                                x = 21,
                                y = 38,
                                r = 1f,
                                g = 0f,
                                b = 0f,
                                launchpadId = "target",
                                localX = 1,
                                localY = 8,
                                abletonPitch = 36,
                            ),
                        ),
                    ),
                ),
            )
            WorkspaceRepository.lightsChain.add(device = keyframes, fromUser = false)
            val replacements = listOf(ViewportLaunchpadX(), ViewportMystrix(), ViewportLaunchpadPro())
            val expectedPositions = listOf(20 to 38, 20 to 37, 21 to 38)
            replacements.zip(other = expectedPositions).forEach { (replacement, expected) ->
                WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = replacement)
                keyframes.renderAnimation()
                val signal = keyframes.state.value.renderedAnimation
                    .flatMap { it.second }
                    .filterIsInstance<Signal.LED>()
                    .first { it.color != Color.Black }

                assertEquals(expected = expected, actual = signal.x to signal.y)
                assertEquals(expected = 36, actual = AbletonNoteSpace.note(signal = signal)?.pitch)
                val inferred = AbletonNoteSpace.note(signal = Signal.Midi(
                    origin = replacement,
                    x = signal.x,
                    y = signal.y,
                    velocity = 127,
                ))
                assertEquals(expected = 36, actual = inferred?.pitch)

                var output = emptyList<Signal>()
                pitcher.signalExit = { output = it }
                pitcher.state.value = pitcher.state.value.copy(pitch = 1)
                pitcher.signalEnter(n = listOf(signal))
                val shifted = output.single() as Signal.LED
                assertEquals(expected = expected.first + 1 to expected.second, actual = shifted.x to shifted.y)
            }
        } finally {
            pitcher.dispose()
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun invisibleNonzeroAbletonNotesKeepTheirIdentityForDownstreamPitchEffects() {
        WorkspaceRepository.clean()
        val keyframes = KeyframesChainDevice()
        val pitcher = AbletonPitcherChainDevice()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val hidden = KeyframesEntry(
                x = -2,
                y = -1,
                r = 1f,
                g = 0f,
                b = 0f,
                launchpadId = "target",
                localX = -2,
                localY = -1,
                abletonPitch = 1,
            )
            keyframes.state.value = KeyframesChainDeviceState(frames = listOf(
                Frame(timing = Timing.Duration(duration = 100.milliseconds), entries = listOf(hidden)),
            ))
            WorkspaceRepository.lightsChain.add(device = keyframes, fromUser = false)
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertEquals(expected = hidden, actual = keyframes.state.value.frames.single().entries.single())
            keyframes.renderAnimation()
            val signals = keyframes.state.value.renderedAnimation.flatMap { it.second }
            assertTrue(actual = signals.isNotEmpty())
            var output = emptyList<Signal>()
            pitcher.signalExit = { output = it }
            pitcher.state.value = pitcher.state.value.copy(pitch = 35)
            pitcher.signalEnter(n = signals.take(n = 1))
            val shifted = output.single() as Signal.LED
            assertEquals(expected = 36, actual = AbletonNoteSpace.note(signal = shifted)?.pitch)
            assertEquals(expected = 0 to 8, actual = shifted.x to shifted.y)
        } finally {
            pitcher.dispose()
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun modelSwapPausesSustainedTimelineNotesAndResumeUsesTheReplacementPad() {
        WorkspaceRepository.clean()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val note = MidiNote.withPaint(
                device = 0,
                pitch = 11,
                color = Color.Red,
                startTimeMs = 0,
                durationMs = 10_000,
            )
            val clip = MidiEntry(startTimeMs = 0, durationMs = 10_000, notes = listOf(note))
            val track = MidiTimelineTrack().apply { entries[0] = clip }
            TimelineRepository.addTrack(track = track)
            TimelineRepository.setPlayheadPosition(positionMs = 1_000)
            TimelineRepository.play()
            assertTrue(actual = TimelineRepository.isPlaying.value)
            val beforePosition = TimelineRepository.playheadPositionMs.value
            val replacement = ViewportLaunchpadX()
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = replacement)
            assertFalse(actual = TimelineRepository.isPlaying.value)
            assertTrue(actual = TimelineRepository.playheadPositionMs.value >= beforePosition)
            assertEquals(expected = listOf(note), actual = clip.notes)
            assertEquals(expected = 0 to 8, actual = clip.pitchToXY(note = note))
            val rendered = CountDownLatch(1)
            val originalExit = replacement.screen.screenExit
            replacement.screen.screenExit = { updates, colors ->
                if (colors[11] == Color.Red) {
                    rendered.countDown()
                }
                originalExit?.invoke(updates, colors)
            }
            TimelineRepository.play()
            assertTrue(actual = TimelineRepository.isPlaying.value)
            assertTrue(actual = TimelineRepository.playheadPositionMs.value >= beforePosition)
            assertTrue(actual = rendered.await(2, TimeUnit.SECONDS))
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun midiTimelineCanonicalNotesFollowReplacementLayoutAndRetainUnavailableControls() {
        WorkspaceRepository.clean()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val mainNote = MidiNote.withPaint(
                device = 0,
                pitch = 11,
                color = Color.Red,
                startTimeMs = 0,
                durationMs = 100,
            )
            val peripheralNote = mainNote.copy(pitch = 40)
            val clip = MidiEntry(startTimeMs = 0, durationMs = 100, notes = listOf(mainNote, peripheralNote))
            assertEquals(expected = 1 to 8, actual = clip.pitchToXY(note = mainNote))
            assertEquals(expected = 0 to 5, actual = clip.pitchToXY(note = peripheralNote))
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertEquals(expected = 0 to 8, actual = clip.pitchToXY(note = mainNote))
            assertNull(actual = clip.pitchToXY(note = peripheralNote))
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportMystrix())
            assertEquals(expected = 0 to 7, actual = clip.pitchToXY(note = mainNote))
            assertNull(actual = clip.pitchToXY(note = peripheralNote))
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadPro())
            assertEquals(expected = 1 to 8, actual = clip.pitchToXY(note = mainNote))
            assertEquals(expected = 0 to 5, actual = clip.pitchToXY(note = peripheralNote))
            assertEquals(expected = listOf(mainNote, peripheralNote), actual = clip.notes)
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun pianoRollTimelineSourceEmitsSolidGradientAndOffSignalsOnTheReplacementDevice() {
        WorkspaceRepository.clean()
        try {
            val neighbor = ViewportLaunchpadPro().apply { launchpadId = "neighbor" }
            val original = ViewportLaunchpadPro().apply {
                launchpadId = "target"
                position.value = Offset(x = 20f, y = 30f)
            }
            ViewportRepository.setDevices(newDevices = listOf(neighbor, original))
            val solid = MidiNote.withPaint(
                device = 1,
                pitch = 11,
                color = Color.Red,
                startTimeMs = 0,
                durationMs = 1_000,
            )
            val gradient = solid.copy(
                pitch = 12,
                led = solid.led.copy(
                    gradient = listOf(
                        NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
                        NoteGradientStop(position = 1f, r = 0f, g = 1f, b = 0f),
                    ),
                ),
            )
            val peripheral = solid.copy(pitch = 40)
            val midiEntry = MidiEntry(
                startTimeMs = 0,
                durationMs = 1_000,
                notes = listOf(solid, gradient, peripheral),
            )
            val effect = ChainEffectEntry(
                clipId = "piano-roll-clip",
                startTimeMs = 0,
                durationMs = 1_000,
                source = PianoRollChainDeviceState(midiEntry = midiEntry),
                processors = StateChain(),
            )
            val track = MidiTimelineTrack().apply { chainEffectEntries[0] = effect }
            TimelineRepository.addTrack(track = track)
            val source = TimelineRepository.chainEffectRuntime(clipId = effect.clipId)!!.source as PianoRollChainDevice
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertSame(
                expected = source,
                actual = TimelineRepository.chainEffectRuntime(clipId = effect.clipId)!!.source,
            )
            assertEquals(expected = midiEntry, actual = source.state.value.midiEntry)
            val solidRendered = CountDownLatch(1)
            val gradientRendered = CountDownLatch(1)
            val emitted = CopyOnWriteArrayList<Signal.LED>()
            source.signalExit = { signals ->
                signals.filterIsInstance<Signal.LED>().forEach { signal ->
                    emitted.add(element = signal)
                    if (signal.color != Color.Black && signal.x == 20 && signal.y == 38) {
                        solidRendered.countDown()
                    }
                    if (signal.color != Color.Black && signal.x == 21 && signal.y == 38) {
                        gradientRendered.countDown()
                    }
                }
            }
            source.startTimelineTrigger()
            assertTrue(actual = solidRendered.await(2, TimeUnit.SECONDS))
            assertTrue(actual = gradientRendered.await(2, TimeUnit.SECONDS))
            source.stopTimelineTrigger()
            assertEquals(expected = setOf(20 to 38, 21 to 38), actual = emitted.map { it.x to it.y }.toSet())
            val offPads = emitted.filter { it.color == Color.Black }.map { it.x to it.y }.toSet()
            assertEquals(expected = setOf(20 to 38, 21 to 38), actual = offPads)
        } finally {
            WorkspaceRepository.clean()
        }
    }


    @Test
    fun attachedAndVirtualMidiInputFollowTheReplacementLayoutAndRecordCanonicalPadAddresses(): Unit = runBlocking {
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData())
        val access = FakeMidiAccess()
        val manager = AmethystMidiManager(midiAccess = access, closeMidiAccessOnClose = false)
        try {
            val original = ViewportLaunchpadPro().apply {
                launchpadId = "target"
                position.value = Offset(x = 20f, y = 30f)
            }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val filter = CoordinateFilterChainDevice().apply {
                state.value = CoordinateFilterChainDeviceState(
                    padFilters = listOf(LaunchpadPadFilter(launchpadId = "target", localX = 1, localY = 8)),
                )
            }
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            val device = FakeMidiDevice(id = "input", name = "Normalized MIDI input")
            val connection = AmethystMidiDeviceConnection(
                device = device,
                input = FakeMidiInput(portId = "input"),
                output = FakeMidiOutput(portId = "output", device = device, access = access),
            )
            val replacements = listOf(ViewportLaunchpadX(), ViewportMystrix())
            val expectedPads = listOf(20 to 38, 20 to 37)
            replacements.zip(other = expectedPads).forEach { (replacement, expectedPad) ->
                WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = replacement)
                replacement.launchpadDevice = LaunchpadDeviceX(connection = connection)
                var emitted = emptyList<Signal>()
                filter.signalExit = { emitted = it }
                manager.run {
                    replacement.onMidiMessage(msg = byteArrayOf(0x90.toByte(), 36, 127))
                }
                val midi = emitted.filterIsInstance<Signal.Midi>().single()
                assertEquals(expected = expectedPad, actual = midi.x to midi.y)
                val recording = RecordingMidiMode()
                WorkspaceRepository.switchMode(mode = recording)
                manager.run {
                    replacement.onMidiMessage(msg = byteArrayOf(0x90.toByte(), 36, 127))
                }
                val attachedInput = recording.inputs.single()
                replacement.handlePadDragStart(x = 0, y = 0)
                replacement.handlePadDrag(x = 1, y = 0)
                replacement.handlePadDragEnd()
                assertEquals(expected = attachedInput, actual = recording.inputs[1])
                assertEquals(
                    expected = listOf(11 to 127, 11 to 127, 11 to 0, 12 to 127, 12 to 0),
                    actual = recording.inputs.map { it.first.pitch to it.first.velocity },
                )
                val (input, offset) = attachedInput
                val recordedGlobal = input.pitch % 10 + offset.x.toInt() to 9 - input.pitch / 10 + offset.y.toInt()
                assertEquals(expected = expectedPad, actual = recordedGlobal)
                WorkspaceRepository.switchMode(mode = dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode())
            }
        } finally {
            manager.close()
            WorkspaceRepository.clean()
        }
    }

    private class RecordingMidiMode : WorkspaceMode() {
        override val displayName = "Recording test"
        override val claimMidiInputs = true
        val inputs = mutableListOf<Pair<MidiInputData, Offset>>()

        override fun onKeyEvent(event: KeyEvent): Boolean = false

        override fun onMidiInput(data: MidiInputData, offset: Offset) {
            inputs.add(element = data to offset)
        }

        @Composable
        override fun Content(modifier: Modifier) = Unit
    }

}
