package dev.anthonyhfm.amethyst.conversion.ableton.adapters

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.DrumGroupDeviceAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.InstrumentGroupAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiEffectGroupAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiNoteLength
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiVelocity
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.json.Json

abstract class AbletonAdapter {
    protected val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }

    abstract fun toDeviceStates(): List<DeviceState>

    companion object {
        fun resolveAdapter(
            device: AbletonDevice,
            offset: IntOffset = IntOffset.Zero,
            outputOffset: IntOffset = IntOffset.Zero,
            chainDepth: Int = 0,
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
                        chainDepth = chainDepth
                    )

                    is MidiEffectGroupDevice -> MidiEffectGroupAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth
                    )

                    is MxDeviceMidiEffect -> MxDeviceMidiEffectAdapter(
                        device = device,
                        offset = offset,
                        outputOffset = outputOffset
                    )

                    is OriginalSimpler -> OriginalSimplerAdapter(device)

                    else -> {
                        println("Unsupported Ableton device type: ${device::class.simpleName}")

                        null
                    }
                }

                /*return when (xml.name) {
                    "MidiEffectGroupDevice", "InstrumentGroupDevice" -> MidiEffectGroupAdapter(
                        xml = xml,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth
                    )

                    "DrumGroupDevice" -> DrumGroupDeviceAdapter(
                        xml = xml,
                        offset = offset,
                        outputOffset = outputOffset,
                        chainDepth = chainDepth
                    )

                    "OriginalSimpler" -> OriginalSimplerAdapter(xml)
                    "MidiVelocity" -> MidiVelocityAdapter(xml)
                    "MidiNoteLength" -> MidiNoteLengthAdapter(xml)

                    "MxDeviceMidiEffect" -> MxDeviceMidiEffectAdapter(
                        xml = xml,
                        offset = offset,
                        outputOffset = outputOffset
                    ) // Will resolve max plugins

                    else -> {
                        println("Unsupported Ableton XML element: ${xml.name}")
                        null
                    }
                }*/
            } catch (e: Exception) {
                println("Error while resolving Ableton adapter for element ${device::class.simpleName}: ${e.message}")
            }

            return null
        }
    }
}