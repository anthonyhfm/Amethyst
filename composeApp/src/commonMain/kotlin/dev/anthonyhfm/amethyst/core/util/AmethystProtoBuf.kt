package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.devices.DeviceStateSerializationModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
val AmethystProtoBuf = ProtoBuf {
    serializersModule = DeviceStateSerializationModule
}