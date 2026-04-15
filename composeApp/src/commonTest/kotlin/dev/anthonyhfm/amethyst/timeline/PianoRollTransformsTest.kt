package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.transforms.PianoRollTransforms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DELTA = 0.001f

private fun note(pitch: Int, startMs: Long = 0L, durationMs: Long = 100L) = MidiNote(
    device = 0, pitch = pitch,
    led = MidiNote.NoteLED(index = pitch, red = 1f, green = 1f, blue = 1f),
    startTimeMs = startMs, durationMs = durationMs
)

class PianoRollTransformsTest {

    @Test
    fun doubleLength_durationBecomesDouble() {
        val notes = listOf(note(0, durationMs = 200L), note(1, durationMs = 50L))
        val result = PianoRollTransforms.doubleLength(notes)
        assertEquals(400L, result[0].durationMs)
        assertEquals(100L, result[1].durationMs)
    }

    @Test
    fun halveLength_durationBecomesHalf() {
        val notes = listOf(note(0, durationMs = 200L), note(1, durationMs = 100L))
        val result = PianoRollTransforms.halveLength(notes)
        assertEquals(100L, result[0].durationMs)
        assertEquals(50L, result[1].durationMs)
    }

    @Test
    fun halveLength_minimumGuard_staysAtOne() {
        val notes = listOf(note(0, durationMs = 1L))
        val result = PianoRollTransforms.halveLength(notes)
        assertEquals(1L, result[0].durationMs)
    }

    @Test
    fun shiftLeft_pitchDecreasesBy1() {
        val notes = listOf(note(5), note(12))
        val result = PianoRollTransforms.shiftLeft(notes)
        assertEquals(4, result[0].pitch)
        assertEquals(11, result[1].pitch)
    }

    @Test
    fun shiftRight_pitchIncreasesBy1() {
        val notes = listOf(note(5), note(12))
        val result = PianoRollTransforms.shiftRight(notes)
        assertEquals(6, result[0].pitch)
        assertEquals(13, result[1].pitch)
    }

    @Test
    fun shiftUp_pitchIncreasesBy10() {
        val notes = listOf(note(0), note(5), note(23))
        val result = PianoRollTransforms.shiftUp(notes)
        assertEquals(10, result[0].pitch)
        assertEquals(15, result[1].pitch)
        assertEquals(33, result[2].pitch)
    }

    @Test
    fun shiftDown_pitchDecreasesBy10() {
        val notes = listOf(note(20), note(35), note(10))
        val result = PianoRollTransforms.shiftDown(notes)
        assertEquals(10, result[0].pitch)
        assertEquals(25, result[1].pitch)
        assertEquals(0, result[2].pitch)
    }

    @Test
    fun mirrorHorizontal_swapsColumnsWithinBoundingBox() {
        // pitch 0 = col 0, row 0; pitch 9 = col 9, row 0
        val n0 = note(0, startMs = 0L)
        val n9 = note(9, startMs = 100L)
        val result = PianoRollTransforms.mirrorHorizontal(listOf(n0, n9))
        val resultFor0 = result.first { it.startTimeMs == 0L }
        val resultFor9 = result.first { it.startTimeMs == 100L }
        assertEquals(9, resultFor0.pitch)
        assertEquals(0, resultFor9.pitch)
    }

    @Test
    fun mirrorVertical_swapsRowsWithinBoundingBox() {
        // pitch 0 = col 0, row 0; pitch 90 = col 0, row 9
        val n0 = note(0, startMs = 0L)
        val n90 = note(90, startMs = 100L)
        val result = PianoRollTransforms.mirrorVertical(listOf(n0, n90))
        val resultFor0 = result.first { it.startTimeMs == 0L }
        val resultFor90 = result.first { it.startTimeMs == 100L }
        assertEquals(90, resultFor0.pitch)
        assertEquals(0, resultFor90.pitch)
    }

    @Test
    fun rotateCW_twoNotesInRow_becomeTwoNotesInColumn() {
        // pitch 0 = col 0, row 0; pitch 1 = col 1, row 0
        val notes = listOf(note(0, startMs = 0L), note(1, startMs = 100L))
        val result = PianoRollTransforms.rotateCW(notes)
        val pitches = result.map { it.pitch }.toSet()
        // After CW rotation: should have pitches 0 and 10
        assertEquals(setOf(0, 10), pitches)
    }

    @Test
    fun rotate180_swapsOppositeCorners() {
        // pitch 0 (col=0, row=0) and pitch 11 (col=1, row=1) in a 2x2 selection
        val n0 = note(0, startMs = 0L)
        val n11 = note(11, startMs = 100L)
        val result = PianoRollTransforms.rotate180(listOf(n0, n11))
        val resultFor0 = result.first { it.startTimeMs == 0L }
        val resultFor11 = result.first { it.startTimeMs == 100L }
        assertEquals(11, resultFor0.pitch)
        assertEquals(0, resultFor11.pitch)
    }

    @Test
    fun gradientSpread_threeNotesGetColorsFromTwoStopGradient() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        val notes = listOf(
            note(0, startMs = 0L),
            note(1, startMs = 500L),
            note(2, startMs = 1000L)
        )
        val result = PianoRollTransforms.gradientSpread(notes, stops)
        // t=0: red
        assertEquals(1f, result[0].led.red, DELTA)
        assertEquals(0f, result[0].led.blue, DELTA)
        // t=0.5: purple
        assertEquals(0.5f, result[1].led.red, DELTA)
        assertEquals(0.5f, result[1].led.blue, DELTA)
        // t=1.0: blue
        assertEquals(0f, result[2].led.red, DELTA)
        assertEquals(1f, result[2].led.blue, DELTA)
        // gradient should be cleared
        assertTrue(result.all { it.led.gradient == null })
    }
}
