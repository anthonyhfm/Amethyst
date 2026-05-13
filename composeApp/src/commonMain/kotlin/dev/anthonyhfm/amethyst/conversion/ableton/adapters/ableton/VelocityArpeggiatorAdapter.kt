package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState

class VelocityArpeggiatorAdapter(
    private val device: MidiEffectGroupDevice
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        // get macro values from midi effect rack (rate, colors)
        val gradientRate = device.macros[0].manual.value

        val velocities = getGradientVelocities()

        // convert rate and velocities to gradient device
        val palette = AbletonConverter.palette
        val bpm = AbletonConverter.bpm
        val steps = velocities.size

        println("Converting Velocity Arpeggiator with rate $gradientRate and velocities $velocities")

        val convertedRateString = rateRangeToTiming(gradientRate.toInt())
        val rateMs = rythmIndexToDuration(convertedRateString, bpm, steps)

        return listOf(
            GradientChainDeviceState(
                gradientData = List(velocities.size) { index ->
                    val color = palette.getOrElse(velocities[index]) { index -> Triple(0, 0, 0) }

                    GradientChainDeviceState.GradientColor(
                        position = index.toFloat() / (velocities.size - 1),
                        r = color.first.toFloat() / 63f,
                        g = color.second.toFloat() / 63f,
                        b = color.third.toFloat() / 63f,
                    )
                },
                timing = Timing.Duration(rateMs),
                durationMs = rateMs.inWholeMilliseconds.toDouble()
            )
        )
    }

    fun getGradientVelocities(): List<Int> {
        return device.macros.map {
            it.manual.value.toInt()
        }.filter {
            it != 0
        }
    }

    fun rateRangeToTiming(rate: Int): String {
        return when (rate) {
            in 0..7 -> "1/64"
            in 8..23 -> "1/48"
            in 24..39 -> "1/32"
            in 40..55 -> "1/24"
            in 56..71 -> "1/16"
            in 72..87 -> "1/12"
            in 88..103 -> "1/8"
            in 104..119 -> "1/6"
            in 120..127 -> "1/4"
            else -> "1/8"
        }
    }
}