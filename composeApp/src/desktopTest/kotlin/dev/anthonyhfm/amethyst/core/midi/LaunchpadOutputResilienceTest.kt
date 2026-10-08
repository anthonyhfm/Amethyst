package dev.anthonyhfm.amethyst.core.midi

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadFirmware
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchpadOutputResilienceTest {
    @Test
    fun slowOutputConflatesToCompleteFinalState(): Unit = runBlocking {
        val output = RecordingOutput(blockFirstSend = true)
        val device = device(output = output)

        try {
            device.sendUpdate(updates = listOf(update(index = 11, color = Color.Red)), colors = emptyArray())
            assertTrue(actual = output.firstSend.await(2, TimeUnit.SECONDS))
            device.sendUpdate(updates = listOf(update(index = 22, color = Color.Green)), colors = emptyArray())
            device.sendUpdate(updates = listOf(update(index = 33, color = Color.Blue)), colors = emptyArray())
            device.sendUpdate(
                updates = listOf(update(index = 11, color = Color.Black), update(index = 33, color = Color.Black)),
                colors = emptyArray(),
            )
            output.resumeSend.countDown()
            waitUntil { output.sends.get() >= 2 }

            assertEquals(expected = 0, actual = output.colors[11])
            assertEquals(expected = 0x00FF00, actual = output.colors[22])
            assertEquals(expected = 0, actual = output.colors[33])
            assertTrue(actual = device.isOutputHealthy)
        } finally {
            output.resumeSend.countDown()
            device.close()
        }
    }

    @Test
    fun sendFailureMarksWorkerUnhealthyEvenIfCloseThrows(): Unit = runBlocking {
        val output = RecordingOutput(failSend = true, failClose = true)
        val device = device(output = output)

        try {
            device.sendUpdate(updates = listOf(update(index = 11, color = Color.Red)), colors = emptyArray())
            waitUntil { !device.isOutputHealthy }
            device.sendUpdate(updates = listOf(update(index = 22, color = Color.Green)), colors = emptyArray())
            delay(timeMillis = 20L)

            assertFalse(actual = device.isOutputHealthy)
            assertTrue(actual = output.isOpen)
            assertEquals(expected = 1, actual = output.attempts.get())
        } finally {
            device.close()
        }
    }

    private fun device(output: AmethystMidiOutput): LaunchpadDevice = EncodingDevice(
        connection = AmethystMidiDeviceConnection(
            device = FakeMidiDevice(id = "led-test", name = "LED Test"),
            input = FakeMidiInput(portId = "led-test-in"),
            output = output,
        ),
    )

    private fun update(index: Int, color: Color): RawLEDUpdate = RawLEDUpdate(index = index, color = color)

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) {
                return
            }
            delay(timeMillis = 10L)
        }
        assertTrue(actual = predicate())
    }
}

private class EncodingDevice(connection: AmethystMidiDeviceConnection) : LaunchpadDevice(
    connection = connection,
    firmware = LaunchpadFirmware.Original,
) {
    override fun clear() = Unit

    override fun encodeUpdate(updates: List<RawLEDUpdate>): List<ByteArray> = listOf(
        updates.flatMap { update ->
            listOf(
                update.index,
                (update.color.red * 255).toInt().toByte(),
                (update.color.green * 255).toInt().toByte(),
                (update.color.blue * 255).toInt().toByte(),
            )
        }.toByteArray(),
    )

    override fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray = byteArrayOf()
}

private class RecordingOutput(
    private val blockFirstSend: Boolean = false,
    private val failSend: Boolean = false,
    private val failClose: Boolean = false,
) : AmethystMidiOutput {
    override val portId = "led-test-out"
    @Volatile
    override var isOpen = true
    val firstSend = CountDownLatch(1)
    val resumeSend = CountDownLatch(1)
    val sends = AtomicInteger()
    val attempts = AtomicInteger()
    val colors = IntArray(101)

    override fun send(data: ByteArray) {
        val attempt = attempts.incrementAndGet()
        check(!failSend) { "injected send failure" }
        if (blockFirstSend && attempt == 1) {
            firstSend.countDown()
            check(resumeSend.await(2, TimeUnit.SECONDS))
        }
        data.toList().chunked(size = 4).forEach { update ->
            colors[update[0].toInt() and 0xFF] = ((update[1].toInt() and 0xFF) shl 16) or
                ((update[2].toInt() and 0xFF) shl 8) or (update[3].toInt() and 0xFF)
        }
        sends.incrementAndGet()
    }

    override fun sendSysEx(data: ByteArray) = send(data = data)

    override fun sendDeviceInquiry() = Unit

    override fun close() {
        check(!failClose) { "injected close failure" }
        isOpen = false
    }
}
