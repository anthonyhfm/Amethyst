package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DELTA = 0.001f

class GradientInterpolatorTest {

    private fun assertColor(expected: Triple<Float, Float, Float>, actual: Triple<Float, Float, Float>) {
        assertEquals(expected.first, actual.first, DELTA, "r mismatch")
        assertEquals(expected.second, actual.second, DELTA, "g mismatch")
        assertEquals(expected.third, actual.third, DELTA, "b mismatch")
    }

    @Test
    fun twoStopsLinear_atStart_returnsFirstStopColor() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        assertColor(Triple(1f, 0f, 0f), GradientInterpolator.interpolate(stops, 0f))
    }

    @Test
    fun twoStopsLinear_atEnd_returnsLastStopColor() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        assertColor(Triple(0f, 0f, 1f), GradientInterpolator.interpolate(stops, 1f))
    }

    @Test
    fun twoStopsLinear_atMidpoint_returnsMidpointColor() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        assertColor(Triple(0.5f, 0f, 0.5f), GradientInterpolator.interpolate(stops, 0.5f))
    }

    @Test
    fun singleStop_alwaysReturnsThatColor() {
        val stops = listOf(NoteGradientStop(position = 0f, r = 0.2f, g = 0.4f, b = 0.6f))
        assertColor(Triple(0.2f, 0.4f, 0.6f), GradientInterpolator.interpolate(stops, 0f))
        assertColor(Triple(0.2f, 0.4f, 0.6f), GradientInterpolator.interpolate(stops, 0.5f))
        assertColor(Triple(0.2f, 0.4f, 0.6f), GradientInterpolator.interpolate(stops, 1f))
    }

    @Test
    fun holdSmoothness_justBeforeNextStop_returnsPreviousStopColor() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f, smoothness = GradientSmoothness.Hold),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        // At t=0.5 (linearT=0.5 < 0.95), should stay at first stop color
        assertColor(Triple(1f, 0f, 0f), GradientInterpolator.interpolate(stops, 0.5f))
        // At t=0.9 (linearT=0.9 < 0.95), still previous color
        assertColor(Triple(1f, 0f, 0f), GradientInterpolator.interpolate(stops, 0.9f))
    }

    @Test
    fun tClampingBelowZero_sameAsZero() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        assertColor(GradientInterpolator.interpolate(stops, 0f), GradientInterpolator.interpolate(stops, -0.1f))
    }

    @Test
    fun tClampingAboveOne_sameAsOne() {
        val stops = listOf(
            NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
            NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f)
        )
        assertColor(GradientInterpolator.interpolate(stops, 1f), GradientInterpolator.interpolate(stops, 1.1f))
    }

    @Test
    fun emptyList_returnsBlack() {
        assertColor(Triple(0f, 0f, 0f), GradientInterpolator.interpolate(emptyList(), 0.5f))
    }
}
