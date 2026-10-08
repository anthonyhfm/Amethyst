package dev.anthonyhfm.amethyst.devices.effects.loop

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Timing
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class LoopResilienceTest {
    @Test
    fun zeroGateHoldLoopIsPacedAndStopsOnRelease(): Unit = runBlocking {
        val loop = LoopChainDevice()
        loop.state.value = loop.state.value.copy(
            timing = Timing.Duration(duration = 10.milliseconds),
            gate = 0f,
            onHold = true,
        )
        val received = AtomicInteger()
        val recurringSignal = CountDownLatch(4)
        loop.signalExit = {
            received.incrementAndGet()
            recurringSignal.countDown()
        }
        val signal = Signal.Midi(origin = null, x = 1, y = 1, velocity = 127)

        try {
            loop.signalEnter(n = listOf(signal))
            assertTrue(actual = recurringSignal.await(2, TimeUnit.SECONDS))
            delay(timeMillis = 30L)
            assertTrue(actual = received.get() < 500)
            loop.signalEnter(n = listOf(signal.copy(velocity = 0)))
            Heaven.waitUntilIdleForTesting()
            val afterRelease = received.get()
            delay(timeMillis = 20L)
            assertTrue(actual = received.get() == afterRelease)
        } finally {
            loop.onChoke()
        }
    }

    @Test
    fun chokeStopsHeldLoop(): Unit = runBlocking {
        val loop = LoopChainDevice()
        loop.state.value = loop.state.value.copy(
            timing = Timing.Duration(duration = 2.milliseconds),
            onHold = true,
        )
        val received = AtomicInteger()
        loop.signalExit = { received.incrementAndGet() }
        loop.signalEnter(n = listOf(Signal.Midi(origin = null, x = 1, y = 1, velocity = 127)))
        delay(timeMillis = 10L)
        loop.onChoke()
        Heaven.waitUntilIdleForTesting()
        val afterChoke = received.get()
        delay(timeMillis = 20L)
        assertTrue(actual = received.get() == afterChoke)
    }
}
