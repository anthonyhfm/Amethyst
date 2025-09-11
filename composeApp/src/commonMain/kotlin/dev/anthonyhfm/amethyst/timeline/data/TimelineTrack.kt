package dev.anthonyhfm.amethyst.timeline.data

abstract class TimelineTrack <E: TimelineEntry> {
    open val entries = mutableMapOf<Long, E>(
        // startTimeMs to Entry
    )
}