package dev.anthonyhfm.amethyst.conversion.ableton.adapters

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceBlobSlot
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceFileDropList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceParameterList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevicePatchSlot
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MultiAdapterTest {

    private fun stringToHex(s: String): String =
        s.toByteArray().joinToString("") { "%02x".format(it) }

    private fun createMxDevice(steps: Int, isMacro: Boolean = false): MxDeviceMidiEffect {
        val json = if (isMacro) {
            """{"live.numbox":[$steps.0],"live.text":[1.0]}"""
        } else {
            """{"live.numbox":[$steps.0],"live.text":[0.0]}"""
        }
        val hex = stringToHex(json)
        return MxDeviceMidiEffect(
            id = 1,
            on = AbletonOn(manual = AbletonManual(true)),
            patchSlot = MxDevicePatchSlot(MxDevicePatchSlot.Value(null)),
            blobSlot = MxDeviceBlobSlot(
                MxDeviceBlobSlot.Value(
                    MxDeviceBlobSlot.Value.MxDBlob(
                        blob = MxDeviceBlobSlot.Value.MxDBlob.Blob(hex)
                    )
                )
            ),
            parameterList = MxDeviceParameterList(MxDeviceParameterList.ParameterList(emptyList())),
            fileDropList = MxDeviceFileDropList(MxDeviceFileDropList.FileDropList(emptyList())),
        )
    }

    private fun createSimpler(transposeSemitones: Float): OriginalSimpler {
        return OriginalSimpler(
            id = 10,
            on = AbletonOn(manual = AbletonManual(true)),
            pitch = OriginalSimpler.Pitch(
                transposeKey = OriginalSimpler.Pitch.TransposeData(
                    manual = AbletonManual(transposeSemitones)
                )
            ),
            volumeAndPan = OriginalSimpler.VolumeAndPan(),
            player = OriginalSimpler.Player(
                multiSampleMap = OriginalSimpler.Player.MultiSampleMap(
                    sampleParts = OriginalSimpler.Player.MultiSampleMap.SampleParts(
                        multiSamplePart = null
                    )
                )
            )
        )
    }

    private fun createInstrumentBranch(
        id: Int,
        minKey: Int,
        maxKey: Int,
        transpose: Float,
    ): InstrumentGroupDevice.Branches.InstrumentBranch {
        return InstrumentGroupDevice.Branches.InstrumentBranch(
            id = id,
            name = InstrumentGroupDevice.Branches.InstrumentBranch.Name(
                effectiveName = InstrumentGroupDevice.Branches.InstrumentBranch.Name.EffectiveName("Step $id")
            ),
            deviceChain = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain(
                deviceChain = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain(
                    devices = InstrumentGroupDevice.Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain.Devices(
                        devices = listOf(createSimpler(transpose))
                    )
                )
            ),
            zoneSettings = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings(
                keyRange = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange(
                    min = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(minKey),
                    max = InstrumentGroupDevice.Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax(maxKey),
                )
            ),
            branchSelectorRange = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange(
                min = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(id),
                max = InstrumentGroupDevice.Branches.InstrumentBranch.BranchSelectorRange.MinMax(id),
            ),
            masterDevice = InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice(
                speaker = InstrumentGroupDevice.Branches.InstrumentBranch.MixerDevice.Speaker(AbletonManual(true))
            )
        )
    }

    @Test
    fun multiAdapterCompensatesDownpitchedSamplesAcrossSteps() {
        val mxDevice = createMxDevice(steps = 3)
        val instrumentContainer = InstrumentGroupDevice(
            id = 2,
            on = AbletonOn(manual = AbletonManual(true)),
            chainSelector = InstrumentGroupDevice.ChainSelector(),
            branches = InstrumentGroupDevice.Branches(
                branches = listOf(
                    // In Ableton Live, each subsequent branch is keyed higher (+1, +2) and transposed down (0, -1, -2)
                    createInstrumentBranch(id = 0, minKey = 36, maxKey = 36, transpose = 0f),
                    createInstrumentBranch(id = 1, minKey = 37, maxKey = 37, transpose = -1f),
                    createInstrumentBranch(id = 2, minKey = 38, maxKey = 38, transpose = -2f),
                )
            )
        )

        val adapter = MultiAdapter(
            device = mxDevice,
            midiContainer = null,
            instrumentContainer = instrumentContainer,
            drumContainer = null,
            offset = IntOffset.Zero,
            outputOffset = IntOffset.Zero,
            chainDepth = 0,
        )

        val result = adapter.toDeviceStates()
        assertEquals(1, result.size)
        val multiState = assertIs<MultiGroupChainDeviceState>(result.first())
        assertEquals(3, multiState.groups.size)

        // Verify that all 3 steps now have transposeSemitones = 0f
        val step0Sample = assertIs<SampleChainDeviceState>(multiState.groups[0].stateChain.devices.first())
        val step1Sample = assertIs<SampleChainDeviceState>(multiState.groups[1].stateChain.devices.first())
        val step2Sample = assertIs<SampleChainDeviceState>(multiState.groups[2].stateChain.devices.first())

        assertEquals(0f, step0Sample.transposeSemitones, "Step 0 transpose")
        assertEquals(0f, step1Sample.transposeSemitones, "Step 1 transpose compensated from -1f")
        assertEquals(0f, step2Sample.transposeSemitones, "Step 2 transpose compensated from -2f")
    }

    @Test
    fun multiAdapterPreservesBaseTransposeOffsetAcrossSteps() {
        val mxDevice = createMxDevice(steps = 3)
        val instrumentContainer = InstrumentGroupDevice(
            id = 2,
            on = AbletonOn(manual = AbletonManual(true)),
            chainSelector = InstrumentGroupDevice.ChainSelector(),
            branches = listOf(
                // Sound designer wanted base transpose of +3f, so in Ableton: 3f, 2f, 1f
                createInstrumentBranch(id = 0, minKey = 36, maxKey = 36, transpose = 3f),
                createInstrumentBranch(id = 1, minKey = 37, maxKey = 37, transpose = 2f),
                createInstrumentBranch(id = 2, minKey = 38, maxKey = 38, transpose = 1f),
            ).let { InstrumentGroupDevice.Branches(it) }
        )

        val adapter = MultiAdapter(
            device = mxDevice,
            midiContainer = null,
            instrumentContainer = instrumentContainer,
            drumContainer = null,
            offset = IntOffset.Zero,
            outputOffset = IntOffset.Zero,
            chainDepth = 0,
        )

        val result = adapter.toDeviceStates()
        val multiState = assertIs<MultiGroupChainDeviceState>(result.first())

        val step0Sample = assertIs<SampleChainDeviceState>(multiState.groups[0].stateChain.devices.first())
        val step1Sample = assertIs<SampleChainDeviceState>(multiState.groups[1].stateChain.devices.first())
        val step2Sample = assertIs<SampleChainDeviceState>(multiState.groups[2].stateChain.devices.first())

        assertEquals(3f, step0Sample.transposeSemitones, "Step 0 preserves base transpose 3f")
        assertEquals(3f, step1Sample.transposeSemitones, "Step 1 preserves base transpose 3f")
        assertEquals(3f, step2Sample.transposeSemitones, "Step 2 preserves base transpose 3f")
    }

    @Test
    fun multiAdapterMacroModeDoesNotCompensatePitch() {
        val mxDevice = createMxDevice(steps = 2, isMacro = true)
        val instrumentContainer = InstrumentGroupDevice(
            id = 2,
            on = AbletonOn(manual = AbletonManual(true)),
            chainSelector = InstrumentGroupDevice.ChainSelector(),
            branches = listOf(
                createInstrumentBranch(id = 0, minKey = 0, maxKey = 127, transpose = -5f),
                createInstrumentBranch(id = 1, minKey = 0, maxKey = 127, transpose = -5f),
            ).let { InstrumentGroupDevice.Branches(it) }
        )

        val adapter = MultiAdapter(
            device = mxDevice,
            midiContainer = null,
            instrumentContainer = instrumentContainer,
            drumContainer = null,
            offset = IntOffset.Zero,
            outputOffset = IntOffset.Zero,
            chainDepth = 0,
        )

        val result = adapter.toDeviceStates()
        val multiState = assertIs<MultiGroupChainDeviceState>(result.first())

        val step0Sample = assertIs<SampleChainDeviceState>(multiState.groups[0].stateChain.devices.first())
        val step1Sample = assertIs<SampleChainDeviceState>(multiState.groups[1].stateChain.devices.first())

        // In macro mode, no pitch shift was applied by Outbreak Multi, so transpose remains as set
        assertEquals(-5f, step0Sample.transposeSemitones)
        assertEquals(-5f, step1Sample.transposeSemitones)
    }

    @Test
    fun pitchCompensationRecursesIntoNestedCompoundDevices() {
        val adapter = object : AbletonAdapter() {
            override fun toDeviceStates() = emptyList<dev.anthonyhfm.amethyst.devices.DeviceState>()
            fun testCompensate(state: dev.anthonyhfm.amethyst.devices.DeviceState, semitones: Float) =
                state.withPitchCompensation(semitones)
        }

        val nestedSample = SampleChainDeviceState(transposeSemitones = -2f)
        val groupDevice = GroupChainDeviceState(
            groups = listOf(
                Group(
                    name = "SubGroup",
                    stateChain = StateChain(devices = listOf(nestedSample))
                )
            )
        )
        val chokeDevice = ChokeChainDeviceState(
            stateChain = StateChain(devices = listOf(groupDevice))
        )

        val compensated = adapter.testCompensate(chokeDevice, 2f)
        val unwrappedChoke = assertIs<ChokeChainDeviceState>(compensated)
        val unwrappedGroup = assertIs<GroupChainDeviceState>(unwrappedChoke.stateChain.devices.first())
        val unwrappedSample = assertIs<SampleChainDeviceState>(unwrappedGroup.groups.first().stateChain.devices.first())

        assertEquals(0f, unwrappedSample.transposeSemitones)
    }
}
