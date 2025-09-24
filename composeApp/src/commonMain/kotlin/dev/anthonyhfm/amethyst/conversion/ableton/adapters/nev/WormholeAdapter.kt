package dev.anthonyhfm.amethyst.conversion.ableton.adapters.nev

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DelayAdapter.DelayData
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WormholeAdapter (
    val data: ByteArray
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: WormholeData = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(data.decodeToString())

        return listOf(
            GroupChainDeviceState(
                groups = mutableListOf<Group>().apply {
                    if (dataObj.macro1Enabled.first() == 1.0) {
                        add(
                            Group("Macro 1",
                                stateChain = StateChain(
                                    devices = listOf(
                                        SwitchChainDeviceState(
                                            macro = 0,
                                            value = dataObj.macroSwitchValue1.first().toInt()
                                        )
                                    )
                                )
                            )
                        )
                    }
                    else {
                        error("Wormhole Macro 1 is not enabled - no support for macros other than 1")
                    }
                }
            )
        )
    }

    @Serializable
    data class WormholeData(
        @SerialName("Destination_1")
        val macroSwitchValue1: List<Double>,
        @SerialName("Destination_2")
        val macroSwitchValue2: List<Double>,
        @SerialName("Destination_3")
        val macroSwitchValue3: List<Double>,
        @SerialName("Destination_4")
        val macroSwitchValue4: List<Double>,
        @SerialName("Destination_5")
        val macroSwitchValue5: List<Double>,
        @SerialName("Destination_6")
        val macroSwitchValue6: List<Double>,
        @SerialName("Destination_7")
        val macroSwitchValue7: List<Double>,
        @SerialName("Destination_8")
        val macroSwitchValue8: List<Double>,

        @SerialName("Enabler_1")
        val macro1Enabled: List<Double>,
        @SerialName("Enabler_2")
        val macro2Enabled: List<Double>,
        @SerialName("Enabler_3")
        val macro3Enabled: List<Double>,
        @SerialName("Enabler_4")
        val macro4Enabled: List<Double>,
        @SerialName("Enabler_5")
        val macro5Enabled: List<Double>,
        @SerialName("Enabler_6")
        val macro6Enabled: List<Double>,
        @SerialName("Enabler_7")
        val macro7Enabled: List<Double>,
        @SerialName("Enabler_8")
        val macro8Enabled: List<Double>,
    )
}