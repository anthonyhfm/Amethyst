package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import dev.anthonyhfm.amethyst.conversion.ableton.data.AutomationTarget
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class InstrumentGroupDevice(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val on: AbletonOn = AbletonOn(),

    @XmlElement
    val branches: Branches,

    @XmlElement
    val chainSelector: ChainSelector,

    @XmlElement
    @XmlSerialName("MacroControls.0")
    val macro0: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.1")
    val macro1: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.2")
    val macro2: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.3")
    val macro3: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.4")
    val macro4: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.5")
    val macro5: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.6")
    val macro6: Macro? = null,
    @XmlElement
    @XmlSerialName("MacroControls.7")
    val macro7: Macro? = null,

    @Transient
    val macros: List<Macro> = listOfNotNull(macro0, macro1, macro2, macro3, macro4, macro5, macro6, macro7)
) : AbletonDevice {
    @Serializable
    data class Macro(
        @XmlElement
        val manual: AbletonManual<Float> = AbletonManual(0f),
        @XmlElement
        @XmlSerialName("AutomationTarget")
        val automationTarget: AutomationTarget? = null
    )
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
        val branches: List<InstrumentBranch>
    ) {
        @Serializable
        data class InstrumentBranch(
            @SerialName("Id")
            val id: Int,

            @XmlElement
            val name: Branches.InstrumentBranch.Name,

            @XmlElement
            val deviceChain: Branches.InstrumentBranch.DeviceChain,

            @XmlElement
            val zoneSettings: Branches.InstrumentBranch.ZoneSettings,

            @XmlElement
            val branchSelectorRange: Branches.InstrumentBranch.BranchSelectorRange,

            @XmlElement
            val masterDevice: Branches.InstrumentBranch.MixerDevice
        ) {
            @Serializable
            data class Name(
                @XmlElement
                @XmlSerialName("EffectiveName")
                val effectiveName: Branches.InstrumentBranch.Name.EffectiveName? = null,
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
                val deviceChain: Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain,
            ) {
                @Serializable
                data class MidiToAudioDeviceChain(
                    @XmlElement
                    @SerialName("Devices")
                    val devices: Branches.InstrumentBranch.DeviceChain.MidiToAudioDeviceChain.Devices
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
                val min: Branches.InstrumentBranch.BranchSelectorRange.MinMax,

                @XmlElement
                @XmlSerialName("Max")
                val max: Branches.InstrumentBranch.BranchSelectorRange.MinMax
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
                val keyRange: Branches.InstrumentBranch.ZoneSettings.KeyRange
            ) {
                @Serializable
                data class KeyRange(
                    @XmlElement
                    @XmlSerialName("Min")
                    val min: Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax,

                    @XmlElement
                    @XmlSerialName("Max")
                    val max: Branches.InstrumentBranch.ZoneSettings.KeyRange.MinMax
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
                val speaker: Branches.InstrumentBranch.MixerDevice.Speaker
            ) {
                @Serializable
                data class Speaker(
                    @XmlElement
                    val manual: AbletonManual<Boolean>
                )
            }
        }
    }
}