@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceMenuCommandSurface
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimelineAudioConsolidationTest {
    @BeforeTest
    @AfterTest
    fun reset() {
        TimelineAudioConsolidation.reset()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        SelectionManager.clear()
        AudioLibraryRepository.clear()
        UndoManager.clear()
        ProjectPcmFiles.clear()
    }

    @Test
    fun adjacentClipsBecomeOneSampleAccurateClipAndUndoRestoresSelection() = runBlocking {
        val first = source(id = "first", values = IntArray(size = 10) { 100 + it })
        val second = source(id = "second", values = IntArray(size = 10) { 200 + it })
        install(entries = listOf(clip(source = first), clip(source = second, atMs = 10L)))
        val selection = listOf(selected(atMs = 0L), selected(atMs = 10L))
        SelectionManager.replaceSelections(updatedSelections = selection)

        assertTrue(actual = TimelineAudioConsolidation.consolidate(selections = selection))

        val consolidated = track().entries.values.single()
        val sourceId = consolidated.sourceId
        assertEquals(expected = 0L, actual = consolidated.clipStartSample)
        assertEquals(expected = 20L, actual = consolidated.clipEndSample)
        assertEquals(expected = 20_000L, actual = consolidated.durationUs)
        assertEquals(expected = "first", actual = consolidated.name)
        assertContentEquals(expected = first.rawData + second.rawData, actual = consolidated.source()!!.rawData)
        assertEquals(expected = 1, actual = SelectionManager.selections.value.size)

        UndoManager.undo()
        assertEquals(expected = setOf(0L, 10L), actual = track().entries.keys)
        assertEquals(expected = selection, actual = SelectionManager.selections.value)
        assertFalse(actual = UndoManager.canUndo())
        UndoManager.redo()
        assertEquals(expected = sourceId, actual = track().entries.values.single().sourceId)
        assertEquals(expected = 3, actual = AudioLibraryRepository.all().size)
    }

    @Test
    fun partialRangePreservesBothOuterPiecesAndSourceOffsets() = runBlocking {
        val first = source(id = "first", values = IntArray(size = 15) { 100 + it })
        val second = source(id = "second", values = IntArray(size = 20) { 200 + it })
        install(entries = listOf(clip(source = first, from = 3L, until = 13L), clip(source = second, atMs = 10L, from = 4L, until = 14L)))
        val selection = listOf(Selectable.TimelineRange(trackIndex = 0, startMs = 5L, endMs = 15L))

        TimelineAudioConsolidation.consolidate(selections = selection)

        assertEquals(expected = setOf(0L, 5L, 15L), actual = track().entries.keys)
        val left = track().entries.getValue(key = 0L)
        val right = track().entries.getValue(key = 15L)
        assertEquals(expected = first.id, actual = left.sourceId)
        assertEquals(expected = 3L, actual = left.clipStartSample)
        assertEquals(expected = 8L, actual = left.clipEndSample)
        assertEquals(expected = second.id, actual = right.sourceId)
        assertEquals(expected = 9L, actual = right.clipStartSample)
        assertEquals(expected = 14L, actual = right.clipEndSample)
        assertContentEquals(
            expected = first.rawData.copyOfRange(fromIndex = 8 * 3, toIndex = 13 * 3) + second.rawData.copyOfRange(fromIndex = 4 * 3, toIndex = 9 * 3),
            actual = track().entries.getValue(key = 5L).source()!!.rawData,
        )
    }

    @Test
    fun gapsAndMutedClipsRenderAsSilenceWithTrackMixerUntouched() = runBlocking {
        val audio = source(id = "sound", values = IntArray(size = 4) { 1_000_000 })
        install(entries = listOf(clip(source = audio, atMs = 2L), clip(source = audio, atMs = 8L).copy(isMuted = true)))
        track().volume = 0.25f
        track().isMuted = true
        track().isSoloed = true
        val range = Selectable.TimelineRange(trackIndex = 0, startMs = 0L, endMs = 15L)

        TimelineAudioConsolidation.consolidate(selections = listOf(range))

        val result = track().entries.values.single()
        assertEquals(expected = 15L, actual = result.clipEndSample)
        assertContentEquals(expected = ByteArray(size = 6) + audio.rawData + ByteArray(size = 27), actual = result.source()!!.rawData)
        assertEquals(expected = 0.25f, actual = track().volume)
        assertTrue(actual = track().isMuted && track().isSoloed)
        assertFalse(actual = result.isMuted)
    }

    @Test
    fun multiTrackSelectionUsesCommonBoundsAndLeavesMidiAndItsSelectionUntouched() = runBlocking {
        val audio = source(id = "sound", values = IntArray(size = 4) { 50 })
        val first = AudioTimelineTrack().apply { entries[2L] = clip(source = audio, atMs = 2L) }
        val second = AudioTimelineTrack().apply { entries[5L] = clip(source = audio, atMs = 5L) }
        val midi = MidiTimelineTrack().apply { entries[0L] = MidiEntry(startTimeMs = 0L, durationMs = 20L) }
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(first, second, midi))
        val midiSelection = selected(atMs = 0L, trackIndex = 2)
        val selections = listOf(selected(atMs = 2L), selected(atMs = 5L, trackIndex = 1), midiSelection)

        TimelineAudioConsolidation.consolidate(selections = selections)

        repeat(times = 2) { index ->
            val result = track(index = index).entries.values.single()
            assertEquals(expected = 2L, actual = result.startTimeMs)
            assertEquals(expected = 7_000L, actual = result.durationUs)
        }
        assertTrue(actual = TimelineRepository.tracks.value[2] === midi)
        assertTrue(actual = midiSelection in SelectionManager.selections.value)
        UndoManager.undo()
        assertEquals(expected = setOf(2L), actual = track().entries.keys)
        assertEquals(expected = setOf(5L), actual = track(index = 1).entries.keys)
        assertFalse(actual = UndoManager.canUndo())
    }

    @Test
    fun singleClipKeepsMicrosecondBoundsAndAllIntegerPcmBits() = runBlocking {
        val audio = source(id = "precise", values = intArrayOf(Int.MIN_VALUE, -1, 1, Int.MAX_VALUE), sampleRate = 44_100, bitDepth = 32)
        val original = clip(source = audio).copyWithPreciseTiming(startTimeUs = 10_021L)
        install(entries = listOf(original))

        TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = original.startTimeMs)))

        val result = track().entries.values.single()
        assertEquals(expected = original.startTimeUs, actual = result.startTimeUs)
        assertEquals(expected = original.endTimeUs, actual = result.endTimeUs)
        assertEquals(expected = original.clipEndSample, actual = result.clipEndSample)
        assertContentEquals(expected = audio.rawData, actual = result.source()!!.rawData)
    }

    @Test
    fun adjacentInteger32BitClipsKeepLowBitsAtTheJoin() = runBlocking {
        val first = source(id = "first", values = intArrayOf(16_777_217, -16_777_217, 123_456_789), bitDepth = 32)
        val second = source(id = "second", values = intArrayOf(-123_456_789, 123_456_791, 16_777_219), bitDepth = 32)
        install(entries = listOf(clip(source = first), clip(source = second, atMs = 3L)))

        TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L), selected(atMs = 3L)))

        assertContentEquals(expected = first.rawData + second.rawData, actual = track().entries.values.single().source()!!.rawData)
    }

    @Test
    fun unsignedEightBitInputAndGapsProduceCorrectSignedPcm() {
        val audio = source(id = "eight-bit", values = intArrayOf(0, 128, 255), bitDepth = 8)
        val result = renderConsolidatedAudio(
            target = AudioConsolidationTarget(trackIndex = 0, startUs = 0L, endUs = 5_000L),
            entries = listOf(clip(source = audio, atMs = 1L)),
            sources = mapOf(audio.id to audio),
        ).source.pcmSource()
        assertEquals(expected = 0f, actual = result.sample(frameIndex = 0L, channel = 0))
        assertEquals(expected = -1f, actual = result.sample(frameIndex = 1L, channel = 0))
        assertEquals(expected = 0f, actual = result.sample(frameIndex = 2L, channel = 0))
        assertEquals(expected = 127f / 128f, actual = result.sample(frameIndex = 3L, channel = 0))
        assertEquals(expected = 0f, actual = result.sample(frameIndex = 4L, channel = 0))
    }

    @Test
    fun formatsAreConvertedWithoutNormalizingAndMonoFeedsBothStereoChannels() {
        val mono = source(id = "mono", values = IntArray(size = 480) { 8192 }, sampleRate = 24_000, bitDepth = 16)
        val stereo = source(id = "stereo", values = IntArray(size = 960 * 2) { if (it % 2 == 0) -2_097_152 else 1_048_576 }, sampleRate = 48_000, channels = 2)
        val result = renderConsolidatedAudio(
            target = AudioConsolidationTarget(trackIndex = 0, startUs = 0L, endUs = 40_000L),
            entries = listOf(clip(source = mono), clip(source = stereo, atMs = 20L)),
            sources = mapOf(mono.id to mono, stereo.id to stereo),
        )
        assertEquals(expected = 48_000, actual = result.source.sampleRate)
        assertEquals(expected = 2, actual = result.source.channels)
        assertEquals(expected = 24, actual = result.source.bitDepth)
        assertEquals(expected = 1920L, actual = result.source.totalSamples)
        val pcm = result.source.pcmSource()
        assertEquals(expected = 0.25f, actual = pcm.sample(frameIndex = 200L, channel = 0), absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.25f, actual = pcm.sample(frameIndex = 200L, channel = 1), absoluteTolerance = 0.0001f)
        assertEquals(expected = -0.25f, actual = pcm.sample(frameIndex = 1200L, channel = 0), absoluteTolerance = 0.0001f)
        assertEquals(expected = 0.125f, actual = pcm.sample(frameIndex = 1200L, channel = 1), absoluteTolerance = 0.0001f)
    }

    @Test
    fun rangesTakePriorityAndEmptyOrAutomationSelectionsAreUnavailable() {
        val audio = source(id = "sound", values = IntArray(size = 10) { it })
        install(entries = listOf(clip(source = audio)))
        val reversed = Selectable.TimelineRange(trackIndex = 0, startMs = 8L, endMs = 2L)
        assertEquals(
            expected = listOf(AudioConsolidationTarget(trackIndex = 0, startUs = 2_000L, endUs = 8_000L)),
            actual = resolveAudioConsolidation(selections = listOf(selected(atMs = 0L), reversed)),
        )
        assertFalse(actual = TimelineCommandSurface.canConsolidateSelection(selections = emptyList()))
        assertFalse(actual = TimelineCommandSurface.canConsolidateSelection(selections = listOf(Selectable.TimelineRange(trackIndex = 0, startMs = 12L, endMs = 15L))))
        assertFalse(actual = TimelineCommandSurface.canConsolidateSelection(selections = listOf(Selectable.TimelineRange(trackIndex = 0, startMs = 2L, endMs = 2L))))
        assertFalse(actual = TimelineCommandSurface.canConsolidateSelection(selections = listOf(selected(atMs = 0L), Selectable.TimelineAutomationLane(trackIndex = 0, target = TimelineTrackAutomationTarget.VOLUME))))
    }

    @Test
    fun rightClickPreservesRangeOrClipSelectionOnlyWhenInsideIt() {
        val range = Selectable.TimelineRange(trackIndex = 0, startMs = 2L, endMs = 8L)
        SelectionManager.replaceSelections(updatedSelections = listOf(range))
        TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 5.0, entryStartMs = 0L)
        assertEquals(expected = listOf(range), actual = SelectionManager.selections.value)
        TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 9.0, entryStartMs = 0L)
        assertEquals(expected = 0L, actual = (SelectionManager.selections.value.single() as Selectable.TimelineEntryItem).entryStartMs)
        val clips = listOf(selected(atMs = 0L), selected(atMs = 10L))
        SelectionManager.replaceSelections(updatedSelections = clips)
        TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 11.0, entryStartMs = 10L)
        assertEquals(expected = clips, actual = SelectionManager.selections.value)
        assertFalse(actual = TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 25.0))
        assertEquals(expected = clips, actual = SelectionManager.selections.value)
        SelectionManager.replaceSelections(updatedSelections = listOf(range))
        TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 4.0)
        assertEquals(expected = listOf(range), actual = SelectionManager.selections.value)
        SelectionManager.replaceSelections(updatedSelections = listOf(range, selected(atMs = 10L)))
        TimelineCommandSurface.selectAudioContext(trackIndex = 0, timeMs = 11.0, entryStartMs = 10L)
        assertEquals(expected = 1, actual = SelectionManager.selections.value.size)
        assertTrue(actual = SelectionManager.selections.value.single() is Selectable.TimelineEntryItem)
    }

    @OptIn(ExperimentalCoroutinesApi::class, InternalComposeUiApi::class)
    @Test
    fun keyboardAndEditMenuUseTheSameAsyncActionAndRespectInputFocus() = runBlocking {
        val previousFocus = WorkspaceRepository.isInputFocused
        Dispatchers.setMain(dispatcher = UnconfinedTestDispatcher())
        try {
            val audio = source(id = "sound", values = IntArray(size = 10) { it })
            install(entries = listOf(clip(source = audio), clip(source = audio, atMs = 10L)))
            val selections = listOf(selected(atMs = 0L), selected(atMs = 10L))
            SelectionManager.replaceSelections(updatedSelections = selections)
            assertTrue(actual = WorkspaceMenuCommandSurface.editState(mode = TimelineWorkspaceMode()).canConsolidate)
            WorkspaceRepository.isInputFocused = true
            val shortcut = KeyEvent(key = Key.J, type = KeyEventType.KeyDown, isMetaPressed = true)
            assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shortcut))
            WorkspaceRepository.isInputFocused = false
            assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.J, type = KeyEventType.KeyDown)))
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shortcut))
            withTimeout(timeMillis = 5_000L) {
                TimelineAudioConsolidation.progress.first { it == null }
            }
            assertEquals(expected = 1, actual = track().entries.size)
            assertEquals(expected = null, actual = TimelineAudioConsolidation.error.value)
            UndoManager.undo()
            assertEquals(expected = 2, actual = track().entries.size)
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.J, type = KeyEventType.KeyDown, isCtrlPressed = true)))
            withTimeout(timeMillis = 5_000L) {
                TimelineAudioConsolidation.progress.first { it == null }
            }
            assertEquals(expected = 1, actual = track().entries.size)
        } finally {
            TimelineAudioConsolidation.reset()
            WorkspaceRepository.isInputFocused = previousFocus
            Dispatchers.resetMain()
        }
    }

    @Test
    fun largeOutputStaysFileBackedAndSurvivesUndoRedo() = runBlocking {
        val audio = source(id = "large", values = IntArray(size = 1_500_000) { it })
        install(entries = listOf(clip(source = audio), clip(source = audio, atMs = 1_500_000L)))
        val filesBefore = ProjectPcmFiles.temporaryFileCount()

        TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L), selected(atMs = 1_500_000L)))

        val result = track().entries.values.single().source()!!
        assertNotNull(actual = result.pcmBytes.filePath)
        assertEquals(expected = 3_000_000L, actual = result.totalSamples)
        assertEquals(expected = filesBefore + 1, actual = ProjectPcmFiles.temporaryFileCount())
        assertContentEquals(expected = audio.pcmRegionBytes(fromIndex = 0, toIndex = 12), actual = result.pcmRegionBytes(fromIndex = 4_500_000, toIndex = 4_500_012))
        UndoManager.undo()
        UndoManager.redo()
        assertEquals(expected = result.id, actual = track().entries.values.single().sourceId)
        assertEquals(expected = filesBefore + 1, actual = ProjectPcmFiles.temporaryFileCount())
    }

    @Test
    fun missingSourceFailsWithoutChangingTracksLibraryOrUndo() = runBlocking {
        val audio = source(id = "missing", values = IntArray(size = 10) { it })
        install(entries = listOf(clip(source = audio)))
        AudioLibraryRepository.clear()
        val original = track()
        assertFailsWith<IllegalArgumentException> {
            TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L)))
        }
        assertTrue(actual = track() === original)
        assertTrue(actual = AudioLibraryRepository.all().isEmpty())
        assertFalse(actual = UndoManager.canUndo())
    }

    @Test
    fun editsDuringRenderingRejectTheResultWithoutAddingAssets() = runBlocking {
        val audio = source(id = "sound", values = IntArray(size = 10) { it })
        install(entries = listOf(clip(source = audio)))
        assertFailsWith<IllegalStateException> {
            TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L))) { progress ->
                if (progress == 1f) {
                    track().entries.getValue(key = 0L).name = "Edited during render"
                }
            }
        }
        assertEquals(expected = "Edited during render", actual = track().entries.getValue(key = 0L).name)
        assertEquals(expected = listOf(audio), actual = AudioLibraryRepository.all())
        assertFalse(actual = UndoManager.canUndo())
    }

    @Test
    fun cancellationDiscardsLargeTemporaryOutputAndLeavesTimelineUntouched() = runBlocking {
        val audio = source(id = "large", values = IntArray(size = 1_500_000) { it })
        install(entries = listOf(clip(source = audio)))
        val original = track()
        val temporaryFiles = ProjectPcmFiles.temporaryFileCount()
        val job = Job()
        assertFailsWith<CancellationException> {
            kotlinx.coroutines.withContext(context = job) {
                TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L))) { progress ->
                    if (progress > 0.1f) {
                        job.cancel()
                    }
                }
            }
        }
        assertTrue(actual = track() === original)
        assertEquals(expected = temporaryFiles, actual = ProjectPcmFiles.temporaryFileCount())
        assertEquals(expected = listOf(audio), actual = AudioLibraryRepository.all())
    }

    @Test
    fun workspaceResetDuringRenderingDoesNotApplyTheResult() = runBlocking {
        val audio = source(id = "sound", values = IntArray(size = 10) { it })
        install(entries = listOf(clip(source = audio)))
        assertFailsWith<IllegalStateException> {
            TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L))) { progress ->
                if (progress == 1f) {
                    TimelineAudioConsolidation.reset()
                }
            }
        }
        assertEquals(expected = audio.id, actual = track().entries.values.single().sourceId)
        assertFalse(actual = UndoManager.canUndo())
    }

    @Test
    fun savedProjectResolvesTheConsolidatedPcmAfterReload() = runBlocking {
        val audio = source(id = "sound", values = IntArray(size = 20) { it + 1 })
        install(entries = listOf(clip(source = audio, from = 2L, until = 8L), clip(source = audio, atMs = 6L, from = 10L, until = 16L)))
        TimelineAudioConsolidation.consolidate(selections = listOf(selected(atMs = 0L), selected(atMs = 6L)))
        val expected = track().entries.values.single().source()!!.rawData
        val decoded = AmethystProtoBuf.decodeFromByteArray(
            deserializer = SavableWorkspaceData.serializer(),
            bytes = AmethystProtoBuf.encodeToByteArray(serializer = SavableWorkspaceData.serializer(), value = WorkspaceRepository.saveWorkspace()),
        )
        AudioLibraryRepository.load(sources = decoded.audioSources)
        TimelineRepository.updateTracksSnapshot(updatedTracks = decoded.timelineData)
        assertContentEquals(expected = expected, actual = track().entries.values.single().source()!!.rawData)
    }

    private fun source(
        id: String,
        values: IntArray,
        sampleRate: Int = 1_000,
        channels: Int = 1,
        bitDepth: Int = 24,
    ): AudioSource {
        val bytesPerSample = bitDepth / 8
        val bytes = ByteArray(size = values.size * bytesPerSample) { index ->
            (values[index / bytesPerSample] shr ((index % bytesPerSample) * 8)).toByte()
        }
        return AudioLibraryRepository.add(
            source = AudioSource(
                id = id,
                fileName = "$id.wav",
                rawData = bytes,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
            ),
        )
    }

    private fun clip(
        source: AudioSource,
        atMs: Long = 0L,
        from: Long = 0L,
        until: Long = source.totalSamples,
    ): AudioEntry {
        return AudioEntry(
            startTimeMs = atMs,
            durationMs = 0L,
            fileName = source.fileName,
            sourceId = source.id,
            clipStartSample = from,
            clipEndSample = until,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
        ).copyWithPreciseTiming(durationUs = samplesToUs(sampleCount = until - from, sampleRate = source.sampleRate))
    }

    private fun selected(atMs: Long, trackIndex: Int = 0): Selectable.TimelineEntryItem {
        return Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = atMs)
    }

    private fun install(entries: List<AudioEntry>) {
        val track = AudioTimelineTrack().apply {
            this.entries.putAll(from = entries.associateBy { it.startTimeMs })
        }
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track))
    }

    private fun track(index: Int = 0): AudioTimelineTrack {
        return TimelineRepository.tracks.value[index] as AudioTimelineTrack
    }
}
