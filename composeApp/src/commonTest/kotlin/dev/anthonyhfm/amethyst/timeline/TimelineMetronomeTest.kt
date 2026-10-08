package dev.anthonyhfm.amethyst.timeline

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineMetronomeTest {
    @Test
    fun transportBeatsAndBarAccentsFollowTempoWithoutRepeatingTicks() {
        listOf(60.0, 120.0, 180.0).forEach { bpm ->
            val clock = TimelineMetronomeClock()
            assertEquals(0L, clock.start(positionMs = 0L, bpm = bpm))
            assertNull(clock.tick(positionMs = 0L, bpm = bpm))
            for (beat in 1L..8L) {
                val boundary = kotlin.math.ceil(beat * 60_000.0 / bpm).toLong()
                assertNull(clock.tick(positionMs = boundary - 1L, bpm = bpm))
                assertEquals(beat, clock.tick(positionMs = boundary, bpm = bpm))
                assertNull(clock.tick(positionMs = boundary, bpm = bpm))
            }
        }
    }

    @Test
    fun startingBetweenBeatsWaitsForNextBoundaryAndLateTicksDoNotBurst() {
        val clock = TimelineMetronomeClock()
        assertNull(clock.start(positionMs = 250L, bpm = 120.0))
        assertEquals(1L, clock.tickDelay(positionMs = 499L, bpm = 120.0))
        assertEquals(1L, clock.tick(positionMs = 500L, bpm = 120.0))
        assertEquals(5L, clock.tick(positionMs = 2_501L, bpm = 120.0))
        assertNull(clock.tick(positionMs = 2_501L, bpm = 120.0))
        assertEquals(8L, clock.tickDelay(positionMs = 2_501L, bpm = 120.0))
    }

    @Test
    fun stalledTransportSkipsStaleClicksAndContinuesOnTheNextBeat() {
        val clock = TimelineMetronomeClock()
        assertEquals(0L, clock.start(positionMs = 0L, bpm = 120.0))
        assertNull(clock.tick(positionMs = 2_750L, bpm = 120.0))
        assertEquals(6L, clock.tick(positionMs = 3_000L, bpm = 120.0))
        assertNull(clock.tick(positionMs = 3_000L, bpm = 120.0))
    }

    @Test
    fun pauseSeekAndResumeResetBeatState() {
        val clock = TimelineMetronomeClock()
        assertEquals(0L, clock.start(positionMs = 0L, bpm = 120.0))
        clock.stop()
        assertNull(clock.tick(positionMs = 500L, bpm = 120.0))
        assertNull(clock.start(positionMs = 750L, bpm = 120.0))
        assertEquals(2L, clock.tick(positionMs = 1_000L, bpm = 120.0))
        assertEquals(0L, clock.start(positionMs = 0L, bpm = 120.0))
        assertNull(clock.tick(positionMs = 0L, bpm = 120.0))
        assertEquals(4L, clock.start(positionMs = 2_000L, bpm = 120.0))
    }

    @Test
    fun tempoChangesResynchronizeToTheGridWithoutAnImmediateSecondClick() {
        val clock = TimelineMetronomeClock()
        assertEquals(0L, clock.start(positionMs = 0L, bpm = 120.0))
        assertEquals(1L, clock.tick(positionMs = 500L, bpm = 120.0))
        assertNull(clock.tick(positionMs = 500L, bpm = 60.0))
        assertNull(clock.tick(positionMs = 999L, bpm = 60.0))
        assertEquals(1L, clock.tick(positionMs = 1_000L, bpm = 60.0))
        assertNull(clock.tick(positionMs = 1_000L, bpm = 180.0))
        assertEquals(4L, clock.tick(positionMs = 1_334L, bpm = 180.0))
    }

    @Test
    fun clickPcmIsSignedLittleEndianAtTheOutputRateWithSilentEndpoints() {
        listOf(44_100, 48_000, 96_000).forEach { sampleRate ->
            val regular = timelineMetronomePcm(sampleRate = sampleRate, accented = false)
            val accent = timelineMetronomePcm(sampleRate = sampleRate, accented = true)
            assertEquals(kotlin.math.round(sampleRate * 0.035).toInt() * 2, regular.size)
            assertEquals(regular.size, accent.size)
            assertFalse(regular.contentEquals(other = accent))
            for (pcm in listOf(regular, accent)) {
                val samples = pcm.toList().chunked(size = 2).map { bytes ->
                    (bytes[0].toInt() and 0xff) or (bytes[1].toInt() shl 8)
                }
                assertEquals(0, samples.first())
                assertEquals(0, samples.last())
                assertTrue(samples.any { it > 0 })
                assertTrue(samples.any { it < 0 })
                assertTrue(samples.all { abs(it) <= 12_000 })
            }
        }
    }
}
