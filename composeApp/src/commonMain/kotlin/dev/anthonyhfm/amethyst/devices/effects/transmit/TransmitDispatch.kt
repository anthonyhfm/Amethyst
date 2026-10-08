package dev.anthonyhfm.amethyst.devices.effects.transmit

internal class TransmitDispatchContext {
    val activeSenders = mutableSetOf<TransmitChainDevice>()
    val receivers = mutableSetOf<TransmitChainDevice>()
}

internal expect fun currentTransmitDispatch(): TransmitDispatchContext
