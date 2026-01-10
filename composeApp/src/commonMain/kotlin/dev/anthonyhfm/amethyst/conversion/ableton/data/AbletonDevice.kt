package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiChord
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiNoteLength
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiPitcher
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiVelocity
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.serialization.XmlElement

interface AbletonDevice {
    companion object {
        val module = SerializersModule {
            polymorphic(AbletonDevice::class) {
                subclass(InstrumentGroupDevice::class)
                subclass(MidiEffectGroupDevice::class)
                subclass(DrumGroupDevice::class)
                subclass(MxDeviceMidiEffect::class)
                subclass(OriginalSimpler::class)
                subclass(MidiVelocity::class)
                subclass(MidiNoteLength::class)
                subclass(MidiRandom::class)
                subclass(MidiPitcher::class)
                subclass(MidiChord::class)
                subclass(MidiArpeggiator::class)
            }

            polymorphic(MxParameter::class) {
                subclass(MxParameter.MxDEnumParameter::class)
                subclass(MxParameter.MxDIntParameter::class)
                subclass(MxParameter.MxDFloatParameter::class)
            }
        }
    }
}

@Serializable
data class OriginalSimpler(
    @SerialName("Id")
    val id: Int = 0,

    @XmlElement
    val player: Player
) : AbletonDevice {
    @Serializable
    data class Player(
        @XmlElement
        val multiSampleMap: MultiSampleMap
    ) {
        @Serializable
        data class MultiSampleMap(
            @XmlElement
            val sampleParts: SampleParts
        ) {
            @Serializable
            data class SampleParts(
                @XmlElement
                val multiSamplePart: MultiSamplePart? = null
            ) {
                @Serializable
                data class MultiSamplePart(
                    @XmlElement
                    val sampleRef: SampleRef,

                    @XmlElement
                    val sampleStart: SampleStart,

                    @XmlElement
                    val sampleEnd: SampleEnd
                ) {
                    @Serializable
                    data class SampleStart(
                        @SerialName("Value")
                        val value: Long
                    )

                    @Serializable
                    data class SampleEnd(
                        @SerialName("Value")
                        val value: Long
                    )

                    @Serializable
                    data class SampleRef(
                        @XmlElement
                        val fileRef: FileRef
                    )
                }
            }
        }
    }
}