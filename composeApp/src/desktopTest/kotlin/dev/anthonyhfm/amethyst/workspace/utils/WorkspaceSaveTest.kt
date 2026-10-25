@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.devices.effects.opacity.OpacityChainDeviceState
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import dev.anthonyhfm.amethyst.timeline.data.StemMetadata
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceMeta
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

class WorkspaceSaveTest {
    @Test
    fun snapshotFreezesMutableTracksDevicesAndAutoplay() {
        val clip = MidiEntry(startTimeMs = 0, durationMs = 1000, name = "Snapshot clip")
        val track = MidiTimelineTrack().apply {
            volume = 0.5f
            entries[0] = clip
        }
        val devices = mutableListOf(OpacityChainDeviceState(opacity = 0.25f))
        val actions = mutableMapOf(1.0 to listOf(AutoPlayData.Action(x = 1, y = 2, down = true)))
        val snapshot = WorkspaceSaveSnapshot.capture(
            workspace = SavableWorkspaceData(
                timelineData = listOf(track),
                lights = StateChain(devices = devices),
                autoPlay = AutoPlayData(actions = actions),
            ),
            revision = 42,
        )

        track.volume = 0.9f
        clip.name = "Later edit"
        track.entries.clear()
        devices.clear()
        actions.clear()

        val saved = decodeSnapshot(snapshot = snapshot)
        val savedTrack = saved.timelineData.single() as MidiTimelineTrack
        assertEquals(expected = 0.5f, actual = savedTrack.volume)
        assertEquals(expected = "Snapshot clip", actual = savedTrack.entries.getValue(key = 0).name)
        assertEquals(expected = 0.25f, actual = (saved.lights.devices.single() as OpacityChainDeviceState).opacity)
        assertEquals(expected = 1, actual = saved.autoPlay.actions.size)
        assertEquals(expected = 42, actual = snapshot.revision)
    }

    @Test
    fun streamingAudioRemainsCompatibleWithTheExistingDecoder() {
        try {
            val sources = listOf(0, 128, 4 * 1024 * 1024 + 20).mapIndexed { index, size ->
                AudioSource(
                    id = "source-$index",
                    fileName = "Audio ü $index.wav",
                    rawData = ByteArray(size = size) { (it * 37).toByte() },
                    sampleRate = 48000,
                    channels = 2,
                    bitDepth = 16,
                    stemMetadata = StemMetadata(
                        parentSourceId = "original",
                        kind = StemKind.VOCALS,
                        modelId = "test",
                    ),
                    isLibraryAsset = index != 1,
                )
            }
            val workspace = SavableWorkspaceData(title = "Streaming", audioSources = sources)
            val saved = decodeSnapshot(
                snapshot = WorkspaceSaveSnapshot.capture(workspace = workspace, revision = 0),
            )
            assertEquals(expected = workspace.title, actual = saved.title)
            assertEquals(expected = sources.size, actual = saved.audioSources.size)
            sources.zip(other = saved.audioSources).forEach { (expected, actual) ->
                assertContentEquals(
                    expected = AmethystProtoBuf.encodeToByteArray(value = expected),
                    actual = AmethystProtoBuf.encodeToByteArray(value = actual),
                )
            }
        } finally {
            ProjectPcmFiles.clear()
        }
    }

    @Test
    fun failedCompressionPreservesTheExistingFileAndRemovesTemporaryOutput() {
        val directory = Files.createTempDirectory("amethyst-save-failure")
        val destination = directory.resolve("project.ame")
        val original = "Existing project".toByteArray()
        Files.write(destination, original)
        try {
            assertFailsWith<IOException> {
                writeWorkspaceAtomically(destination = destination) { output ->
                    output.write(ByteArray(size = 128 * 1024))
                    assertContentEquals(expected = original, actual = Files.readAllBytes(destination))
                    throw IOException("Interrupted compression")
                }
            }
            assertContentEquals(expected = original, actual = Files.readAllBytes(destination))
            Files.list(directory).use { paths -> assertEquals(expected = 1L, actual = paths.count()) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun pausedCompressionKeepsTheOriginalUntilTheCompletedFileIsPublished() = runBlocking {
        val directory = Files.createTempDirectory("amethyst-save-paused")
        val destination = directory.resolve("project.ame")
        val original = "Existing project".toByteArray()
        val replacement = ByteArray(size = 300_000) { it.toByte() }
        Files.write(destination, original)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        try {
            val writer = async(context = Dispatchers.IO) {
                writeWorkspaceAtomically(destination = destination) { output ->
                    output.write(replacement, 0, 150_000)
                    started.complete(value = Unit)
                    runBlocking { finish.await() }
                    output.write(replacement, 150_000, 150_000)
                }
            }
            withTimeout(timeMillis = 5000) { started.await() }
            assertContentEquals(expected = original, actual = Files.readAllBytes(destination))
            finish.complete(value = Unit)
            writer.await()
            GZIPInputStream(Files.newInputStream(destination)).use { input ->
                assertContentEquals(expected = replacement, actual = input.readBytes())
            }
            Files.list(directory).use { paths -> assertEquals(expected = 1L, actual = paths.count()) }
        } finally {
            finish.complete(value = Unit)
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun busySavesRejectRepeatedSaveAndSaveAsRequestsUntilComplete() = runTest {
        Dispatchers.setMain(dispatcher = UnconfinedTestDispatcher(scheduler = testScheduler))
        val directory = Files.createTempDirectory("amethyst-save-busy")
        val destination = directory.resolve("project.ame")
        WorkspaceRepository.clean()
        val previousRecents = GlobalSettings.recentWorkspaces
        try {
            WorkspaceRepository.workspaceMeta = WorkspaceMeta(path = destination.toString())
            WorkspaceRepository.setBpm(bpm = 121.0, undoable = false)
            val repeatedRequests = launch(context = Dispatchers.Unconfined) {
                WorkspaceSaveHelper.saveProgress.first { it?.phase == WorkspaceSaveHelper.SavePhase.Compressing }
                WorkspaceRepository.setBpm(bpm = 122.0, undoable = false)
                repeat(times = 20) {
                    assertFalse(actual = WorkspaceSaveHelper.saveWorkspace())
                    assertFalse(actual = WorkspaceSaveHelper.saveWorkspaceAs())
                }
                assertTrue(actual = WorkspaceSaveHelper.isSaving)
                assertFalse(actual = Files.exists(destination))
            }
            val first = async(start = CoroutineStart.UNDISPATCHED) { WorkspaceSaveHelper.saveWorkspace() }
            repeatedRequests.join()
            assertTrue(actual = first.await())
            assertEquals(expected = 121.0, actual = readWorkspace(path = destination).settings.bpm)
            assertTrue(actual = WorkspaceRepository.hasUnsavedChanges())
            assertFalse(actual = WorkspaceSaveHelper.isSaving)

            assertTrue(actual = WorkspaceSaveHelper.saveWorkspace())
            assertEquals(expected = 122.0, actual = readWorkspace(path = destination).settings.bpm)
            assertFalse(actual = WorkspaceRepository.hasUnsavedChanges())
            assertFalse(actual = WorkspaceSaveHelper.isSaving)
        } finally {
            WorkspaceSaveHelper.awaitPendingSaves()
            WorkspaceRepository.clean()
            GlobalSettings.recentWorkspaces = previousRecents
            Dispatchers.resetMain()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancellingTheCallerDoesNotCancelAnAcceptedSave() = runTest {
        Dispatchers.setMain(dispatcher = StandardTestDispatcher(scheduler = testScheduler))
        val directory = Files.createTempDirectory("amethyst-save-cancel")
        val destination = directory.resolve("project.ame")
        WorkspaceRepository.clean()
        val previousRecents = GlobalSettings.recentWorkspaces
        try {
            WorkspaceRepository.workspaceMeta = WorkspaceMeta(path = destination.toString())
            val caller = launch(start = CoroutineStart.LAZY) { WorkspaceSaveHelper.saveWorkspace() }
            val cancelDuringSave = launch(context = Dispatchers.Unconfined) {
                WorkspaceSaveHelper.saveProgress.first { it?.phase == WorkspaceSaveHelper.SavePhase.Compressing }
                assertFalse(actual = Files.exists(destination))
                caller.cancel()
            }
            caller.start()
            cancelDuringSave.join()
            caller.join()
            WorkspaceSaveHelper.awaitPendingSaves()
            assertEquals(expected = "Untitled", actual = readWorkspace(path = destination).title)
            assertFalse(actual = WorkspaceSaveHelper.isSaving)
        } finally {
            WorkspaceSaveHelper.awaitPendingSaves()
            WorkspaceRepository.clean()
            GlobalSettings.recentWorkspaces = previousRecents
            Dispatchers.resetMain()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun realProjectSavesAndReopensWithoutChangingProjectData() = runBlocking {
        val fixture = System.getenv("AMETHYST_PROJECT_SAVE_FIXTURE")
            ?.takeIf(String::isNotBlank) ?: return@runBlocking
        val directory = Files.createTempDirectory("amethyst-real-save")
        try {
            val workspace = HomeRepository.loadWorkspaceData(file = PlatformFile(fixture))
            lateinit var snapshot: WorkspaceSaveSnapshot
            val captureTime = measureTime {
                snapshot = WorkspaceSaveSnapshot.capture(workspace = workspace, revision = 0)
            }
            val destination = directory.resolve("roundtrip.ame")
            val saveTime = measureTime {
                writeWorkspaceAtomically(destination = destination) { output -> snapshot.writeTo(output = output) }
            }
            val saved = readWorkspace(path = destination)
            assertContentEquals(
                expected = AmethystProtoBuf.encodeToByteArray(value = workspace.copy(audioSources = emptyList())),
                actual = AmethystProtoBuf.encodeToByteArray(value = saved.copy(audioSources = emptyList())),
            )
            assertEquals(expected = workspace.audioSources.size, actual = saved.audioSources.size)
            workspace.audioSources.zip(other = saved.audioSources).forEach { (expected, actual) ->
                assertEquals(expected = expected.id, actual = actual.id)
                assertEquals(expected = expected.pcmByteCount, actual = actual.pcmByteCount)
                var offset = 0
                while (offset < expected.pcmByteCount) {
                    val end = minOf(offset + 256 * 1024, expected.pcmByteCount)
                    assertContentEquals(
                        expected = expected.pcmRegionBytes(fromIndex = offset, toIndex = end),
                        actual = actual.pcmRegionBytes(fromIndex = offset, toIndex = end),
                    )
                    offset = end
                }
            }
            println(
                "ProjectSaveProfile snapshot=$captureTime backgroundSave=$saveTime " +
                    "bytes=${Files.size(destination)} sources=${saved.audioSources.size}",
            )
        } finally {
            ProjectPcmFiles.clear()
            directory.toFile().deleteRecursively()
        }
    }

    private fun decodeSnapshot(snapshot: WorkspaceSaveSnapshot): SavableWorkspaceData {
        return ByteArrayOutputStream().use { output ->
            snapshot.writeTo(output = output)
            AmethystProtoBuf.decodeFromByteArray(bytes = output.toByteArray())
        }
    }

    private fun readWorkspace(path: Path): SavableWorkspaceData {
        return GZIPInputStream(Files.newInputStream(path)).use { input ->
            AmethystProtoBuf.decodeFromByteArray(bytes = input.readBytes())
        }
    }
}
