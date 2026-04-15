package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.devices.audio.sample.sampleChainStateFromAudioEntry
import dev.anthonyhfm.amethyst.timeline.automation.TimelineTrackAutomationState
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.timeline.data.usToRoundedMs
import dev.anthonyhfm.amethyst.timeline.data.usToSamples
import dev.anthonyhfm.amethyst.timeline.utils.TimelineClipUtils
import dev.anthonyhfm.amethyst.ui.components.computeWaveformEnvelope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioTimelinePrecisionTest {
    private val sampleRate = 44_100
    private val channels = 2
    private val bitDepth = 16
    private val bytesPerSample = (bitDepth / 8) * channels
    private val totalSamples = 176_400L

    @BeforeTest
    fun setUp() {
        UndoManager.clear()
        AudioSourceLibrary.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @AfterTest
    fun tearDown() {
        UndoManager.clear()
        AudioSourceLibrary.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @Test
    fun audioCutKeepsTimelineBoundaryExact() {
        val track = createTrackWithSingleClip()

        assertTrue(
            TimelineClipUtils.cutAtSelection(
                Selectable.TimelineTime(trackIndex = 0, timeMs = 1_500L)
            )
        )

        val updatedTrack = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        val entries = updatedTrack.entries.values.sortedBy(AudioEntry::startTimeUs)
        val left = entries[0]
        val right = entries[1]
        val expectedCutSample = usToSamples(msToUs(1_500L), sampleRate)

        assertEquals(0L, left.startTimeMs)
        assertEquals(1_500L, right.startTimeMs)
        assertEquals(msToUs(1_500L), right.startTimeUs)
        assertEquals(expectedCutSample, left.clipEndSample)
        assertEquals(expectedCutSample, right.clipStartSample)
    }

    @Test
    fun repeatedAudioSplitsDoNotAccumulateDrift() {
        createTrackWithSingleClip()

        assertTrue(TimelineClipUtils.cutAtSelection(Selectable.TimelineTime(trackIndex = 0, timeMs = 1_000L)))
        assertTrue(TimelineClipUtils.cutAtSelection(Selectable.TimelineTime(trackIndex = 0, timeMs = 2_500L)))

        val updatedTrack = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        val entries = updatedTrack.entries.values.sortedBy(AudioEntry::startTimeUs)

        assertEquals(listOf(0L, 1_000L, 2_500L), entries.map(AudioEntry::startTimeMs))
        assertEquals(listOf(0L, 44_100L, 110_250L), entries.map(AudioEntry::clipStartSample))
        assertEquals(listOf(44_100L, 110_250L, totalSamples), entries.map(AudioEntry::clipEndSample))
    }

    @Test
    fun playbackSignalUsesPreciseSampleOffsetAfterCut() {
        createTrackWithSingleClip()
        assertTrue(TimelineClipUtils.cutAtSelection(Selectable.TimelineTime(trackIndex = 0, timeMs = 1_500L)))

        val updatedTrack = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        val right = updatedTrack.entries.values.sortedBy(AudioEntry::startTimeUs)[1]
        val signal = right.buildPlaybackSignal(
            startAt = 2_000L,
            automation = TimelineTrackAutomationState()
        )

        val nonNullSignal = assertNotNull(signal)
        val playbackOffsetUs = msToUs(2_000L) - right.startTimeUs
        val expectedStartSample = right.clipStartSample + usToSamples(playbackOffsetUs, sampleRate)
        val expectedRemainingSamples = right.clipEndSample - expectedStartSample

        assertEquals((expectedRemainingSamples * bytesPerSample).toInt(), nonNullSignal.rawData?.size)
        assertEquals(usToRoundedMs(samplesToUs(expectedRemainingSamples, sampleRate)), nonNullSignal.durationMs)
    }

    @Test
    fun samplingPasteUsesOnlyClipAudioSegment() {
        val source = AudioSource(
            id = "sample-source",
            fileName = "segment.wav",
            rawData = ByteArray(24) { it.toByte() },
            sampleRate = 1_000,
            channels = 2,
            bitDepth = 16
        )
        AudioSourceLibrary.add(source)

        val entry = AudioEntry(
            startTimeMs = 100L,
            durationMs = 3L,
            fileName = source.fileName,
            sourceId = source.id,
            clipStartSample = 2L,
            clipEndSample = 5L,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            startTimeUs = 100_000L,
            durationUs = 3_000L
        )

        val sampleState = assertNotNull(sampleChainStateFromAudioEntry(entry))

        assertContentEquals(
            byteArrayOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19),
            sampleState.rawData
        )
        assertEquals(3L, sampleState.totalDurationMs)
        assertEquals(0f, sampleState.startPosition)
        assertEquals(1f, sampleState.endPosition)
        assertTrue(sampleState.isLoaded)
    }

    @Test
    fun samplingPasteKeepsImportedVolumeAutomationLane() {
        val source = AudioSource(
            id = "sample-automation-source",
            fileName = "segment.wav",
            rawData = ByteArray(24) { it.toByte() },
            sampleRate = 1_000,
            channels = 2,
            bitDepth = 16
        )
        AudioSourceLibrary.add(source)

        val entry = AudioEntry(
            startTimeMs = 0L,
            durationMs = 3L,
            fileName = source.fileName,
            sourceId = source.id,
            clipStartSample = 0L,
            clipEndSample = 3L,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            startTimeUs = 0L,
            durationUs = 3_000L
        )
        val automationLane = dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane(
            target = dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint(timeMs = 0L, value = 1f),
                dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint(timeMs = 3L, value = 0.5f)
            )
        )

        val sampleState = assertNotNull(sampleChainStateFromAudioEntry(entry, automationLane))

        assertEquals(
            listOf(0L, 3L),
            sampleState.volumeAutomationLane?.points?.map(dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint::timeMs)
        )
    }

    @Test
    fun waveformEnvelopeKeepsBucketPhaseAfterSplit() {
        val risingSamples = FloatArray(20) { index -> index / 20f }

        val fullEnvelope = computeWaveformEnvelope(
            samples = risingSamples,
            startSample = 0L,
            endSample = 20L,
            zoomLevel = 0.4f,
            sampleRate = 1_000,
            timelineStartUs = 0L
        )
        val rightSplitEnvelope = computeWaveformEnvelope(
            samples = risingSamples,
            startSample = 6L,
            endSample = 20L,
            zoomLevel = 0.4f,
            sampleRate = 1_000,
            timelineStartUs = 6_000L
        )

        assertContentEquals(
            expected = fullEnvelope.copyOfRange(3, fullEnvelope.size),
            actual = rightSplitEnvelope.copyOfRange(1, rightSplitEnvelope.size)
        )
    }

    private fun createTrackWithSingleClip(): AudioTimelineTrack {
        val source = AudioSource(
            id = "source-1",
            fileName = "clip.wav",
            rawData = ByteArray((totalSamples * bytesPerSample).toInt()),
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth
        )
        AudioSourceLibrary.add(source)

        val track = AudioTimelineTrack().apply {
            entries[0L] = AudioEntry(
                startTimeMs = 0L,
                durationMs = 4_000L,
                fileName = source.fileName,
                sourceId = source.id,
                clipStartSample = 0L,
                clipEndSample = totalSamples,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                startTimeUs = 0L,
                durationUs = samplesToUs(totalSamples, sampleRate)
            )
        }

        TimelineRepository.loadTracks(listOf(track))
        return track
    }
}
