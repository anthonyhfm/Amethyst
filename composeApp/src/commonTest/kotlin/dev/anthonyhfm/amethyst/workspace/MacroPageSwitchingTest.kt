package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.currentSignalMacroValues
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDevice
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.Macro
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MacroPageSwitchingTest {

    @BeforeTest
    fun setup() {
        WorkspaceRepository.setMacros(listOf(Macro(id = "macro-0", value = 0)), undoable = false)
    }

    @AfterTest
    fun tearDown() {
        WorkspaceRepository.setMacros(listOf(Macro(id = "macro-0", value = 0)), undoable = false)
    }

    @Test
    fun effectOnPageATriggersWhenSameButtonSwitchesToPageBInSameRack() {
        val page1Signals = mutableListOf<Signal>()
        val page2Signals = mutableListOf<Signal>()

        val page1Filter = MacroFilterChainDevice().apply {
            state.value = MacroFilterChainDeviceState(macro = 0, allowedValues = setOf(0))
        }
        val page1Chain = Chain().apply {
            devices.value = listOf(page1Filter)
            reroute()
            signalExit = { page1Signals.addAll(it) }
        }

        val page2Filter = MacroFilterChainDevice().apply {
            state.value = MacroFilterChainDeviceState(macro = 0, allowedValues = setOf(1))
        }
        val page2Chain = Chain().apply {
            devices.value = listOf(page2Filter)
            reroute()
            signalExit = { page2Signals.addAll(it) }
        }

        val pageSwitcher = MacroControlChainDevice().apply {
            state.value = MacroControlChainDeviceState(macro = 0, value = 1, macroId = "macro-0")
        }
        val pageSwitchChain = Chain().apply {
            devices.value = listOf(pageSwitcher)
            reroute()
        }

        val groupDevice = GroupChainDevice().apply {
            state.value = GroupChainDeviceState(
                groups = listOf(
                    Group(name = "Page 1 Effect", stateChain = StateChain()).apply {
                        chain.devices.value = page1Chain.devices.value
                        chain.reroute()
                        chain.signalExit = page1Chain.signalExit
                    },
                    Group(name = "Page 2 Effect", stateChain = StateChain()).apply {
                        chain.devices.value = page2Chain.devices.value
                        chain.reroute()
                        chain.signalExit = page2Chain.signalExit
                    },
                    // Page Switching is placed at the end of the groups
                    Group(name = "Page Switching", stateChain = StateChain()).apply {
                        chain.devices.value = pageSwitchChain.devices.value
                        chain.reroute()
                    }
                )
            )
        }

        val macroSnapshot = currentSignalMacroValues()
        assertEquals(listOf(0), macroSnapshot)

        val inputSignals = listOf(
            Signal.LED(
                origin = null,
                x = 9,
                y = 2,
                color = Color.White,
                layer = 0,
                macroValues = macroSnapshot,
            )
        )

        groupDevice.signalEnter(inputSignals)

        // Page 1 effect should have triggered
        assertEquals(1, page1Signals.size, "Page 1 effect should have triggered")
        // Page 2 effect should NOT have triggered
        assertEquals(0, page2Signals.size, "Page 2 effect should not have triggered")
        // Macro 0 should now be switched to 1 (Page 2)
        assertEquals(1, WorkspaceRepository.macros.value[0].value, "Macro 0 should be switched to Page 2")
    }

    @Test
    fun crossChainPageSwitchingPreservesPageAEffectOnOtherChain() {
        val lightsSignals = mutableListOf<Signal>()

        // lightsChain has a Page 1 effect (active when macro 0 == 0)
        val lightsFilter = MacroFilterChainDevice().apply {
            state.value = MacroFilterChainDeviceState(macro = 0, allowedValues = setOf(0))
        }
        val lightsChain = Chain().apply {
            devices.value = listOf(lightsFilter)
            reroute()
            signalExit = { lightsSignals.addAll(it) }
        }

        // samplingChain has Page Switching to Page 2 (value 1)
        val pageSwitcher = MacroControlChainDevice().apply {
            state.value = MacroControlChainDeviceState(macro = 0, value = 1, macroId = "macro-0")
        }
        val samplingChain = Chain().apply {
            devices.value = listOf(pageSwitcher)
            reroute()
        }

        // Pad press on (9, 2) while on Page 1 (macro 0 == 0)
        val macroSnapshot = currentSignalMacroValues()
        assertEquals(listOf(0), macroSnapshot)

        val midiSignals = listOf(
            Signal.Midi(
                origin = null,
                x = 9,
                y = 2,
                velocity = 127,
                macroValues = macroSnapshot,
            )
        )
        val ledSignals = listOf(
            Signal.LED(
                origin = null,
                x = 9,
                y = 2,
                color = Color.White,
                layer = 0,
                macroValues = macroSnapshot,
            )
        )

        // As in AmethystMidiManager: samplingChain runs first, then lightsChain
        samplingChain.signalEnter(midiSignals)
        assertEquals(1, WorkspaceRepository.macros.value[0].value, "Sampling chain switched macro to 1")

        lightsChain.signalEnter(ledSignals)
        // Lights effect on Page 1 must still trigger because ledSignals captured macroSnapshot == [0]
        assertEquals(1, lightsSignals.size, "Lights effect on Page 1 should have received signal")
    }

    @Test
    fun abletonAdaptersPlacePageSwitchingAtEndOfGroups() {
        val branch = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch(
            id = 0,
            name = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.Name(
                effectiveName = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.Name.EffectiveName("Branch 0")
            ),
            deviceChain = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain(
                deviceChain = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain(
                    devices = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain.Devices(emptyList())
                )
            ),
            zoneSettings = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings(
                keyRange = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange(
                    min = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(0),
                    max = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(127),
                )
            ),
            branchSelectorRange = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange(
                min = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(1),
                max = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(1),
            ),
            masterDevice = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice(
                speaker = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice.Speaker(
                    dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual(true)
                )
            )
        )
        val instrumentContainer = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice(
            id = 1,
            on = dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn(manual = dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual(true)),
            chainSelector = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.ChainSelector(
                keyMidi = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.ChainSelector.KeyMidi(),
            ),
            branches = dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice.Branches(listOf(branch))
        )
        dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter.launchpadLayout =
            dev.anthonyhfm.amethyst.conversion.ableton.AbletonLaunchpadLayout.create(1)
        val adapter = dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.InstrumentGroupAdapter(
            device = instrumentContainer,
            offset = androidx.compose.ui.unit.IntOffset.Zero,
            outputOffset = androidx.compose.ui.unit.IntOffset.Zero,
            chainDepth = 0,
        )
        val result = adapter.toDeviceStates()
        val groupState = kotlin.test.assertIs<GroupChainDeviceState>(result.first())
        assertEquals("Branch 0", groupState.groups.first().name)
        assertEquals("Page Switching", groupState.groups.last().name)
    }

    @Test
    fun keyframesRenderAnimationPreservesEntriesEvenWithoutMappedDevices() {
        val entry = dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry(
            x = 4,
            y = 8,
            r = 1f,
            g = 1f,
            b = 1f,
            launchpadId = "unmapped-uuid",
            localX = 4,
            localY = 8,
        )
        val frame = dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame(
            timing = dev.anthonyhfm.amethyst.core.util.Timing.Rythm(dev.anthonyhfm.amethyst.core.util.Timing.Rythm.RythmTiming._1_16),
            entries = listOf(entry),
        )
        val kf = dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice().apply {
            state.value = dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState(
                frames = listOf(frame),
            )
            renderAnimation()
        }

        val rendered = kf.state.value.renderedAnimation
        val nonEmpties = rendered.filter { it.second.isNotEmpty() }
        kotlin.test.assertTrue(nonEmpties.isNotEmpty(), "Rendered animation must have non-empty signals")
        val signal = nonEmpties.first().second.first() as Signal.LED
        assertEquals(4, signal.x)
        assertEquals(8, signal.y)
        assertEquals(Color.White, signal.color)
    }

    @Test
    fun stateOfMindDebug() {
        val alsPath = "/Users/anthony/Downloads/Teminite - State Of Mind/State Of Mind.als"
        if (!java.io.File(alsPath).exists()) return
        val bytes = java.io.File(alsPath).readBytes()
        val abletonData = dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter.decodeAbletonAls(bytes)
        val layout = dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayoutDetector.detectLayout(abletonData.liveSet.tracks.midiTracks)
        dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter.launchpadLayout =
            dev.anthonyhfm.amethyst.conversion.ableton.AbletonLaunchpadLayout.create(1)
        val bpm = abletonData.liveSet.masterTrack.deviceChain.mixer.tempo.manual.value
        dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter.bpm = bpm
        println("BPM: $bpm")
        val macroActions = dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonTutorialDetector.detectMacroAutomationActions(
            layout = layout,
            tracks = abletonData.liveSet.tracks.midiTracks,
            tutorialStartBeats = 0.0,
        )
        println("=== detectMacroAutomationActions (${macroActions.size} times) ===")
        macroActions.entries.sortedBy { it.key }.forEach { (time, acts) ->
            println("Time $time ms: $acts")
        }
        val autoPlayData = dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonTutorialDetector.getAutoPlayData(layout, abletonData.liveSet.tracks.midiTracks)
        println("autoPlay actions count: ${autoPlayData.actions.size}")
        autoPlayData.actions.entries.sortedBy { it.key }.filter { it.key <= 12400.0 }.forEach { (time, acts) ->
            println("Action at $time ms: $acts")
        }


        val platformFile = io.github.vinceglb.filekit.PlatformFile(alsPath)
        dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter.file = platformFile
        println("Layout: $layout")
        val singleLayout = layout as? dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout.Single
        val lightsTrack = singleLayout?.lightsTrack
        val audioTrack = singleLayout?.audioTrack
        println("lightsTrack: ${lightsTrack?.name}, audioTrack: ${audioTrack?.name}")

        val lightsStateChain = dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader(androidx.compose.ui.unit.IntOffset.Zero).readMidiChain(lightsTrack!!)
        val lightsChain = lightsStateChain.unpack()
        val rootGroupForTest = lightsChain.devices.value.first() as GroupChainDevice
        var printBranchEmissions = false
        rootGroupForTest.state.value.groups.forEachIndexed { idx, grp ->
            val origExit = grp.chain.signalExit
            grp.chain.signalExit = { signals ->
                if (printBranchEmissions) {
                    println("  [Branch $idx '${grp.name}'] emitted ${signals.size} signals: $signals")
                }
                origExit?.invoke(signals)
            }
        }
        val kf = rootGroupForTest.state.value.groups[36].chain.devices.value
            .filterIsInstance<dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice>().first()
        println("KF frames count: ${kf.state.value.frames.size}")
        println("KF renderedAnimation events: ${kf.state.value.renderedAnimation.size}")
        println("KF totalDuration: ${kf.state.value.renderedAnimation.lastOrNull()?.first}")
        println("KF playbackMode: ${kf.state.value.playbackMode}")
        println("KF isolate: ${kf.state.value.isolate}")
        println("KF rootKey: ${kf.state.value.rootKey}")
        println("KF repeats: ${kf.state.value.repeats}")
        println("Branch 121 devices: " + rootGroupForTest.state.value.groups[121].chain.devices.value.map { it::class.simpleName to it.state.value })
        var b121Called = false
        val testSig = listOf(Signal.LED(origin = null, x = 6, y = 7, color = Color.White, macroValues = listOf(0)))
        val oldExit = rootGroupForTest.state.value.groups[121].chain.signalExit
        rootGroupForTest.state.value.groups[121].chain.signalExit = { b121Called = true }
        rootGroupForTest.state.value.groups[121].chain.signalEnter(testSig)
        println("DIRECT TEST Branch 121 called with macroValues=[0]: $b121Called")
        rootGroupForTest.state.value.groups[121].chain.signalExit = oldExit
        val lightsExits = mutableListOf<Signal>()
        lightsChain.signalExit = {
            lightsExits.addAll(it)
            println(">>> lightsChain emitted ${it.size} signals: ${it.take(5)}")
        }

        fun findMacroControls(chain: Chain, path: String) {
            chain.devices.value.forEachIndexed { idx, dev ->
                if (dev is MacroControlChainDevice) {
                    println("Found MacroControl at $path -> device[$idx]: macro=${dev.state.value.macro}, value=${dev.state.value.value}")
                }
                if (dev is GroupChainDevice) {
                    dev.state.value.groups.forEach { grp ->
                        findMacroControls(grp.chain, "$path -> Group(${grp.name})")
                    }
                }
            }
        }
        println("--- MacroControls in lightsChain ---")
        findMacroControls(lightsChain, "lights")

        val audioChainState = dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader(androidx.compose.ui.unit.IntOffset.Zero).readMidiChain(audioTrack!!)
        val audioChain = audioChainState.unpack()
        println("--- MacroControls in audioChain ---")
        fun findMacroControlsWithCoords(chain: Chain, path: String, currentCoords: List<Pair<Int, Int>>) {
            var coords = currentCoords
            chain.devices.value.forEachIndexed { idx, dev ->
                if (dev is dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice) {
                    coords = dev.state.value.filters.toList()
                }
                if (dev is MacroControlChainDevice) {
                    println("Found MacroControl at $path -> device[$idx]: macro=${dev.state.value.macro}, value=${dev.state.value.value}, coords=$coords")
                }
                if (dev is GroupChainDevice) {
                    dev.state.value.groups.forEach { grp ->
                        findMacroControlsWithCoords(grp.chain, "$path -> Group(${grp.name})", coords)
                    }
                }
            }
        }
        findMacroControlsWithCoords(audioChain, "audio", emptyList())



        fun inspectChain(chain: Chain, indent: String = "") {
            chain.devices.value.forEachIndexed { idx, dev ->
                println("$indent[$idx] ${dev::class.simpleName} (state=${dev.state.value})")
                if (dev is GroupChainDevice) {
                    dev.state.value.groups.forEachIndexed { gIdx, grp ->
                        println("$indent  Group $gIdx: '${grp.name}'")
                        inspectChain(grp.chain, "$indent    ")
                    }
                }
            }
        }
        val rootGroup = lightsChain.devices.value.first() as GroupChainDevice
        println("=== Branches in lightsChain matching (6, 7) ===")
        rootGroup.state.value.groups.forEachIndexed { idx, grp ->
            var matches67 = false
            fun checkChain(c: Chain) {
                c.devices.value.forEach { d ->
                    if (d is dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice && d.state.value.filters.contains(Pair(6, 7))) {
                        matches67 = true
                    }
                    if (d is GroupChainDevice) {
                        d.state.value.groups.forEach { checkChain(it.chain) }
                    }
                }
            }
            checkChain(grp.chain)
            if (matches67) {
        println("=== Page 1 (macro 0 == 0) branches in lightsChain ===")
        rootGroup.state.value.groups.forEachIndexed { idx, grp ->
            val macroFilters = grp.chain.devices.value.filterIsInstance<MacroFilterChainDevice>().map { it.state.value.allowedValues }
            if (macroFilters.any { it.contains(0) }) {
                val coords = mutableListOf<Pair<Int, Int>>()
                grp.chain.devices.value.filterIsInstance<dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice>().forEach {
                    coords.addAll(it.state.value.filters)
                    coords.addAll(it.state.value.padFilters.map { p -> Pair(p.localX, p.localY) })
                }
                val deviceNames = grp.chain.devices.value.map { it::class.simpleName }
                println("Branch $idx: '${grp.name}', coords=$coords, devices=$deviceNames")
            }
        }
            }
        }


        println("--- Simulating AutoPlay actions from 0 to 12400 ms ---")
        WorkspaceRepository.setMacros(listOf(Macro(id = "macro-0", value = 0)), undoable = false)

        val sortedActions = autoPlayData.actions.entries.sortedBy { it.key }
        for ((time, actions) in sortedActions) {
            if (time > 13500.0) break
            val macroBefore = currentSignalMacroValues()
            if (time >= 12390.0) {
                printBranchEmissions = true
                println("At time $time ms, macroBefore=$macroBefore, actions=$actions")
            }
            val macroSnapshot = currentSignalMacroValues()
            val midiSignals = actions.map {
                Signal.Midi(origin = null, x = it.x, y = it.y, velocity = if (it.down) 127 else 0, macroValues = macroSnapshot)
            }
            val ledSignals = actions.map {
                Signal.LED(origin = null, x = it.x, y = it.y, color = if (it.down) Color.White else Color.Black, macroValues = macroSnapshot)
            }
            audioChain.signalEnter(midiSignals)
            val macroAfterSampling = currentSignalMacroValues()
            if (time >= 12390.0) {
                println("  After sampling: macro=$macroAfterSampling")
            }
            lightsChain.signalEnter(ledSignals)
            val macroAfterLights = currentSignalMacroValues()
            if (time >= 12390.0) {
                println("  After lights: macro=$macroAfterLights")
            }
        }







    }
}
