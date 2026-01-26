package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.toTiming
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import dev.anthonyhfm.amethyst.ui.components.toMsValue

class ApolloFadeAdapter(
    model: ApolloModel.Device.Fade
) : ApolloAdapter<ApolloModel.Device.Fade>(model) {
    override fun toDeviceState(): DeviceState {
        return GradientChainDeviceState(
            gradientData = model.colors.mapIndexed { index, color ->
                GradientChainDeviceState.GradientColor(
                    position = model.positions[index].toFloat(),
                    r = color.r.toInt() / 63f,
                    g = color.g.toInt() / 63f,
                    b = color.b.toInt() / 63f,
                    smoothness = GradientSmoothness.entries[model.fadeTypes.getOrNull(index) ?: 0]
                )
            },
            timing = model.time.toTiming(),
            durationMs = model.time.toTiming().toMsValue(ApolloConverter.bpm.toDouble()).toDouble(),
            gate = model.gate.toFloat() / 2f,
            loop = model.playMode == 1
        )
    }
}