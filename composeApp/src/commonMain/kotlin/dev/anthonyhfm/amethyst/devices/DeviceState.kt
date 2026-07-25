package dev.anthonyhfm.amethyst.devices

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class DeviceState {
    @Transient
    open var isMuted: Boolean = false
}