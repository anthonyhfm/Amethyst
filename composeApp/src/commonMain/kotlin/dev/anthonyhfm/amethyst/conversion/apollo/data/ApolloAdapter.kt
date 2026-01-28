package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloChokeAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloCopyAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloDelayAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloFadeAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloFlipAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloGroupAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloHoldAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloKeyFilterAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloLayerAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloLayerFilterAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloLoopAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloMacroFilterAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloPaintAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloPatternAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloRotateAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloSwitchAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState

abstract class ApolloAdapter<T: ApolloModel.Device>(
    protected val model: T
) {

    abstract fun toDeviceState(): DeviceState

    companion object {
        fun resolveAdapter(model: ApolloModel.Device): DeviceState {
            return when (model) {
                is ApolloModel.Device.KeyFilter -> ApolloKeyFilterAdapter(model)
                is ApolloModel.Device.MacroFilter -> ApolloMacroFilterAdapter(model)
                is ApolloModel.Device.Group -> ApolloGroupAdapter(model)
                is ApolloModel.Device.Choke -> ApolloChokeAdapter(model)
                is ApolloModel.Device.Copy -> ApolloCopyAdapter(model)
                is ApolloModel.Device.Delay -> ApolloDelayAdapter(model)
                is ApolloModel.Device.Hold -> ApolloHoldAdapter(model)
                is ApolloModel.Device.Loop -> ApolloLoopAdapter(model)
                is ApolloModel.Device.Paint -> ApolloPaintAdapter(model)
                is ApolloModel.Device.Fade -> ApolloFadeAdapter(model)
                is ApolloModel.Device.Pattern -> ApolloPatternAdapter(model)
                is ApolloModel.Device.Layer -> ApolloLayerAdapter(model)
                is ApolloModel.Device.LayerFilter -> ApolloLayerFilterAdapter(model)
                is ApolloModel.Device.Switch -> ApolloSwitchAdapter(model)
                is ApolloModel.Device.Flip -> ApolloFlipAdapter(model)
                is ApolloModel.Device.Rotate -> ApolloRotateAdapter(model)

                else -> error("Apollo adapter missing for: ${model::class.simpleName}")
            }.toDeviceState()
        }
    }
}