package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.engine.echo.AudioSourcePlayback
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionClipContext
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompositionAudioPreviewTest {
    private val context = CompositionClipContext(clipId = "composition", startTimeMs = 3_000L, durationMs = 2_000L)

    @Test
    fun rangeRequestIncludesSourceTrimAndStopsAtCompositionBoundary() {
        val entry = audioEntry(startMs = 1_000L, durationMs = 10_000L, sampleRate = 48_000, trim = 24_000L)
        val request = assertNotNull(actual = buildCompositionAudioRequest(
            entry = entry,
            totalSamples = 504_000L,
            positionMs = 3_500L,
            rangeEndMs = 5_000L,
            gain = 0.4f,
            origin = "preview",
        ))
        assertEquals(expected = 144_000L, actual = request.startFrame)
        assertEquals(expected = 216_000L, actual = request.endFrameExclusive)
        assertEquals(expected = 0.4f, actual = request.gain)
    }

    @Test
    fun rangeRequestHonorsPreciseClipBoundaryAndSourceLength() {
        val entry = audioEntry(startMs = 1_000L, durationMs = 2_000L, sampleRate = 44_100)
            .copy(startTimeUs = 1_000_500L, durationUs = 1_999_500L)
        val request = assertNotNull(actual = buildCompositionAudioRequest(
            entry = entry,
            totalSamples = 88_000L,
            positionMs = 1_001L,
            rangeEndMs = 4_000L,
            gain = 1f,
            origin = null,
        ))
        assertEquals(expected = 22L, actual = request.startFrame)
        assertEquals(expected = 88_000L, actual = request.endFrameExclusive)
        assertNull(actual = buildCompositionAudioRequest(
            entry = entry,
            totalSamples = 88_000L,
            positionMs = 3_000L,
            rangeEndMs = 4_000L,
            gain = 1f,
            origin = null,
        ))
    }

    @Test
    fun previewStartsLaterClipsOnceAndLeavesGapsSilent() {
        val first = audioEntry(startMs = 2_000L, durationMs = 1_500L)
        val later = audioEntry(startMs = 4_000L, durationMs = 2_000L)
        val fixture = Fixture(tracks = listOf(audioTrack(entries = listOf(first, later))))
        fixture.preview.sync(context = context, positionMs = 3_000L)
        fixture.preview.sync(context = context, positionMs = 3_100L)
        assertEquals(expected = 1, actual = fixture.requests.size)
        assertEquals(expected = 1_000L, actual = fixture.requests.single().startFrame)
        fixture.preview.sync(context = context, positionMs = 3_500L)
        assertEquals(expected = listOf("voice-1"), actual = fixture.stoppedIds)
        fixture.preview.sync(context = context, positionMs = 3_900L)
        assertEquals(expected = 1, actual = fixture.requests.size)
        fixture.preview.sync(context = context, positionMs = 4_000L)
        assertEquals(expected = 2, actual = fixture.requests.size)
        assertEquals(expected = 1_000L, actual = fixture.requests.last().endFrameExclusive)
        fixture.preview.sync(context = context, positionMs = 5_000L)
        assertEquals(expected = 1, actual = fixture.stoppedOrigins.size)
    }

    @Test
    fun pauseSeekAndRepeatReuseOnlyThePreviewOrigin() {
        val fixture = Fixture(tracks = listOf(audioTrack(entries = listOf(audioEntry(startMs = 1_000L, durationMs = 8_000L)))))
        fixture.preview.sync(context = context, positionMs = 3_000L)
        fixture.preview.stop()
        fixture.preview.sync(context = context, positionMs = 4_500L)
        assertEquals(expected = 3_500L, actual = fixture.requests.last().startFrame)
        fixture.preview.stop()
        fixture.preview.sync(context = context, positionMs = 3_000L)
        fixture.preview.stop()
        fixture.preview.stop()
        assertEquals(expected = 3, actual = fixture.stoppedOrigins.size)
        val origin = assertNotNull(actual = fixture.requests.first().origin)
        fixture.requests.forEach { request -> assertSame(expected = origin, actual = request.origin) }
        fixture.stoppedOrigins.forEach { stopped -> assertSame(expected = origin, actual = stopped) }
    }

    @Test
    fun muteSoloAndAbsoluteVolumeAutomationApplyWhilePreviewing() {
        val track = audioTrack(entries = listOf(audioEntry(startMs = 1_000L, durationMs = 8_000L)))
        track.automationLanes.add(element = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(timeMs = 3_000L, value = 0.25f),
                TimelineAutomationPoint(timeMs = 4_000L, value = 0.5f),
            ),
        ))
        val fixture = Fixture(tracks = listOf(track))
        fixture.preview.sync(context = context, positionMs = 3_000L)
        assertEquals(expected = 0.25f, actual = fixture.requests.single().gain)
        fixture.preview.sync(context = context, positionMs = 4_000L)
        assertEquals(expected = 0.5f, actual = fixture.updates.last().second, absoluteTolerance = 0.000001f)
        track.isMuted = true
        fixture.preview.sync(context = context, positionMs = 4_100L)
        assertEquals(expected = 1, actual = fixture.stoppedIds.size)
        track.isMuted = false
        val other = MidiTimelineTrack().apply { isSoloed = true }
        fixture.tracks = listOf(track, other)
        fixture.preview.sync(context = context, positionMs = 4_200L)
        assertEquals(expected = 1, actual = fixture.requests.size)
        track.isSoloed = true
        fixture.preview.sync(context = context, positionMs = 4_300L)
        assertEquals(expected = 2, actual = fixture.requests.size)
    }

    @Test
    fun distinctPreviewSessionsDoNotShareOriginsAndFailedVoicesCanRetry() {
        val track = audioTrack(entries = listOf(audioEntry(startMs = 1_000L, durationMs = 8_000L)))
        val first = Fixture(tracks = listOf(track))
        val second = Fixture(tracks = listOf(track))
        first.failPlayback = true
        first.preview.sync(context = context, positionMs = 3_000L)
        first.failPlayback = false
        first.preview.sync(context = context, positionMs = 3_100L)
        second.preview.sync(context = context, positionMs = 3_100L)
        assertEquals(expected = 2, actual = first.requests.size)
        assertTrue(actual = first.requests.last().origin !== second.requests.single().origin)
        first.preview.stop()
        second.preview.sync(context = context, positionMs = 3_200L)
        assertEquals(expected = 1, actual = second.requests.size)
        assertTrue(actual = second.stoppedOrigins.isEmpty())
    }

    private fun audioEntry(startMs: Long, durationMs: Long, sampleRate: Int = 1_000, trim: Long = 0L): AudioEntry {
        return AudioEntry(
            startTimeMs = startMs,
            durationMs = durationMs,
            sourceId = "source-$startMs",
            fileName = "$startMs.wav",
            sampleRate = sampleRate,
            clipStartSample = trim,
            clipEndSample = trim + durationMs * sampleRate / 1_000L,
        )
    }

    private fun audioTrack(entries: List<AudioEntry>): AudioTimelineTrack {
        return AudioTimelineTrack().apply {
            entries.forEach { entry -> this.entries[entry.startTimeMs] = entry }
        }
    }

    private class Fixture(var tracks: List<TimelineTrack<*>>) {
        val requests = mutableListOf<AudioSourcePlayback>()
        val updates = mutableListOf<Pair<String, Float>>()
        val stoppedIds = mutableListOf<String>()
        val stoppedOrigins = mutableListOf<Any>()
        var failPlayback = false
        val preview = CompositionAudioPreview(
            tracksProvider = { tracks },
            totalSamplesProvider = { it.clipEndSample },
            prepareSources = {},
            playSources = { batch ->
                batch.map { request ->
                    requests.add(element = request)
                    if (failPlayback) {
                        null
                    } else {
                        "voice-${requests.size}"
                    }
                }
            },
            updateVoice = { id, gain -> updates.add(element = id to gain) },
            stopVoice = { id -> stoppedIds.add(element = id) },
            stopOrigin = { origin -> stoppedOrigins.add(element = origin) },
        )
    }
}
