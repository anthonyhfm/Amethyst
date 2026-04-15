package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.serialization.Serializable

@Serializable
enum class TimelineTrackKind {
    AUDIO,
    MIDI
}
