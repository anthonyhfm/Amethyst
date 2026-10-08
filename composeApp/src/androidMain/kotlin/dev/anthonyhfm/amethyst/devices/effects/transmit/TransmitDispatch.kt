package dev.anthonyhfm.amethyst.devices.effects.transmit

private val transmitDispatch = object : ThreadLocal<TransmitDispatchContext>() {
    override fun initialValue(): TransmitDispatchContext = TransmitDispatchContext()
}

internal actual fun currentTransmitDispatch(): TransmitDispatchContext = transmitDispatch.get()
