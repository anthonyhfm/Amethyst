package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ableton.AbletonNoteSpace
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class KeyframesMemoryTest {
    @Test
    fun animationSignalsSharePitchMetadataAcrossColorChangesAndNoteOff() {
        val device = KeyframesChainDevice()
        try {
            device.state.value = KeyframesChainDeviceState(
                frames = List(3) { index ->
                    Frame(
                        timing = Timing.Duration(duration = 100.milliseconds),
                        entries = listOf(KeyframesEntry(
                            x = 11,
                            y = 8,
                            r = (index + 1) / 3f,
                            g = 0f,
                            b = 0f,
                            abletonPitch = 36,
                            localX = 1,
                            localY = 8,
                        )),
                    )
                },
            )
            device.renderAnimation()
            val animation = device.state.value.renderedAnimation
            val signals = animation.flatMap { it.second }.filterIsInstance<Signal.LED>()
            assertEquals(expected = listOf(0, 100, 200, 300), actual = animation.map { it.first })
            assertEquals(expected = 4, actual = signals.size)
            val metadata = signals.first().extras
            signals.forEach { signal ->
                assertSame(expected = metadata, actual = signal.extras)
                assertEquals(expected = 11, actual = signal.x)
                assertEquals(expected = 8, actual = signal.y)
                assertEquals(expected = 10, actual = AbletonNoteSpace.note(signal = signal)?.targetX)
            }
            assertTrue(actual = signals.first().color != Color.Black)
            assertEquals(expected = Color.Black, actual = signals.last().color)
        } finally {
            device.dispose()
        }
    }
}
