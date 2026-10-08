package dev.anthonyhfm.amethyst.conversion.ableton.adapters

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.RandomDeviceMultisamplingAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.AbletonRackVelocityRange
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiPitcher
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceBlobSlot
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceFileDropList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceParameterList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevicePatchSlot
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ableton.AbletonNoteSpace
import dev.anthonyhfm.amethyst.devices.ableton.AbletonVelocityChainDevice
import dev.anthonyhfm.amethyst.devices.ableton.AbletonVelocityChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.StereoGainChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AbletonMultisamplingZonesTest {
    @Test
    fun readsActualRackSelectorVelocityAndDrumSendingNoteXml() {
        val selector = AbletonConverter.xml.decodeFromString(
            deserializer = InstrumentGroupDevice.ChainSelector.serializer(),
            string = """<ChainSelector><Manual Value="2"/></ChainSelector>""",
        )
        val zones = AbletonConverter.xml.decodeFromString(
            deserializer = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.serializer(),
            string = """<ZoneSettings><KeyRange><Min Value="62"/><Max Value="62"/></KeyRange><VelocityRange><Min Value="64"/><Max Value="127"/></VelocityRange></ZoneSettings>""",
        )
        val drum = AbletonConverter.xml.decodeFromString(
            deserializer = DrumGroupDevice.Branches.DrumBranch.BranchInfo.serializer(),
            string = """<BranchInfo><ReceivingNote Value="48"/><SendingNote Value="61"/><ChokeGroup Value="1"/></BranchInfo>""",
        )

        assertEquals(expected = 2, actual = selector.manual.value)
        assertEquals(expected = 62, actual = zones.keyRange.min.value)
        assertEquals(expected = 64, actual = zones.velocityRange.min.value)
        assertEquals(expected = 127, actual = zones.velocityRange.max.value)
        assertEquals(expected = 48, actual = drum.receivingNote.value)
        assertEquals(expected = 61, actual = drum.sendingNote.value)
        assertEquals(expected = 1, actual = drum.chokeGroup.value)
    }

    @Test
    fun wannaCryFiveStepsKeepBothLayersAtNote62AndTheFinalMainSample() {
        val result = outbreak(rack = wannaCryRack(), steps = 5)

        assertEquals(
            expected = listOf("Vox", "Vox", "Vox + Main", "Main", "Main"),
            actual = result.groups.map { group -> group.name },
        )
        val layers = assertIs<GroupChainDeviceState>(value = result.groups[2].stateChain.devices.last())
        assertEquals(expected = listOf("Vox", "Main"), actual = layers.groups.map { group -> group.name })
        assertEquals(expected = 5, actual = result.groups.size)
    }

    @Test
    fun keyZoneOrderWidthsAndGapsDoNotChangeTheSequence() {
        val result = outbreak(
            rack = rack(
                branches = listOf(
                    branch(id = 3, name = "Last", minimum = 64),
                    branch(id = 2, name = "Wide", minimum = 60, maximum = 62),
                    branch(id = 1, name = "Layer", minimum = 61),
                ),
            ),
            steps = 5,
        )

        assertEquals(
            expected = listOf("Wide", "Wide + Layer", "Wide", "Step 4", "Last"),
            actual = result.groups.map { group -> group.name },
        )
        assertTrue(actual = result.groups[3].stateChain.devices.isEmpty())
    }

    @Test
    fun selectorModeKeepsOverlappingChainsAndNoteModeUsesTheStoredSelector() {
        val branches = listOf(
            branch(id = 1, name = "First", minimum = 0, maximum = 127, selectorMinimum = 0, selectorMaximum = 1),
            branch(id = 2, name = "Second", minimum = 0, maximum = 127, selectorMinimum = 1, selectorMaximum = 2),
        )
        val selectorMode = outbreak(rack = rack(branches = branches), steps = 3, macroMode = true)
        assertEquals(
            expected = listOf("First", "First + Second", "Second"),
            actual = selectorMode.groups.map { group -> group.name },
        )
        val noteMode = outbreak(rack = rack(branches = branches, selector = 2), steps = 1)
        assertEquals(expected = "Second", actual = noteMode.groups.single().name)
    }

    @Test
    fun innerBranchGainAndSpeakerStateArePreservedByBothAdapters() {
        val source = rack(
            branches = listOf(
                branch(id = 1, name = "Attenuated", minimum = 60, volume = 0.5f),
                branch(id = 2, name = "Muted", minimum = 61, enabled = false),
            ),
        )
        val results = listOf(outbreak(rack = source, steps = 2), random(rack = source, steps = 2))
        results.forEach { result ->
            val attenuated = assertIs<StereoGainChainDeviceState>(value = result.groups[0].stateChain.devices.last())
            val muted = assertIs<StereoGainChainDeviceState>(value = result.groups[1].stateChain.devices.last())
            assertTrue(actual = abs(x = attenuated.gainDb - -6.0206f) < 0.0001f)
            assertTrue(actual = muted.muted)
        }
    }

    @Test
    fun randomAlternateUsesActualKeyZonesIncludingOverlapsAndScale() {
        val result = random(rack = wannaCryRack(), steps = 5)
        assertEquals(
            expected = listOf("Vox", "Vox", "Vox + Main", "Main", "Main"),
            actual = result.groups.map { group -> group.name },
        )
        val scaled = random(rack = wannaCryRack(), steps = 3, scale = 2.0)
        assertEquals(
            expected = listOf("Vox", "Vox + Main", "Main"),
            actual = scaled.groups.map { group -> group.name },
        )
    }

    @Test
    fun velocityZonePreservesAcceptedVelocityAndOnlyReleasesForwardedNotes() {
        val source = rack(
            branches = listOf(
                branch(id = 1, name = "Soft", minimum = 60, velocityMinimum = 1, velocityMaximum = 63),
                branch(id = 2, name = "Hard", minimum = 60, velocityMinimum = 64, velocityMaximum = 127),
            ),
        )
        val step = outbreak(rack = source, steps = 1).groups.single()
        val layers = assertIs<GroupChainDeviceState>(value = step.stateChain.devices.single())
        layers.groups.forEach { group ->
            val filterState = assertIs<AbletonVelocityChainDeviceState>(value = group.stateChain.devices.filterIsInstance<AbletonVelocityChainDeviceState>().single())
            val output = mutableListOf<Int>()
            val filter = AbletonVelocityChainDevice().apply {
                state.value = filterState
                signalExit = { signals ->
                    output.addAll(elements = signals.filterIsInstance<Signal.Midi>().map { signal -> signal.velocity })
                }
            }
            listOf(40, 0, 90, 0).forEach { velocity ->
                filter.signalEnter(n = listOf(Signal.Midi(origin = "pad", x = 1, y = 1, velocity = velocity)))
            }
            assertEquals(
                expected = if (group.name == "Soft") {
                    listOf(40, 0)
                } else {
                    listOf(90, 0)
                },
                actual = output,
            )
        }
    }

    @Test
    fun compiledOverlapRoutesPressReleaseAndResetWithoutChangingTheHeldStep() {
        fun verify(inputNote: Int?) {
            val saved = outbreak(rack = wannaCryRack(), steps = 5, inputNote = inputNote)
            val multi = MultiGroupChainDevice().apply {
                loadFromState(savedState = saved)
            }
            val events = mutableListOf<Triple<Int, String, Int>>()
            multi.state.value.groups.forEachIndexed { step, group ->
                val parallel = group.chain.devices.value.filterIsInstance<GroupChainDevice>().singleOrNull()
                if (parallel == null) {
                    group.chain.signalExit = { signals ->
                        signals.filterIsInstance<Signal.Midi>().forEach { signal ->
                            assertEquals(expected = 60, actual = AbletonNoteSpace.note(signal = signal)?.pitch)
                            events.add(element = Triple(first = step, second = group.name, third = signal.velocity))
                        }
                    }
                } else {
                    parallel.state.value.groups.forEach { layer ->
                        layer.chain.signalExit = { signals ->
                            signals.filterIsInstance<Signal.Midi>().forEach { signal ->
                                assertEquals(expected = 60, actual = AbletonNoteSpace.note(signal = signal)?.pitch)
                                events.add(element = Triple(first = step, second = layer.name, third = signal.velocity))
                            }
                        }
                    }
                }
            }
            fun send(velocity: Int) {
                val input = Signal.Midi(origin = "pad", x = 1, y = 2, velocity = velocity)
                val note = AbletonNoteSpace.Note(pitch = 60, targetX = 0, targetY = 0)
                val signal = AbletonNoteSpace.withPitch(signal = input, note = note, pitch = 60) ?: return
                multi.signalEnter(n = listOf(signal))
            }
            repeat(times = 2) {
                send(velocity = 127)
                send(velocity = 0)
            }
            send(velocity = 127)
            multi.state.value = multi.state.value.copy(currentMultiIndex = 0)
            send(velocity = 0)
            send(velocity = 127)
            send(velocity = 0)

            assertEquals(
                expected = listOf(
                    Triple(first = 0, second = "Vox", third = 127), Triple(first = 0, second = "Vox", third = 0),
                    Triple(first = 1, second = "Vox", third = 127), Triple(first = 1, second = "Vox", third = 0),
                    Triple(first = 2, second = "Vox", third = 127), Triple(first = 2, second = "Main", third = 127),
                    Triple(first = 2, second = "Vox", third = 0), Triple(first = 2, second = "Main", third = 0),
                    Triple(first = 0, second = "Vox", third = 127), Triple(first = 0, second = "Vox", third = 0),
                ),
                actual = events,
            )
        }

        verify(inputNote = 60)
        verify(inputNote = null)
    }

    private fun wannaCryRack(): InstrumentGroupDevice = rack(
        branches = listOf(
            branch(id = 6, name = "Vox", minimum = 60),
            branch(id = 8, name = "Vox", minimum = 61),
            branch(id = 1, name = "Vox", minimum = 62),
            branch(id = 2, name = "Main", minimum = 62),
            branch(id = 3, name = "Main", minimum = 63),
            branch(id = 4, name = "Main", minimum = 64),
        ),
    )

    private fun rack(
        branches: List<InstrumentGroupDevice.Branches.InstrumentBranch>,
        selector: Int = 0,
    ): InstrumentGroupDevice = InstrumentGroupDevice(
        id = 1,
        branches = InstrumentGroupDevice.Branches(branches = branches),
        chainSelector = InstrumentGroupDevice.ChainSelector(manual = AbletonManual(value = selector)),
    )

    private fun branch(
        id: Int,
        name: String,
        minimum: Int,
        maximum: Int = minimum,
        selectorMinimum: Int = 0,
        selectorMaximum: Int = 0,
        volume: Float = 1f,
        enabled: Boolean = true,
        velocityMinimum: Int = 1,
        velocityMaximum: Int = 127,
    ): InstrumentGroupDevice.Branches.InstrumentBranch {
        val simpler = OriginalSimpler(
            player = OriginalSimpler.Player(
                multiSampleMap = OriginalSimpler.Player.MultiSampleMap(
                    sampleParts = OriginalSimpler.Player.MultiSampleMap.SampleParts(),
                ),
            ),
            volumeAndPan = OriginalSimpler.VolumeAndPan(),
        )
        return InstrumentGroupDevice.Branches.InstrumentBranch(
            id = id,
            name = InstrumentGroupDevice.Branches.InstrumentBranch.Name(
                effectiveName = InstrumentGroupDevice.Branches.InstrumentBranch.Name.EffectiveName(value = name),
            ),
            deviceChain = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain(
                deviceChain = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain(
                    devices = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain.Devices(
                        devices = listOf(
                            MidiPitcher(id = 1, pitch = MidiPitcher.Pitch(manual = AbletonManual(value = 60 - minimum))),
                            simpler,
                        ),
                    ),
                ),
            ),
            zoneSettings = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings(
                keyRange = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange(
                    min = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(value = minimum),
                    max = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(value = maximum),
                ),
                velocityRange = AbletonRackVelocityRange(
                    min = AbletonRackVelocityRange.Boundary(value = velocityMinimum),
                    max = AbletonRackVelocityRange.Boundary(value = velocityMaximum),
                ),
            ),
            branchSelectorRange = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange(
                min = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(value = selectorMinimum),
                max = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(value = selectorMaximum),
            ),
            masterDevice = InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice(
                speaker = InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice.Speaker(manual = AbletonManual(value = enabled)),
                volume = InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice.Volume(manual = AbletonManual(value = volume)),
            ),
        )
    }

    private fun outbreak(
        rack: InstrumentGroupDevice,
        steps: Int,
        macroMode: Boolean = false,
        inputNote: Int? = 60,
    ): MultiGroupChainDeviceState {
        val blob = """{"live.numbox":[$steps],"live.text":[${if (macroMode) { 1 } else { 0 }}]}"""
            .encodeToByteArray().joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0') }
        val device = MxDeviceMidiEffect(
            patchSlot = MxDevicePatchSlot(value = MxDevicePatchSlot.Value()),
            blobSlot = MxDeviceBlobSlot(
                value = MxDeviceBlobSlot.Value(
                    mxdBlob = MxDeviceBlobSlot.Value.MxDBlob(blob = MxDeviceBlobSlot.Value.MxDBlob.Blob(value = blob)),
                ),
            ),
            parameterList = MxDeviceParameterList(parameterList = MxDeviceParameterList.ParameterList(parameters = emptyList())),
            fileDropList = MxDeviceFileDropList(fileDropList = MxDeviceFileDropList.FileDropList(items = emptyList())),
        )
        return assertIs<MultiGroupChainDeviceState>(
            value = MultiAdapter(
                device = device,
                midiContainer = null,
                instrumentContainer = rack,
                drumContainer = null,
                offset = IntOffset.Zero,
                outputOffset = IntOffset.Zero,
                chainDepth = 0,
                inputNote = inputNote,
            ).toDeviceStates().single(),
        )
    }

    private fun random(rack: InstrumentGroupDevice, steps: Int, scale: Double = 1.0): MultiGroupChainDeviceState {
        return assertIs<MultiGroupChainDeviceState>(
            value = RandomDeviceMultisamplingAdapter(
                random = MidiRandom(
                    id = 1,
                    chance = MidiRandom.Chance(manual = AbletonManual(value = 1.0)),
                    choices = MidiRandom.Choices(manual = AbletonManual(value = steps.toDouble())),
                    scale = MidiRandom.Scale(manual = AbletonManual(value = scale)),
                    alternate = MidiRandom.Alternate(manual = AbletonManual(value = true)),
                ),
                midiContainer = null,
                instrumentContainer = rack,
                drumContainer = null,
                inputNote = 60,
            ).toDeviceStates().single(),
        )
    }
}
