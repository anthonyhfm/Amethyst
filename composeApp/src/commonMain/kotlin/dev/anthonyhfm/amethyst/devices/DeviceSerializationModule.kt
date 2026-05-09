package dev.anthonyhfm.amethyst.devices

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

val DeviceSerializationModule = SerializersModule {
    polymorphic(DeviceState::class) {
        @Suppress("UNCHECKED_CAST")
        DeviceRegistry.factories.forEach { (_, factory) ->
            subclass(
                factory.stateClass as KClass<DeviceState>,
                factory.serializer as KSerializer<DeviceState>
            )
        }
    }
}
