package dev.anthonyhfm.amethyst.conversion.ableton.data

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class DrumGroupDevice(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val branches: Branches,

    @XmlElement
    val chainSelector: ChainSelector,
) : AbletonDevice {
    @Serializable
    data class ChainSelector(
        @XmlElement
        val keyMidi: ChainSelector.KeyMidi? = null
    ) {
        /**
         * This data model does NOT exist. It is a placeholder to detect if a element is existing in the ChainSelector element
         */
        @Serializable
        data class KeyMidi(
            val enabled: Boolean = false
        )
    }

    @Serializable
    data class Branches(
        val branches: List<DrumBranch>
    ) {
        @Serializable
        data class DrumBranch(
            @SerialName("Id")
            val id: Int,

            @XmlElement
            val name: Name,

            @XmlElement
            val deviceChain: DeviceChain,

            @XmlElement
            val branchSelectorRange: BranchSelectorRange,

            @XmlElement
            val masterDevice: MixerDevice,

            @XmlElement
            val branchInfo: BranchInfo
        ) {
            @Serializable
            data class BranchInfo(
                val receivingNote: ReceivingNote
            ) {
                @Serializable
                data class ReceivingNote(
                    @SerialName("Value")
                    val value: Int
                )
            }

            @Serializable
            data class Name(
                @XmlElement
                @XmlSerialName("EffectiveName")
                val effectiveName: Name.EffectiveName? = null,
            ) {
                @Serializable
                data class EffectiveName(
                    @XmlSerialName("Value")
                    val value: String
                )
            }

            @Serializable
            data class DeviceChain(
                @XmlElement
                @SerialName("DeviceChain")
                val deviceChain: MidiToAudioDeviceChain,
            ) {
                @Serializable
                data class MidiToAudioDeviceChain(
                    @XmlElement
                    @SerialName("Devices")
                    val devices: Devices
                ) {
                    @Serializable
                    data class Devices(
                        val devices: List<@Polymorphic AbletonDevice>
                    )
                }
            }

            @Serializable
            data class BranchSelectorRange(
                @XmlElement
                @XmlSerialName("Min")
                val min: MinMax,

                @XmlElement
                @XmlSerialName("Max")
                val max: MinMax
            ) {
                @Serializable
                data class MinMax(
                    @SerialName("Value")
                    val value: Int
                )
            }

            @Serializable
            data class ZoneSettings(
                @XmlElement
                val keyRange: KeyRange
            ) {
                @Serializable
                data class KeyRange(
                    @XmlElement
                    @XmlSerialName("Min")
                    val min: MinMax,

                    @XmlElement
                    @XmlSerialName("Max")
                    val max: MinMax
                ) {
                    @Serializable
                    data class MinMax(
                        @SerialName("Value")
                        val value: Int
                    )
                }
            }

            @Serializable
            data class MixerDevice(
                @XmlElement
                val speaker: Speaker
            ) {
                @Serializable
                data class Speaker(
                    @XmlElement
                    val manual: Manual
                ) {
                    @Serializable
                    data class Manual(
                        @SerialName("Value")
                        val value: Boolean
                    )
                }
            }
        }
    }
}