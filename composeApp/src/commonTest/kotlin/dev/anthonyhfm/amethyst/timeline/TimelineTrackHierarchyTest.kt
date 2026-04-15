package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineTrackHierarchyTest {
    @Test
    fun timelineTrackRowsReturnsFlatListInOrder() {
        val audioTrack = AudioTimelineTrack().apply { trackId = "audio-main" }
        val midiTrack = MidiTimelineTrack().apply { trackId = "midi-main" }
        val audioTrack2 = AudioTimelineTrack().apply { trackId = "audio-2" }

        val rows = listOf(audioTrack, midiTrack, audioTrack2).timelineTrackRows()

        assertEquals(listOf("audio-main", "midi-main", "audio-2"), rows.map { it.track.trackId })
        assertEquals(listOf(0, 1, 2), rows.map { it.trackIndex })
        assertEquals(listOf(0, 0, 0), rows.map { it.nestingLevel })
    }

    @Test
    fun timelineTrackRowsHandlesEmptyList() {
        val rows = emptyList<dev.anthonyhfm.amethyst.timeline.data.TimelineTrack<*>>().timelineTrackRows()
        assertEquals(0, rows.size)
    }
}
