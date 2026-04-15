package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val TimelineSerializationModule = SerializersModule {
    polymorphic(TimelineTrack::class) {
        subclass(AudioTimelineTrack::class)
        subclass(MidiTimelineTrack::class)
    }
}
