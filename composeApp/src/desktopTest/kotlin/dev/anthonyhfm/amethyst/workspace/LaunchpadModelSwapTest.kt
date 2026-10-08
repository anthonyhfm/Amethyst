package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LaunchpadModelSwapTest {
    @Test
    fun replacementKeepsIdentitySelectionMidiPreferencesBindingsAndUndoHistory() {
        WorkspaceRepository.clean()
        try {
            val original = ViewportLaunchpadX().apply {
                launchpadId = "stable-id"
                position.value = Offset(x = 20f, y = 30f)
                rotationDegrees.floatValue = 90f
                savedMidiDeviceId = "offline-midi"
                savedInputPortId = "input-id"
                savedInputPortName = "input-name"
                savedOutputPortId = "output-id"
                savedOutputPortName = "output-name"
            }
            ViewportRepository.setDevices(newDevices = listOf(original))
            SelectionManager.select(element = Selectable.VirtualViewportDevice(element = original))
            val filter = CoordinateFilterChainDevice().apply {
                state.value = CoordinateFilterChainDeviceState(
                    padFilters = listOf(
                        LaunchpadPadFilter(launchpadId = "stable-id", localX = 0, localY = 8),
                    ),
                )
            }
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            val beforeBpm = WorkspaceRepository.bpm.value
            WorkspaceRepository.setBpm(bpm = beforeBpm + 1.0)
            val replacement = ViewportLaunchpadPro()
            assertTrue(
                actual = WorkspaceRepository.replaceVirtualDevice(
                    deviceId = original.launchpadId,
                    replacement = replacement,
                ),
            )
            assertEquals(expected = original.launchpadId, actual = replacement.launchpadId)
            assertEquals(expected = original.selectionUUID, actual = replacement.selectionUUID)
            assertSame(expected = original.position, actual = replacement.position)
            assertEquals(expected = 90f, actual = replacement.rotationDegrees.floatValue)
            assertEquals(expected = "offline-midi", actual = replacement.savedMidiDeviceId)
            assertEquals(expected = "input-id", actual = replacement.savedInputPortId)
            assertEquals(expected = "input-name", actual = replacement.savedInputPortName)
            assertEquals(expected = "output-id", actual = replacement.savedOutputPortId)
            assertEquals(expected = "output-name", actual = replacement.savedOutputPortName)
            val selected = SelectionManager.selections.value.single() as Selectable.VirtualViewportDevice
            assertSame(expected = replacement, actual = selected.element)
            assertEquals(expected = 1 to 9, actual = filter.state.value.padFilters.single().let { it.localX to it.localY })
            val saved = WorkspaceRepository.saveWorkspace()
            assertIs<SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro>(value = saved.launchpadDevices.single())
            assertEquals(expected = "stable-id", actual = saved.launchpadDevices.single().id)
            UndoManager.undo()
            assertIs<ViewportLaunchpadX>(value = ViewportRepository.devices.value.single())
            assertEquals(expected = 0 to 8, actual = filter.state.value.padFilters.single().let { it.localX to it.localY })
            UndoManager.redo()
            assertIs<ViewportLaunchpadPro>(value = ViewportRepository.devices.value.single())
            UndoManager.undo()
            UndoManager.undo()
            assertEquals(expected = beforeBpm, actual = WorkspaceRepository.bpm.value)
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun unavailablePeripheralBindingsCannotReachNeighboringDevices() {
        WorkspaceRepository.clean()
        val filter = CoordinateFilterChainDevice()
        val keyframes = KeyframesChainDevice()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            val neighbor = ViewportLaunchpadX().apply {
                launchpadId = "neighbor"
                position.value = Offset(x = -9f, y = 0f)
            }
            ViewportRepository.setDevices(newDevices = listOf(original, neighbor))
            filter.state.value = CoordinateFilterChainDeviceState(
                padFilters = listOf(
                    LaunchpadPadFilter(launchpadId = "target", localX = 0, localY = 4),
                ),
            )
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            keyframes.state.value = KeyframesChainDeviceState(
                rootKey = 40,
                rootKeyLaunchpadId = "target",
                frames = listOf(
                    Frame(
                        timing = Timing.Duration(duration = 100.milliseconds),
                        entries = listOf(
                            KeyframesEntry(
                                x = 0,
                                y = 4,
                                r = 1f,
                                g = 0f,
                                b = 0f,
                                launchpadId = "target",
                                localX = 0,
                                localY = 4,
                            ),
                        ),
                    ),
                ),
            )
            WorkspaceRepository.lightsChain.add(device = keyframes, fromUser = false)
            assertTrue(
                actual = WorkspaceRepository.replaceVirtualDevice(
                    deviceId = "target",
                    replacement = ViewportLaunchpadX(),
                ),
            )
            assertEquals(expected = -1 to 4, actual = filter.state.value.padFilters.single().let { it.localX to it.localY })
            assertTrue(actual = filter.resolvedFilters().isEmpty())
            var emitted = emptyList<Signal>()
            filter.signalExit = { emitted = it }
            filter.signalEnter(n = listOf(Signal.Midi(origin = neighbor, x = -1, y = 4, velocity = 127)))
            assertTrue(actual = emitted.isEmpty())
            keyframes.renderAnimation()
            assertTrue(actual = keyframes.state.value.renderedAnimation.flatMap { it.second }.isEmpty())
            assertNull(actual = keyframes.rootPosition())
            val unavailableAction = AutoPlayData.Action(x = -1, y = 4, down = true, launchpadId = "target")
            assertFalse(actual = AutoPlayRepository.isActionAvailable(action = unavailableAction))
            UndoManager.undo()
            assertEquals(expected = setOf(0 to 4), actual = filter.resolvedFilters())
            assertEquals(expected = 0 to 4, actual = keyframes.rootPosition())
            keyframes.renderAnimation()
            val renderedSignals = keyframes.state.value.renderedAnimation.flatMap { it.second }
            assertTrue(actual = renderedSignals.any { it is Signal.LED && it.color != Color.Black })
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun migratedGlobalFiltersKeepTheirBehaviorWithoutActivatingDormantLegacyCoordinates() {
        WorkspaceRepository.clean()
        val filter = CoordinateFilterChainDevice()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            ViewportRepository.setDevices(newDevices = listOf(original))
            filter.state.value = CoordinateFilterChainDeviceState(filters = listOf(0 to 4, 30 to 30))
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertEquals(expected = setOf(30 to 30), actual = filter.resolvedFilters())
            UndoManager.undo()
            assertEquals(expected = setOf(0 to 4, 30 to 30), actual = filter.resolvedFilters())
            filter.state.value = CoordinateFilterChainDeviceState(
                filters = listOf(30 to 30),
                padFilters = listOf(LaunchpadPadFilter(launchpadId = "target", localX = 1, localY = 8)),
            )
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertEquals(expected = setOf(0 to 8), actual = filter.resolvedFilters())
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun neighboringLegacyGlobalFiltersCanStillBeToggledAfterReplacingAnotherDevice() {
        WorkspaceRepository.clean()
        val filter = CoordinateFilterChainDevice()
        try {
            val original = ViewportLaunchpadPro().apply { launchpadId = "target" }
            val neighbor = ViewportLaunchpadPro().apply {
                launchpadId = "neighbor"
                position.value = Offset(x = 10f, y = 0f)
            }
            ViewportRepository.setDevices(newDevices = listOf(original, neighbor))
            filter.state.value = CoordinateFilterChainDeviceState(filters = listOf(1 to 8, 11 to 8))
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadX())
            assertEquals(expected = setOf(0 to 8, 11 to 8), actual = filter.resolvedFilters())
            filter.onSetKeyFilter(device = neighbor, localX = 1, localY = 8)
            assertEquals(expected = setOf(0 to 8), actual = filter.resolvedFilters())
            filter.onSetKeyFilter(device = neighbor, localX = 1, localY = 8)
            assertEquals(expected = setOf(0 to 8, 11 to 8), actual = filter.resolvedFilters())
            assertTrue(actual = filter.state.value.globalFilters.isEmpty())
        } finally {
            WorkspaceRepository.clean()
        }
    }

    @Test
    fun timelineEffectsRemapSourceAndProcessorsWhilePreservingClipAndChainIds() {
        WorkspaceRepository.clean()
        try {
            val original = ViewportLaunchpadX().apply { launchpadId = "target" }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val filterState = CoordinateFilterChainDeviceState(
                padFilters = listOf(
                    LaunchpadPadFilter(launchpadId = "target", localX = 0, localY = 8),
                ),
            )
            val source = KeyframesChainDeviceState(
                frames = listOf(
                    Frame(
                        timing = Timing.Duration(duration = 100.milliseconds),
                        entries = listOf(
                            KeyframesEntry(
                                x = 0,
                                y = 8,
                                r = 1f,
                                g = 0f,
                                b = 0f,
                                launchpadId = "target",
                                localX = 0,
                                localY = 8,
                            ),
                        ),
                    ),
                ),
            )
            val entry = ChainEffectEntry(
                clipId = "clip-id",
                startTimeMs = 0,
                durationMs = 100,
                source = source,
                processors = StateChain(devices = listOf(filterState), deviceIds = listOf("filter-id")),
            )
            val track = MidiTimelineTrack().apply { chainEffectEntries[0] = entry }
            TimelineRepository.addTrack(track = track)
            assertTrue(
                actual = WorkspaceRepository.replaceVirtualDevice(
                    deviceId = "target",
                    replacement = ViewportLaunchpadPro(),
                ),
            )
            val swappedTrack = TimelineRepository.tracks.value.single() as MidiTimelineTrack
            val swapped = swappedTrack.chainEffectEntries.getValue(0)
            assertEquals(expected = track.trackId, actual = swappedTrack.trackId)
            assertEquals(expected = entry.clipId, actual = swapped.clipId)
            assertEquals(expected = entry.processors.deviceIds, actual = swapped.processors.deviceIds)
            val filter = swapped.processors.devices.single() as CoordinateFilterChainDeviceState
            assertEquals(expected = 1 to 8, actual = filter.padFilters.single().let { it.localX to it.localY })
            val swappedSource = swapped.source as KeyframesChainDeviceState
            val swappedEntry = swappedSource.frames.single().entries.single()
            assertEquals(expected = 1 to 8, actual = swappedEntry.localX to swappedEntry.localY)
            val runtime = TimelineRepository.chainEffectRuntime(clipId = "clip-id")!!
            val runtimeFilter = runtime.processors.devices.value.single() as CoordinateFilterChainDevice
            assertEquals(expected = filter, actual = runtimeFilter.state.value)
            UndoManager.undo()
            val restoredTrack = TimelineRepository.tracks.value.single() as MidiTimelineTrack
            val restoredEntry = restoredTrack.chainEffectEntries.getValue(key = 0)
            assertEquals(expected = filterState, actual = restoredEntry.processors.devices.single())
        } finally {
            WorkspaceRepository.clean()
        }
    }
}
