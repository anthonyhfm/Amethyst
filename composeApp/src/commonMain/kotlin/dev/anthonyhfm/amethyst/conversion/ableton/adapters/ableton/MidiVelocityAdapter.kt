package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState

class MidiVelocityAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val velocity = xml
            .querySelector("MaxOut")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toInt() ?: 127
        
        val palette = AbletonConverter.palette

        return listOf(
            ColorChainDeviceState(
                r = palette[velocity].first / 63f,
                g = palette[velocity].second / 63f,
                b = palette[velocity].third / 63f,
            )
        )
    }
}