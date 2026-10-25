package dev.anthonyhfm.amethyst.workspace.audio.export

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.nativeengine.EchoEngine
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class AudioExportTest {
    @AfterTest
    fun reset() {
        SelectionManager.clear()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        ProjectPcmFiles.clear()
    }

    @Test
    fun rectangularMixIncludesOnlySelectedTracksAndClipsWithExactBounds() {
        val audio = source(id = "tone", samples = FloatArray(size = 10) { 0.25f })
        val first = track(source = audio, atMs = 1L).apply { volume = 0.5f }
        val second = track(source = audio, atMs = 3L).apply { volume = 0.25f }
        val outside = track(source = audio)
        val snapshot = assertNotNull(actual = captureTimelineExport(
            tracks = listOf(first, second, outside),
            selections = listOf(range(track = 0, start = 0L, end = 8L), range(track = 1, start = 0L, end = 8L)),
            sourceProvider = { audio },
        ))
        val mixed = render(snapshot = snapshot)
        assertEquals(expected = 8 * 2, actual = mixed.size)
        assertEquals(expected = 0f, actual = mixed[0])
        assertEquals(expected = 0.125f, actual = mixed[2], absoluteTolerance = 0.000001f)
        assertEquals(expected = 0.1875f, actual = mixed[6], absoluteTolerance = 0.000001f)
        assertEquals(expected = mixed[6], actual = mixed[7])
    }

    @Test
    fun selectedClipsDoNotPullInUnselectedOverlappingAudioAndLeaveGaps() {
        val audio = source(id = "tone", samples = FloatArray(size = 2) { 0.25f })
        val selected = track(source = audio).apply { entries[6L] = entry(source = audio, atMs = 6L) }
        val snapshot = assertNotNull(actual = captureTimelineExport(
            tracks = listOf(selected, track(source = audio)),
            selections = listOf(clip(track = 0, start = 0L), clip(track = 0, start = 6L)),
            sourceProvider = { audio },
        ))
        val mixed = render(snapshot = snapshot)
        assertEquals(expected = 16, actual = mixed.size)
        assertEquals(expected = 0.25f, actual = mixed.first())
        assertEquals(expected = 0f, actual = mixed[4])
        assertEquals(expected = 0.25f, actual = mixed.last())
    }

    @Test
    fun automationUsesAbsoluteProjectTimeAndSnapshotIgnoresSubsequentEdits() {
        val audio = source(id = "tone", samples = FloatArray(size = 10) { 0.5f })
        val track = track(source = audio).apply {
            automationLanes.add(element = TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                points = listOf(TimelineAutomationPoint(timeMs = 0L, value = 0.5f), TimelineAutomationPoint(timeMs = 8L, value = 0.25f)),
            ))
        }
        val snapshot = assertNotNull(actual = captureTimelineExport(tracks = listOf(track), selections = listOf(range(track = 0, start = 3L, end = 7L)), sourceProvider = { audio }))
        val expectedGain = track.volumeAt(timeMs = 3L)
        track.isMuted = true
        track.automationLanes.clear()
        val mixed = render(snapshot = snapshot)
        assertEquals(expected = 0.5f * expectedGain, actual = mixed.first(), absoluteTolerance = 0.000001f)
        assertTrue(actual = mixed.first() > mixed.last())
    }

    @Test
    fun muteAndGlobalSoloAlsoApplyWhenTheSoloedTrackIsOutsideSelection() {
        val audio = source(id = "tone", samples = FloatArray(size = 10) { 0.5f })
        val first = track(source = audio)
        val soloed = MidiTimelineTrack().apply { isSoloed = true }
        val snapshot = assertNotNull(actual = captureTimelineExport(tracks = listOf(first, soloed), selections = listOf(range(track = 0, start = 0L, end = 10L)), sourceProvider = { audio }))
        assertFalse(actual = snapshot.hasAudibleClips)
        soloed.isSoloed = false
        first.entries[0L] = first.entries.getValue(key = 0L).copy(isMuted = true)
        assertFalse(actual = assertNotNull(actual = captureTimelineExport(tracks = listOf(first), selections = null, sourceProvider = { audio })).hasAudibleClips)
        first.isMuted = true
        assertFalse(actual = assertNotNull(actual = captureTimelineExport(tracks = listOf(first), selections = null, sourceProvider = { audio })).hasAudibleClips)
    }

    @Test
    fun limiterDoesNotAddSilenceOrLoseTheFirstAndLastSamples() {
        val audio = source(id = "impulse", samples = floatArrayOf(0.75f, 0f, 0f, 0.5f))
        val snapshot = assertNotNull(actual = captureTimelineExport(tracks = listOf(track(source = audio)), selections = null, sourceProvider = { audio }))
        assertContentEquals(expected = floatArrayOf(0.75f, 0.75f, 0f, 0f, 0f, 0f, 0.5f, 0.5f), actual = render(snapshot = snapshot))
    }

    @Test
    fun microsecondClipOriginsUseTheSameRoundedSourceOffsetsAsTimelineTrimming() {
        val audio = source(id = "precise", samples = FloatArray(size = 1000) { it / 1024f }, sampleRate = 44_100)
        val precise = entry(source = audio).copyWithPreciseTiming(
            startTimeUs = 1025L,
            durationUs = samplesToUs(sampleCount = 995L, sampleRate = 44_100),
            clipStartSample = 5L,
        )
        val track = AudioTimelineTrack().apply { entries[precise.startTimeMs] = precise }
        val snapshot = assertNotNull(actual = captureTimelineExport(tracks = listOf(track), selections = listOf(range(track = 0, start = 2L, end = 3L)), sourceProvider = { audio }))
        val values = mutableListOf<Float>()
        renderAudioExport(snapshot = snapshot, options = AudioExportOptions(sampleRate = 44_100), checkpoint = {}) { buffer, offset, count ->
            repeat(times = count * 2) { index -> values.add(element = buffer[offset + index]) }
        }
        assertEquals(expected = 88, actual = values.size)
        assertEquals(expected = 48f / 1024f, actual = values.first(), absoluteTolerance = 0.000001f)
    }

    @Test
    fun differentSampleRatesMixWithoutChangingPitchOrLevel() {
        val first = source(id = "low-rate", samples = FloatArray(size = 100) { 0.125f })
        val second = source(id = "high-rate", samples = FloatArray(size = 200) { 0.25f }, sampleRate = 2000)
        val snapshot = assertNotNull(actual = captureTimelineExport(
            tracks = listOf(track(source = first), track(source = second)),
            selections = listOf(range(track = 0, start = 20L, end = 40L), range(track = 1, start = 20L, end = 40L)),
            sourceProvider = { if (it == first.id) first else second },
        ))
        val mixed = render(snapshot = snapshot)
        assertEquals(expected = 40, actual = mixed.size)
        assertTrue(actual = mixed.all { abs(it - 0.375f) < 0.0001f })
    }

    @Test
    fun waveHasCorrectHeaderPcmAndOddBytePadding() {
        val audio = source(id = "mono", samples = floatArrayOf(0.25f, -0.25f, 0.5f))
        val snapshot = sourceSnapshot(source = audio)
        val dir = Files.createTempDirectory("amethyst-wave-test")
        try {
            val path = dir.resolve("mono.wav")
            writeAudioExport(snapshot = snapshot, options = AudioExportOptions(sampleRate = 1000), destination = path)
            val bytes = Files.readAllBytes(path)
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(expected = 54, actual = bytes.size)
            assertEquals(expected = 46, actual = header.getInt(4))
            assertEquals(expected = 9, actual = header.getInt(40))
            assertEquals(expected = 1, actual = header.getShort(22).toInt())
            assertEquals(expected = 1000, actual = header.getInt(24))
            assertContentEquals(expected = audio.rawData, actual = bytes.copyOfRange(fromIndex = 44, toIndex = 53))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun mp3RoundTripsWithDurationMetadataAndBothChannels() {
        val rate = 44_100
        val samples = FloatArray(size = rate * 2) { index ->
            (sin(2.0 * PI * 440.0 * (index / 2) / rate) * if (index % 2 == 0) 0.25 else 0.125).toFloat()
        }
        val audio = source(id = "stereo", samples = samples, sampleRate = rate, channels = 2)
        val dir = Files.createTempDirectory("amethyst-mp3-test")
        try {
            val path = dir.resolve("stereo.mp3")
            writeAudioExport(snapshot = sourceSnapshot(source = audio), options = AudioExportOptions(format = AudioExportFormat.MP3, sampleRate = rate), destination = path)
            val bytes = Files.readAllBytes(path)
            assertTrue(actual = bytes.toString(charset = Charsets.ISO_8859_1).contains(other = "Info"))
            EchoEngine().use { decoder ->
                val result = decoder.decodeFile(path.toString())
                val decoded = assertNotNull(actual = result.buffer, message = result.error)
                assertEquals(expected = rate, actual = decoded.sampleRate.toInt())
                assertEquals(expected = 2, actual = decoded.channels.toInt())
                assertTrue(actual = abs(decoded.pcm24.size / 6 - rate) < 3000)
                assertTrue(actual = decoded.pcm24.any { it != 0.toByte() })
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancellationPreservesExistingDestinationAndRemovesTemporaryOutput() {
        val audio = source(id = "long", samples = FloatArray(size = 10000) { 0.1f })
        val dir = Files.createTempDirectory("amethyst-cancel-test")
        try {
            val path = dir.resolve("existing.wav")
            Files.write(path, byteArrayOf(1, 2, 3))
            assertFailsWith<CancellationException> {
                writeAudioExport(snapshot = sourceSnapshot(source = audio), options = AudioExportOptions(sampleRate = 1000), destination = path) { progress ->
                    if (progress > 0.1f) {
                        throw CancellationException()
                    }
                }
            }
            assertContentEquals(expected = byteArrayOf(1, 2, 3), actual = Files.readAllBytes(path))
            Files.list(dir).use { files -> assertEquals(expected = 1L, actual = files.count()) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun pinnedPcmSurvivesWorkspaceCleanupUntilExportReleasesIt() {
        val storage = ProjectPcmFiles.store(bytes = ByteArray(size = 100))
        val path = java.nio.file.Paths.get(assertNotNull(actual = storage.filePath))
        val release = ProjectPcmFiles.pin(paths = setOf(path.toString()))
        ProjectPcmFiles.clear()
        assertTrue(actual = Files.exists(path))
        release()
        assertFalse(actual = Files.exists(path))
    }

    @Test
    fun independentPcmPinsReleaseOnceAndKeepSourcesRetainedByTheWorkspace() {
        val storage = ProjectPcmFiles.store(bytes = ByteArray(size = 100))
        val path = java.nio.file.Paths.get(assertNotNull(actual = storage.filePath))
        val first = ProjectPcmFiles.pin(paths = setOf(path.toString()))
        val second = ProjectPcmFiles.pin(paths = setOf(path.toString()))
        ProjectPcmFiles.clear()
        first()
        first()
        assertTrue(actual = Files.exists(path))
        ProjectPcmFiles.retain(paths = setOf(path.toString()))
        second()
        assertTrue(actual = Files.exists(path))
        ProjectPcmFiles.clear()
        assertFalse(actual = Files.exists(path))
    }

    @Test
    fun rightClickInAnEmptySelectedLaneKeepsTheWholeRectangle() {
        val audio = source(id = "tone", samples = FloatArray(size = 100) { 0.1f })
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track(source = audio), AudioTimelineTrack()))
        val selections = listOf(range(track = 0, start = 0L, end = 100L), range(track = 1, start = 0L, end = 100L))
        SelectionManager.replaceSelections(updatedSelections = selections)
        assertTrue(actual = TimelineCommandSurface.selectAudioContext(trackIndex = 1, timeMs = 50.0))
        assertEquals(expected = selections, actual = SelectionManager.selections.value)
    }

    private fun render(snapshot: AudioExportSnapshot): FloatArray {
        val values = mutableListOf<Float>()
        renderAudioExport(snapshot = snapshot, options = AudioExportOptions(sampleRate = 1000), checkpoint = {}) { buffer, offset, count ->
            repeat(times = count * snapshot.channels) { index -> values.add(element = buffer[offset + index]) }
        }
        return values.toFloatArray()
    }

    private fun source(id: String, samples: FloatArray, sampleRate: Int = 1000, channels: Int = 1): AudioSource {
        val bytes = ByteArray(size = samples.size * 3)
        samples.forEachIndexed { index, sample ->
            val value = (sample * 8388608f).toInt()
            repeat(times = 3) { byte -> bytes[index * 3 + byte] = (value shr (byte * 8)).toByte() }
        }
        return AudioSource(id = id, fileName = "$id.wav", rawData = bytes, sampleRate = sampleRate, channels = channels, bitDepth = 24)
    }

    private fun entry(source: AudioSource, atMs: Long = 0L): AudioEntry = AudioEntry(
        startTimeMs = atMs,
        durationMs = source.totalDurationMs,
        fileName = source.fileName,
        sourceId = source.id,
        clipStartSample = 0L,
        clipEndSample = source.totalSamples,
        sampleRate = source.sampleRate,
        channels = source.channels,
        bitDepth = source.bitDepth,
    ).copyWithPreciseTiming(startTimeUs = atMs * 1000L, durationUs = samplesToUs(sampleCount = source.totalSamples, sampleRate = source.sampleRate))

    private fun track(source: AudioSource, atMs: Long = 0L): AudioTimelineTrack = AudioTimelineTrack().apply {
        entries[atMs] = entry(source = source, atMs = atMs)
    }

    private fun sourceSnapshot(source: AudioSource): AudioExportSnapshot = AudioExportSnapshot(
        clips = listOf(AudioExportClip(entry = entry(source = source), source = source, track = null, startUs = 0L, endUs = samplesToUs(sampleCount = source.totalSamples, sampleRate = source.sampleRate), audible = true)),
        startUs = 0L,
        endUs = samplesToUs(sampleCount = source.totalSamples, sampleRate = source.sampleRate),
        name = source.fileName,
        channels = source.channels,
        originalSampleRate = source.sampleRate,
    )

    private fun range(track: Int, start: Long, end: Long): Selectable.TimelineRange = Selectable.TimelineRange(trackIndex = track, startMs = start, endMs = end)

    private fun clip(track: Int, start: Long): Selectable.TimelineEntryItem = Selectable.TimelineEntryItem(trackIndex = track, entryStartMs = start)
}
