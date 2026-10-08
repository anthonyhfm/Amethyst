package dev.anthonyhfm.amethyst.devices.effects.transmit

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TransmitConcurrencyTest {
    @AfterTest
    fun tearDown() {
        TransmitChainDevice.clearReceivers()
    }

    @Test
    fun registrationChangesDoNotInterruptConcurrentDelivery() {
        val received = AtomicInteger()
        val receiver = TransmitChainDevice().apply {
            state.value = TransmitChainDeviceState(mode = TransmitChainDeviceState.Mode.Receive)
        }
        val stableChain = Chain().apply {
            signalExit = { received.incrementAndGet() }
            add(device = receiver, fromUser = false)
        }
        val senderChain = Chain().apply {
            add(device = TransmitChainDevice(), fromUser = false)
        }
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val messages = listOf(Signal.LED(origin = null, x = 1, y = 1, color = Color.Red))
        val threads = listOf(
            Thread {
                try {
                    start.await()
                    repeat(times = 2_000) {
                        senderChain.signalEnter(n = messages)
                    }
                } catch (cause: Throwable) {
                    failure.compareAndSet(null, cause)
                }
            },
            Thread {
                try {
                    start.await()
                    repeat(times = 2_000) {
                        val temporary = TransmitChainDevice().apply {
                            state.value = TransmitChainDeviceState(mode = TransmitChainDeviceState.Mode.Receive)
                        }
                        val chain = Chain().apply {
                            add(device = temporary, fromUser = false)
                        }
                        chain.dispose()
                    }
                } catch (cause: Throwable) {
                    failure.compareAndSet(null, cause)
                }
            },
        )

        try {
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { thread ->
                thread.join(10_000)
                assertFalse(actual = thread.isAlive, message = "Transmit dispatch did not complete")
            }
            assertNull(actual = failure.get())
            assertEquals(expected = 2_000, actual = received.get())
        } finally {
            threads.filter { it.isAlive }.forEach { it.interrupt() }
            senderChain.dispose()
            stableChain.dispose()
        }
    }
}
