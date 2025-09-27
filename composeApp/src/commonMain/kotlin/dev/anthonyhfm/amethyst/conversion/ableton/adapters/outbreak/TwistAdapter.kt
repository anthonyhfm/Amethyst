package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class TwistAdapter (
    private val data: ByteArray
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: TwistData = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(data.decodeToString())

        val bpm = AbletonConverter.bpm

        val deviceGroups = mutableListOf<Group>()

        dataObj.macroEnabledStates.withIndex().map { (index, num) ->
            if (num == 1) {
                val groupDevices = mutableListOf<DeviceState>()

                if (dataObj.smoothModeEnabledStates[index] == 1) {
                    val isFreeMode = dataObj.freeModeEnabledStates[index] == 1

                    val delayMs = if (isFreeMode) {
                        dataObj.smoothModeValues[index].toLong()
                    } else {
                        val timingString = twistRateIndexToTiming(
                            when (index) {
                                0 -> dataObj.macro1SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                1 -> dataObj.macro2SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                2 -> dataObj.macro3SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                3 -> dataObj.macro4SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                4 -> dataObj.macro5SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                5 -> dataObj.macro6SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                6 -> dataObj.macro7SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                7 -> dataObj.macro8SyncModeIndex.getOrNull(0)?.toInt() ?: 2
                                else -> 2
                            }
                        ) ?: "1/8"

                        rythmIndexToDuration(timingString, bpm, 1).toLong(
                            DurationUnit.MILLISECONDS
                        )
                    }

                    groupDevices.add(
                        DelayChainDeviceState(
                            timing = Timing.Duration(delayMs.milliseconds),
                            delayMs = delayMs
                        )
                    )
                }

                groupDevices.add(
                    SwitchChainDeviceState(
                        macro = index,
                        value = dataObj.pageSwitchNumbers[index]
                    )
                )

                deviceGroups.add(Group(
                    name = "Twist Macro ${index + 1}",
                    stateChain = StateChain(
                        devices = groupDevices,
                    )
                ))
            }
        }

        return listOf(
            GroupChainDeviceState(
                groups = deviceGroups,
            )
        )
    }

    fun twistRateIndexToTiming(index: Int): String? {
        return when (index) {
            0 -> "1/32"
            1 -> "1/16"
            2 -> "1/8"
            3 -> "1/4"
            4 -> "1/2"
            5 -> "1/1"
            6 -> "2/1"
            7 -> "4/1"
            8 -> "8/1"
            9 -> "16/1"
            else -> "1/8" // Default to 1/8 if unknown
        }
    }

    @Serializable
    data class TwistData(
        @SerialName("table[13]")
        val macroEnabledStates: List<Int>,

        @SerialName("table[1]")
        val pageSwitchNumbers: List<Int>,

        @SerialName("table[7]")
        val smoothModeEnabledStates: List<Int>,
        @SerialName("table[8]")
        val smoothModeValues: List<Int>,
        @SerialName("table[6]")
        val freeModeEnabledStates: List<Int>, // 1 if free mode (timing in ms)

        @SerialName("Rate")
        val macro1SyncModeIndex: List<Double>,
        @SerialName("Rate[3]")
        val macro2SyncModeIndex: List<Double>,
        @SerialName("Rate[4]")
        val macro3SyncModeIndex: List<Double>,
        @SerialName("Rate[5]")
        val macro4SyncModeIndex: List<Double>,
        @SerialName("Rate[6]")
        val macro5SyncModeIndex: List<Double>,
        @SerialName("Rate[7]")
        val macro6SyncModeIndex: List<Double>,
        @SerialName("Rate[8]")
        val macro7SyncModeIndex: List<Double>,
        @SerialName("Rate[9]")
        val macro8SyncModeIndex: List<Double>,

        // TODO: handle slope for newer twist version
    )
}