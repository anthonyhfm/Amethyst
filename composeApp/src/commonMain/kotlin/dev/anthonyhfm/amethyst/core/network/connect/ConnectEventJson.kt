package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.devices.DeviceSerializationModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

val ConnectEventJson = Json {
    serializersModule = SerializersModule {
        include(DeviceSerializationModule)
    }
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun ConnectEvent.encodeToString(): String =
    ConnectEventJson.encodeToString(ConnectEvent.serializer(), this)

fun String.decodeToConnectEvent(): ConnectEvent =
    ConnectEventJson.decodeFromString(ConnectEvent.serializer(), this)
