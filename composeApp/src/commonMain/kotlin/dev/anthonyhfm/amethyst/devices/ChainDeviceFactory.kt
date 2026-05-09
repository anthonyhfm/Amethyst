package dev.anthonyhfm.amethyst.devices

import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

interface ChainDeviceFactory<S : DeviceState> {
    val stateClass: KClass<S>
    val serializer: KSerializer<S>

    fun create(): GenericChainDevice<S>

    fun pack(device: GenericChainDevice<S>): S = device.state.value

    fun unpack(state: S): GenericChainDevice<S> = create().apply {
        this.state.update { state }
    }
}
