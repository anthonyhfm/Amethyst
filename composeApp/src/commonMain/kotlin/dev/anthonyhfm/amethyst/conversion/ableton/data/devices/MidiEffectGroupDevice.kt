package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class MidiEffectGroupDevice(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val branches: Branches,

    @XmlElement
    val chainSelector: ChainSelector,

    @XmlElement
    @XmlSerialName("MacroControls.0")
    private val macro0: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.1")
    private val macro1: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.2")
    private val macro2: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.3")
    private val macro3: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.4")
    private val macro4: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.5")
    private val macro5: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.6")
    private val macro6: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.7")
    private val macro7: Macro? = null,

    @Transient
    val macros: List<Macro> = listOfNotNull(macro0, macro1, macro2, macro3, macro4, macro5, macro6, macro7)
) : AbletonDevice {
    @Serializable
    data class ChainSelector(
        @XmlElement
        val keyMidi: KeyMidi? = null
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
        val branches: List<MidiEffectBranch>
    ) {
        @Serializable
        data class MidiEffectBranch(
            @SerialName("Id")
            val id: Int,

            @XmlElement
            val name: Name,

            @XmlElement
            val deviceChain: DeviceChain,

            @XmlElement
            val zoneSettings: ZoneSettings,

            @XmlElement
            val branchSelectorRange: BranchSelectorRange,

            @XmlElement
            val masterDevice: MixerDevice
        ) {
            @Serializable
            data class Name(
                @XmlElement
                @XmlSerialName("EffectiveName")
                val effectiveName: EffectiveName? = null,
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
                val deviceChain: MidiToMidiDeviceChain,
            ) {
                @Serializable
                data class MidiToMidiDeviceChain(
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
                    val manual: AbletonManual<Boolean>
                )
            }
        }
    }

    @Serializable
    data class Macro(
        val manual: AbletonManual<Int>
    )
}