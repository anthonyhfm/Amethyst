package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry

class SetNotesAdapter(private val device: MxDevice) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val blob = device.decodeBlob()
        val setPitch = flag(blob, "Set Pitch")
        val setVelocity = flag(blob, "Set Velocity")
        val pitch = intParameter(device, 0)
        val velocity = intParameter(device, 1).coerceIn(0, 127)
        val color = AbletonConverter.palette.getOrElse(velocity) { Triple(0, 0, 0) }
        val pad = pitch.takeIf { setPitch }?.let(DRUM_RACK_TO_XY::getOrNull)
        val entry = pad?.let {
            KeyframesEntry(
                x = it % 10,
                y = 9 - it / 10,
                r = if (setVelocity) color.first / 63f else 1f,
                g = if (setVelocity) color.second / 63f else 1f,
                b = if (setVelocity) color.third / 63f else 1f,
            )
        }
        return listOf(
            KeyframesChainDeviceState(
                frames = listOf(
                    Frame(
                        timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
                        entries = listOfNotNull(entry),
                    )
                ),
            )
        )
    }

    companion object {
        private fun flag(blob: String, name: String): Boolean =
            Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\[\\s*1")
                .containsMatchIn(blob)

        private fun intParameter(device: MxDevice, index: Int): Int =
            when (val parameter = device.parameterList.parameterList.parameters.firstOrNull { it.index == index }) {
                is MxParameter.MxDIntParameter -> parameter.timeable.manual.value
                is MxParameter.MxDEnumParameter -> parameter.timeable.manual.value
                is MxParameter.MxDFloatParameter -> parameter.timeable.manual.value.toInt()
                else -> 0
            }
    }
}
