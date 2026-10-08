package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PianoRollFramePreviewTest {
    @Test
    fun snapshotIncludesLongNotesAtTheirCurrentGradientPhaseAndAllDevices() {
        val notes = listOf(
            MidiNote.withPaint(
                device = 0,
                pitch = 11,
                color = Color.Red,
                startTimeMs = 100L,
                durationMs = 1000L,
                gradient = listOf(
                    NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
                    NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f),
                ),
            ),
            MidiNote.withColor(device = 1, pitch = 11, color = Color.Green, startTimeMs = 500L, durationMs = 500L),
            MidiNote.withColor(device = 0, pitch = 12, color = Color.Blue, startTimeMs = 700L, durationMs = 100L),
        )
        val frame = pianoRollFrameColors(notes = notes, timeMs = 600L, clipDurationMs = 2000L)
        assertEquals(expected = setOf(0 to 11, 1 to 11), actual = frame.keys)
        assertEquals(expected = 0.5f, actual = frame.getValue(key = 0 to 11).red, absoluteTolerance = 1f / 255f)
        assertEquals(expected = 0.5f, actual = frame.getValue(key = 0 to 11).blue, absoluteTolerance = 1f / 255f)
        assertEquals(expected = Color.Green, actual = frame.getValue(key = 1 to 11))
        assertTrue(actual = pianoRollFrameColors(notes = notes, timeMs = 1100L, clipDurationMs = 2000L).isEmpty())
        assertTrue(actual = pianoRollFrameColors(notes = notes, timeMs = 600L, clipDurationMs = 600L).isEmpty())
    }

    @Test
    fun overlappingNotesRespectLayerPriorityAndBlending() {
        val lower = MidiNote.withColor(
            device = 0,
            pitch = 11,
            color = Color.Blue,
            startTimeMs = 0L,
            durationMs = 1000L,
            layer = 1,
        )
        val upper = MidiNote.withColor(
            device = 0,
            pitch = 11,
            color = Color.Red,
            startTimeMs = 0L,
            durationMs = 1000L,
            layer = 2,
        )
        assertEquals(
            expected = Color.Red,
            actual = pianoRollFrameColors(notes = listOf(lower, upper), timeMs = 500L, clipDurationMs = 1000L)[0 to 11],
        )
        assertEquals(
            expected = Color.Magenta,
            actual = pianoRollFrameColors(
                notes = listOf(lower, upper.copy(led = upper.led.copy(blendingMode = Signal.LED.BlendingMode.Screen))),
                timeMs = 500L,
                clipDurationMs = 1000L,
            )[0 to 11],
        )
    }

    @Test
    fun sameLayerUsesTheMostRecentlyStartedActiveNote() {
        val early = MidiNote.withColor(device = 0, pitch = 11, color = Color.Red, startTimeMs = 0L, durationMs = 1000L)
        val later = MidiNote.withColor(device = 0, pitch = 11, color = Color.Blue, startTimeMs = 250L, durationMs = 500L)
        assertEquals(
            expected = Color.Blue,
            actual = pianoRollFrameColors(notes = listOf(later, early), timeMs = 500L, clipDurationMs = 1000L)[0 to 11],
        )
    }
}
