package dev.anthonyhfm.amethyst.conversion.ableton.adapters

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.DrumGroupDeviceAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.Compressor2Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.Eq8Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.InstrumentGroupAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiArpeggiatorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiChordAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiEffectGroupAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiNoteLengthAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiPitcherAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiVelocityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceInstrumentAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.LimiterAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.StereoGainAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Compressor2
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Eq8
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceInstrument
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiChord
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiNoteLength
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiPitcher
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiVelocity
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Limiter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.StereoGain
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.json.Json

abstract class AbletonAdapter {
    protected val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }

    abstract fun toDeviceStates(): List<DeviceState>

    /**
     * Applies Ableton's device On/Off state as isMuted on all returned DeviceStates.
     * In Ableton, On.Manual = false means the device is disabled/muted.
     */
    protected fun List<DeviceState>.withMuteState(isOn: Boolean): List<DeviceState> {
        if (!isOn) forEach { it.isMuted = true }
        return this
    }

    /**
     * In Ableton Live, Multi / multisampling devices (e.g. Outbreak Multi, Kaskobi Multi,
     * MidiRandom in Alt mode) cycle through chains by pitch-shifting outgoing MIDI notes
     * up by (+step) semitones. Because Ableton's Simpler tracks MIDI pitch, the project
     * author lowered each Simpler's transpose by (-step) semitones to compensate.
     * In Amethyst, MultiGroupChainDevice routes directly to chains without shifting note pitch.
     * Therefore, the (-step) downpitch on Simpler must be counteracted by (+step).
     */
    protected fun DeviceState.withPitchCompensation(semitones: Float): DeviceState {
        if (semitones == 0f) return this
        return when (this) {
            is SampleChainDeviceState -> preserveDisplayState(
                copy(transposeSemitones = transposeSemitones + semitones)
            )
            is GroupChainDeviceState -> preserveDisplayState(
                copy(
                    groups = groups.map { group ->
                        group.copy(
                            stateChain = group.stateChain.withPitchCompensation(semitones)
                        )
                    }
                )
            )
            is MultiGroupChainDeviceState -> preserveDisplayState(
                copy(
                    groups = groups.map { group ->
                        group.copy(
                            stateChain = group.stateChain.withPitchCompensation(semitones)
                        )
                    },
                    preprocessChain = preprocessChain.withPitchCompensation(semitones)
                )
            )
            is ChokeChainDeviceState -> preserveDisplayState(
                copy(
                    stateChain = stateChain.withPitchCompensation(semitones)
                )
            )
            else -> this
        }
    }

    protected fun StateChain.withPitchCompensation(semitones: Float): StateChain {
        if (semitones == 0f) return this
        return copy(
            devices = devices.map { it.withPitchCompensation(semitones) }
        )
    }

    protected fun List<DeviceState>.withPitchCompensation(semitones: Float): List<DeviceState> {
        if (semitones == 0f) return this
        return map { it.withPitchCompensation(semitones) }
    }

    private fun <T : DeviceState> DeviceState.preserveDisplayState(copy: T): T = copy.also {
        it.isMuted = isMuted
        it.isCollapsed = isCollapsed
    }

    companion object {
        fun resolveAdapter(
            device: AbletonDevice,
            offset: IntOffset = IntOffset.Zero,
            outputOffset: IntOffset = IntOffset.Zero,
            chainDepth: Int = 0,
            isInsideDrumRack: Boolean = false,
        ): AbletonAdapter? {
            try {
                return when (device) {
                    is DrumGroupDevice -> DrumGroupDeviceAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth
                    )

                    is InstrumentGroupDevice -> InstrumentGroupAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth,
                        isInsideDrumRack = isInsideDrumRack,
                    )

                    is MidiEffectGroupDevice -> MidiEffectGroupAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth,
                        isInsideDrumRack = isInsideDrumRack,
                    )

                    is MxDeviceMidiEffect -> MxDeviceMidiEffectAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset
                    )

                    is MxDeviceInstrument -> MxDeviceInstrumentAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset
                    )

                    is OriginalSimpler -> OriginalSimplerAdapter(device)
                    is MidiNoteLength -> MidiNoteLengthAdapter(device)
                    is MidiVelocity -> MidiVelocityAdapter(device)
                    is MidiPitcher -> MidiPitcherAdapter(device)
                    is MidiChord -> MidiChordAdapter(device)
                    is MidiArpeggiator -> MidiArpeggiatorAdapter(device)
                    is Eq8 -> Eq8Adapter(device)
                    is StereoGain -> StereoGainAdapter(device)
                    is Limiter -> LimiterAdapter(device)
                    is Compressor2 -> Compressor2Adapter(device)

                    else -> {
                        println("Unsupported Ableton device type: ${device::class.simpleName}")

                        null
                    }
                }
            } catch (e: Exception) {
                println("Error while resolving Ableton adapter for element ${device::class.simpleName}: ${e.message}")
            }

            return null
        }
    }
}
