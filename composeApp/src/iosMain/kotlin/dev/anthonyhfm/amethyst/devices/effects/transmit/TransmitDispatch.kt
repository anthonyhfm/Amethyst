package dev.anthonyhfm.amethyst.devices.effects.transmit

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private val transmitDispatch = TransmitDispatchContext()

internal actual fun currentTransmitDispatch(): TransmitDispatchContext = transmitDispatch
