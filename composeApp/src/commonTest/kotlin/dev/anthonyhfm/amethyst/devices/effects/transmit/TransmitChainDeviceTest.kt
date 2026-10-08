package dev.anthonyhfm.amethyst.devices.effects.transmit

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TransmitChainDeviceTest {
    @AfterTest
    fun tearDown() {
        TransmitChainDevice.clearReceivers()
    }

    @Test
    fun receiverRoutesSignalsWithoutRenderingItsChain() {
        val received = mutableListOf<Signal>()
        val receiver = TransmitChainDevice().apply {
            state.value = TransmitChainDeviceState(
                mode = TransmitChainDeviceState.Mode.Receive,
                channel = 3,
            )
        }
        val receiverChain = Chain().apply {
            signalExit = { signals -> received.addAll(signals) }
            add(device = receiver, fromUser = false)
        }
        val sender = TransmitChainDevice().apply {
            state.value = TransmitChainDeviceState(
                mode = TransmitChainDeviceState.Mode.Send,
                channel = 3,
            )
        }
        val senderChain = Chain().apply {
            add(device = sender, fromUser = false)
        }
        val signal = Signal.LED(
            origin = null,
            x = 2,
            y = 4,
            color = Color.Red,
        )

        senderChain.signalEnter(listOf(signal))

        assertEquals<List<Signal>>(listOf(signal), received)

        receiver.state.value = receiver.state.value.copy(channel = 4)
        receiver.onStateRestored()
        senderChain.signalEnter(listOf(signal))

        assertEquals<List<Signal>>(listOf(signal), received)

        sender.state.value = sender.state.value.copy(channel = 4)
        senderChain.signalEnter(listOf(signal))

        assertEquals<List<Signal>>(listOf(signal, signal), received)

        receiverChain.remove(receiver.selectionUUID, fromUser = false)
        senderChain.signalEnter(listOf(signal))

        assertEquals<List<Signal>>(listOf(signal, signal), received)
    }

    @Test
    fun mutedReceiverStopsWirelessDeliveryAndUnmuteRestoresIt() {
        val received = mutableListOf<Signal>()
        val (receiver, _) = attach(mode = TransmitChainDeviceState.Mode.Receive, received = received)
        val (_, senderChain) = attach(mode = TransmitChainDeviceState.Mode.Send)
        val signal = signal()

        receiver.setMuted(muted = true)
        senderChain.signalEnter(n = listOf(signal))
        assertEquals<List<Signal>>(expected = emptyList(), actual = received)

        receiver.setMuted(muted = false)
        senderChain.signalEnter(n = listOf(signal))
        assertEquals<List<Signal>>(expected = listOf(signal), actual = received)
    }

    @Test
    fun privateBusesStayIsolatedAndPreserveOnOffSignals() {
        val matching = mutableListOf<Signal>()
        val other = mutableListOf<Signal>()
        attach(mode = TransmitChainDeviceState.Mode.Receive, busId = "mask", received = matching)
        attach(mode = TransmitChainDeviceState.Mode.Receive, busId = "", received = other)
        val (_, senderChain) = attach(mode = TransmitChainDeviceState.Mode.Send, busId = "mask")
        val signals = listOf(signal(), signal().copy(color = Color.Black))

        senderChain.signalEnter(n = signals)

        assertEquals<List<Signal>>(expected = signals, actual = matching)
        assertEquals<List<Signal>>(expected = emptyList(), actual = other)
    }

    @Test
    fun cyclicReceiverSenderChainStopsWithoutLosingOtherReceivers() {
        val received = mutableListOf<Signal>()
        val (receiver, receiverChain) = attach(mode = TransmitChainDeviceState.Mode.Receive)
        val loopSender = TransmitChainDevice().apply {
            state.value = TransmitChainDeviceState(channel = 3)
        }
        receiverChain.add(device = loopSender, fromUser = false)
        attach(mode = TransmitChainDeviceState.Mode.Receive, received = received)
        val (_, senderChain) = attach(mode = TransmitChainDeviceState.Mode.Send)
        val signal = signal()

        senderChain.signalEnter(n = listOf(signal))
        assertEquals<List<Signal>>(expected = listOf(signal), actual = received)

        receiverChain.remove(uuid = receiver.selectionUUID, fromUser = false)
        received.clear()
        senderChain.signalEnter(n = listOf(signal))
        assertEquals<List<Signal>>(expected = listOf(signal), actual = received)
    }

    @Test
    fun failedReceiverOutputDoesNotPoisonLaterDispatch() {
        val received = mutableListOf<Signal>()
        val (_, receiverChain) = attach(mode = TransmitChainDeviceState.Mode.Receive)
        receiverChain.signalExit = { throw IllegalStateException("Receiver failure") }
        val (_, senderChain) = attach(mode = TransmitChainDeviceState.Mode.Send)

        kotlin.test.assertFailsWith<IllegalStateException> {
            senderChain.signalEnter(n = listOf(signal()))
        }
        receiverChain.signalExit = { signals -> received.addAll(elements = signals) }
        senderChain.signalEnter(n = listOf(signal()))
        assertEquals(expected = 1, actual = received.size)
    }

    private fun attach(
        mode: TransmitChainDeviceState.Mode,
        busId: String = "",
        received: MutableList<Signal> = mutableListOf(),
    ): Pair<TransmitChainDevice, Chain> {
        val device = TransmitChainDevice().apply {
            state.value = TransmitChainDeviceState(mode = mode, channel = 3, busId = busId)
        }
        val chain = Chain().apply {
            signalExit = { signals -> received.addAll(elements = signals) }
            add(device = device, fromUser = false)
        }
        return device to chain
    }

    private fun signal(): Signal.LED = Signal.LED(
        origin = null,
        x = 2,
        y = 4,
        color = Color.Red,
    )

}
