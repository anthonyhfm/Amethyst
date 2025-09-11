package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.serialization.Serializable

interface TimelineEntry {
    val startTimeMs: Long
    val durationMs: Long
    val endTimeMs: Long get() = startTimeMs + durationMs

    fun start()
    fun stop()
}

@Serializable
data class AudioEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
    val fileName: String,
    val rawData: ByteArray?,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16
) : TimelineEntry {
    override fun start() {
        // Audio playback start logic
    }

    override fun stop() {
        // Audio playback stop logic
    }
}

@Serializable
data class LightEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
) : TimelineEntry {
    override fun start() {
        // Light playback start logic
    }

    override fun stop() {
        // Light playback stop logic
    }
}
