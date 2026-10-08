package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionClipContext
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CompositionClipDurationTest {
    @Test
    fun clipSourcesFollowTheirOwnPersistedDurationWithoutChangingPlaybackOptions() {
        val first = runtime(clipId = "short", startMs = 3_000L, durationMs = 4_000L)
        val second = runtime(clipId = "long", startMs = 12_000L, durationMs = 8_000L)
        val standalone = CompositionChainDevice()
        try {
            val source = first.source as CompositionChainDevice
            val originalOptions = source.state.value.playbackOptions
            val standaloneDuration = standalone.playbackDurationMs()
            assertEquals(expected = 4_000L, actual = source.playbackDurationMs())
            assertEquals(expected = 8_000L, actual = (second.source as CompositionChainDevice).playbackDurationMs())
            assertEquals(expected = TimelineDuration.Finite(milliseconds = 4_000L), actual = first.naturalDuration())
            first.updateEntryMetadata(updated = first.entry.copy(startTimeMs = 20_000L, durationMs = 12_000L))
            assertEquals(expected = 12_000L, actual = source.playbackDurationMs())
            assertEquals(expected = originalOptions, actual = source.state.value.playbackOptions)
            assertEquals(expected = 8_000L, actual = (second.source as CompositionChainDevice).playbackDurationMs())
            assertEquals(expected = standaloneDuration, actual = standalone.playbackDurationMs())
            assertFalse(actual = standalone.isTimelineClip())
        } finally {
            first.dispose()
            second.dispose()
            standalone.dispose()
        }
    }

    @Test
    fun clipContextMapsMiddleSeekAndMovedClipsToTheSameLocalProgress() {
        val context = CompositionClipContext(clipId = "seek", startTimeMs = 30_000L, durationMs = 8_000L)
        assertEquals(expected = 0.5f, actual = context.progressAt(positionMs = 34_000L))
        assertEquals(expected = 4_000L, actual = context.localTimeMs(progress = 0.5f))
        val moved = context.copy(startTimeMs = 50_000L)
        assertEquals(expected = 0.5f, actual = moved.progressAt(positionMs = 54_000L))
        assertEquals(expected = 0f, actual = moved.progressAt(positionMs = 49_000L))
        assertEquals(expected = 1f, actual = moved.progressAt(positionMs = 60_000L))
    }

    private fun runtime(clipId: String, startMs: Long, durationMs: Long): ChainEffectRuntime {
        return ChainEffectRuntime(
            entry = ChainEffectEntry(
                clipId = clipId,
                startTimeMs = startMs,
                durationMs = durationMs,
                source = CompositionChainDeviceState(),
            ),
            bpmProvider = { 120.0 },
            onStateOrDurationChanged = {},
        )
    }
}
