package dev.anthonyhfm.amethyst.conversion.ableton.adapters

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.DrumGroupDeviceAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiEffectGroupAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiNoteLengthAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MidiVelocityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.json.Json

abstract class AbletonAdapter {
    protected val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }

    abstract fun toDeviceStates(): List<DeviceState>

    companion object {
        fun resolveAdapter(
            xml: XmlElement,
            offset: IntOffset = IntOffset.Zero,
            outputOffset: IntOffset = IntOffset.Zero,
            chainDepth: Int = 0,
        ): AbletonAdapter? {
            try {
                return when (xml.name) {
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
                }
            } catch (e: Exception) {
                println("Error while resolving Ableton adapter for element ${xml.name}: ${e.message}")
            }

            return null
        }
    }
}