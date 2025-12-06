package dev.anthonyhfm.amethyst.conversion.ableton.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
sealed interface AbletonDevice {
    companion object {
        val module = SerializersModule {
            polymorphic(AbletonDevice::class) {
                subclass(InstrumentGroupDevice::class)
                subclass(MidiEffectGroupDevice::class)
                subclass(DrumGroupDevice::class)
                subclass(MxDeviceMidiEffect::class)
                subclass(OriginalSimpler::class)
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
    val id: Int,

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
                val multiSamplePart: MultiSamplePart
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