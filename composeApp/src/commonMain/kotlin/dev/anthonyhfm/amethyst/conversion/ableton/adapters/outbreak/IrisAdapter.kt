package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class IrisAdapter (
    private val blob: String
): AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<IrisSelectorData>(blob)

        val bpm = AbletonConverter.bpm

        val palette = AbletonConverter.palette
        val filteredVelocities = data.fadeVelocities.filter { it != 0 }

        val timing = when {
            data.timingByMsStatus.first().toInt() == 0 ->  {
                val timingString = irisIndexToTiming(data.timingRate.first().toInt())
                Timing.Duration(
                    rythmIndexToDuration(timingString, bpm, filteredVelocities.size)
                )
            }
            data.timingByMsStatus.first().toInt() == 1 -> {
                Timing.Duration(data.timingMs.first().milliseconds * filteredVelocities.size)
            }
            else -> Timing.Rythm(Timing.Rythm.RythmTiming._1_8)
        }

        val gradientDevice = GradientChainDeviceState(
            gradientData = List(filteredVelocities.size) { index ->
                val color = palette.getOrElse(filteredVelocities[index]) { index -> Triple(0, 0, 0) }

                GradientChainDeviceState.GradientColor(
                    position = index.toFloat() / (filteredVelocities.size - 1),
                    r = color.first.toFloat() / 63f,
                    g = color.second.toFloat() / 63f,
                    b = color.third.toFloat() / 63f,
                )
            },
            timing = timing,
            durationMs = timing.let {
                when (it) {
                    is Timing.Duration -> it.duration.inWholeMilliseconds.toInt() * filteredVelocities.size
                    is Timing.Rythm -> rythmIndexToDuration(it.timing.text, bpm, filteredVelocities.size).inWholeMilliseconds.toInt()
                }
            }.toDouble(),
        )

        val loopDevice = LoopChainDeviceState(
            onHold = true,
            timing = timing,
        )

        // TODO: check if we can get rid of group here
        return if (data.loopMode.first().toInt() == 1) {
            listOf(
                GroupChainDeviceState(
                    groups = listOf(
                        Group(
                            name = "Iris Loop",
                            stateChain = StateChain(
                                devices = mutableListOf(
                                    loopDevice,
                                    gradientDevice
                                )
                            )
                        )
                    )
                )
            )
        } else {
            listOf(gradientDevice)
        }
    }

    fun irisIndexToTiming(index: Int): String {
        return when (index) {
            0 -> "1/128"
            1 -> "1/64"
            2 -> "1/48"
            3 -> "1/32"
            4 -> "1/24"
            5 -> "1/16"
            6 -> "1/12"
            7 -> "1/8"
            8 -> "1/4"
            else -> "1/8" // Default to 1/8 if unknown
        }
    }

    @Serializable
    data class IrisSelectorData(
        @SerialName("Rate")
        val timingRate: List<Double>,
        @SerialName("Rate[2]")
        val timingMs: List<Double>,

        @SerialName("live.text")
        val loopMode: List<Double>,

        @SerialName("live.text[1]")
        val timingByMsStatus: List<Double>,

        @SerialName("table")
        val fadeVelocities: List<Int>,
    )
}