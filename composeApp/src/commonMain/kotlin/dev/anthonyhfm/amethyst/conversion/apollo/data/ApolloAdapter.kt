package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloGroupAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.adapters.ApolloKeyFilterAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState

abstract class ApolloAdapter<T: ApolloModel.Device>(model: T) {
    protected val model = model

    abstract fun toDeviceState(): DeviceState

    companion object {
        fun resolveAdapter(model: ApolloModel.Device): DeviceState {
            return when (model) {
                is ApolloModel.Device.KeyFilter -> ApolloKeyFilterAdapter(model)
                is ApolloModel.Device.Group -> ApolloGroupAdapter(model)

                else -> error("Apollo adapter missing for: ${model::class.simpleName}")
            }.toDeviceState()
        }
    }
}