package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Compressor2
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Eq8
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiChord
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiNoteLength
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiPitcher
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiVelocity
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceInstrument
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Limiter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.StereoGain
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

interface AbletonDevice {
    companion object {
        val module = SerializersModule {
            polymorphic(AbletonDevice::class) {
                subclass(InstrumentGroupDevice::class)
                subclass(MidiEffectGroupDevice::class)
                subclass(DrumGroupDevice::class)
                subclass(MxDeviceMidiEffect::class)
                subclass(MxDeviceInstrument::class)
                subclass(OriginalSimpler::class)
                subclass(MidiVelocity::class)
                subclass(MidiNoteLength::class)
                subclass(MidiRandom::class)
                subclass(MidiPitcher::class)
                subclass(MidiChord::class)
                subclass(MidiArpeggiator::class)
                subclass(Eq8::class)
                subclass(StereoGain::class)
                subclass(Limiter::class)
                subclass(Compressor2::class)
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
    val on: AbletonOn = AbletonOn(),

    @XmlElement
    val player: Player,
    @XmlElement
    val pitch: Pitch = Pitch(),
    val volumeAndPan: VolumeAndPan
) : AbletonDevice {
    @Serializable
    data class Pitch(
        @XmlElement
        @XmlSerialName("TransposeKey")
        val transposeKey: TransposeData = TransposeData(),
    ) {
        @Serializable
        data class TransposeData(
            val manual: AbletonManual<Float> = AbletonManual(0f),
        )
    }

    @Serializable
    data class Player(
        @XmlElement
        val multiSampleMap: MultiSampleMap,

        @XmlElement
        @XmlSerialName("LoopModulators")
        val loopModulators: LoopModulators = LoopModulators(),
    ) {
        @Serializable
        data class LoopModulators(
            @XmlElement
            @XmlSerialName("LoopOn")
            val loopOn: AbletonOn = AbletonOn(manual = dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual(false)),
        )

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

    @Serializable
    data class VolumeAndPan(
        @XmlElement
        @XmlSerialName("Volume")
        val volume: VolumeData = VolumeData(),

        val oneShotEnvelope: OneShotEnvelope = OneShotEnvelope()
    ) {
        @Serializable
        data class VolumeData(
            val manual: AbletonManual<Float> = AbletonManual(0f)
        )

        @Serializable
        data class OneShotEnvelope(
            @XmlElement
            @XmlSerialName("FadeInTime")
            val fadeInTime: FadeData = FadeData(),

            @XmlElement
            @XmlSerialName("FadeOutTime")
            val fadeOutTime: FadeData = FadeData()
        ) {
            @Serializable
            data class FadeData(
                val manual: AbletonManual<Float> = AbletonManual(0f)
            )
        }
    }
}
