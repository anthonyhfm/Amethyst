package dev.anthonyhfm.amethyst.core.engine.heaven

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeavenResilienceTest {
    @Test
    fun throwingCancellationPredicateLeavesHeapIntact() {
        val queue = ScheduledJobQueue()
        listOf(3L, 1L, 2L).forEach { deadline ->
            queue.add(job = job(id = "$deadline", deadline = deadline))
        }
        var inspected = 0

        assertFailsWith<IllegalStateException> {
            queue.removeAll {
                inspected++
                check(inspected < 2)
                true
            }
        }

        assertEquals(expected = 3, actual = queue.size)
        assertEquals(expected = listOf("1", "2", "3"), actual = List(3) { queue.removeFirst().id })
    }

    @Test
    fun schedulerContinuesAfterCommandFailure() {
        val owner = Any()
        val completed = CountDownLatch(2)
        val failedCommand = CountDownLatch(1)
        Heaven.schedule(delayInMs = 100.0, owner = owner) { completed.countDown() }
        Heaven.cancelJobs { job ->
            if (job.owner === owner) {
                failedCommand.countDown()
                error("injected cancellation failure")
            }
            false
        }
        Heaven.schedule(delayInMs = 110.0, owner = owner) { completed.countDown() }

        try {
            assertTrue(actual = failedCommand.await(2, TimeUnit.SECONDS))
            assertTrue(actual = completed.await(2, TimeUnit.SECONDS))
        } finally {
            Heaven.cancelJobsForOwner(owner = owner)
        }
    }

    @Test
    fun rendererDrawsBeforeBacklogIsEmptyAndPreservesFinalBlackFrame(): Unit = runBlocking {
        Heaven.resetForTesting()
        val previousDevices = Heaven.devices
        val launchpad = ViewportLaunchpadPro()
        val firstDraw = CountDownLatch(1)
        val resumeDraw = CountDownLatch(1)
        val intermediateDraw = CountDownLatch(1)
        val frames = AtomicInteger()
        val sawBacklog = AtomicBoolean()
        val handler: suspend () -> Unit = {
            if (frames.incrementAndGet() == 1) {
                firstDraw.countDown()
                check(resumeDraw.await(5, TimeUnit.SECONDS))
            } else if (Heaven.hasPendingSignalsForTesting()) {
                sawBacklog.set(true)
                intermediateDraw.countDown()
            }
        }
        val red = Signal.LED(origin = null, x = 1, y = 1, color = Color.Red, layer = 5)
        val blue = red.copy(color = Color.Blue, layer = 6)
        Screen.addDrawingHandler(handler = handler)

        try {
            Heaven.devices = listOf(launchpad)
            Heaven.midiEnter(signals = listOf(red))
            assertTrue(actual = firstDraw.await(2, TimeUnit.SECONDS))
            repeat(20_000) { iteration ->
                Heaven.midiEnter(signals = listOf(if (iteration % 2 == 0) red else blue))
            }
            Heaven.midiEnter(signals = listOf(red.copy(color = Color.Black), blue.copy(color = Color.Black)))
            resumeDraw.countDown()
            assertTrue(actual = intermediateDraw.await(3, TimeUnit.SECONDS))
            Heaven.waitUntilIdleForTesting(timeoutMs = 5_000L)
            assertTrue(actual = sawBacklog.get())
            assertTrue(actual = (0..100).all { launchpad.screen.getColor(index = it) == Color.Black })
        } finally {
            resumeDraw.countDown()
            Screen.removeDrawingHandler(handler = handler)
            Heaven.resetForTesting()
            Heaven.devices = previousDevices
            launchpad.close()
        }
    }

    private fun job(id: String, deadline: Long): ScheduledJob = ScheduledJob(
        id = id,
        targetTimeNanos = deadline,
        sequence = deadline,
        job = {},
        globalGeneration = 0,
    )
}
