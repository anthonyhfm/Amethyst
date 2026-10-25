@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import dev.anthonyhfm.amethyst.timeline.data.StemMetadata
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveSnapshot
import dev.anthonyhfm.amethyst.workspace.utils.writeWorkspaceAtomically
import io.github.vinceglb.filekit.PlatformFile
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

class StreamingWorkspaceReaderTest {
    @AfterTest
    fun cleanPcm() {
        ProjectPcmFiles.clear()
    }

    @Test
    fun readsExistingAndStreamingSavesWithIdenticalMetadataAndAudio() {
        val directory = Files.createTempDirectory("amethyst-stream-read")
        try {
            val sources = listOf(0, 128, 4 * 1024 * 1024 + 2).mapIndexed { index, size ->
                AudioSource(
                    id = "source-$index",
                    fileName = "Audio ü $index.wav",
                    rawData = ByteArray(size = size) { (it * 37).toByte() },
                    sampleRate = 44100,
                    channels = 1,
                    bitDepth = 16,
                    stemMetadata = StemMetadata(
                        parentSourceId = "original",
                        kind = StemKind.VOCALS,
                        modelId = "test",
                    ),
                    isLibraryAsset = index != 1,
                )
            }
            val track = MidiTimelineTrack().apply {
                volume = 0.25f
                entries[100] = MidiEntry(startTimeMs = 100, durationMs = 1000, name = "Clip")
            }
            val workspace = SavableWorkspaceData(
                title = "Stream",
                timelineData = listOf(track),
                audioSources = sources,
            )
            val legacyFile = directory.resolve("existing.ame")
            Files.write(legacyFile, Zip.encode(data = AmethystProtoBuf.encodeToByteArray(value = workspace)))
            val streamedFile = directory.resolve("streamed.ame")
            val snapshot = WorkspaceSaveSnapshot.capture(workspace = workspace, revision = 0)
            writeWorkspaceAtomically(destination = streamedFile) { output -> snapshot.writeTo(output = output) }
            for (path in listOf(legacyFile, streamedFile)) {
                val loaded = assertNotNull(
                    actual = readStreamingAmethystWorkspace(file = PlatformFile(path.toFile())),
                )
                assertContentEquals(
                    expected = AmethystProtoBuf.encodeToByteArray(
                        value = workspace.copy(audioSources = emptyList()),
                    ),
                    actual = AmethystProtoBuf.encodeToByteArray(value = loaded.copy(audioSources = emptyList())),
                )
                assertEquals(expected = sources.size, actual = loaded.audioSources.size)
                sources.zip(other = loaded.audioSources).forEach { (expected, actual) ->
                    assertContentEquals(
                        expected = AmethystProtoBuf.encodeToByteArray(value = expected),
                        actual = AmethystProtoBuf.encodeToByteArray(value = actual),
                    )
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun invalidGzipReleasesOnlyItsOwnPcmFiles() {
        val directory = Files.createTempDirectory("amethyst-stream-corrupt")
        try {
            val bytes = ByteArray(size = 4 * 1024 * 1024) { (it * 31).toByte() }
            val source = AudioSource(
                id = "existing",
                fileName = "audio.wav",
                rawData = bytes,
                sampleRate = 44100,
                channels = 1,
                bitDepth = 16,
            )
            val before = ProjectPcmFiles.temporaryFileCount()
            val data = Zip.encode(
                data = AmethystProtoBuf.encodeToByteArray(
                    value = SavableWorkspaceData(audioSources = listOf(source)),
                ),
            )
            data[data.size - 8] = (data[data.size - 8].toInt() xor 1).toByte()
            val path = directory.resolve("corrupt.ame")
            Files.write(path, data)
            assertFailsWith<IOException> {
                readStreamingAmethystWorkspace(file = PlatformFile(path.toFile()))
            }
            assertEquals(expected = before, actual = ProjectPcmFiles.temporaryFileCount())
            assertContentEquals(expected = bytes, actual = source.rawData)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingAudioDataFallsBackWithoutLeakingFiles() {
        val directory = Files.createTempDirectory("amethyst-stream-incomplete")
        try {
            val source = AudioSource(
                id = "complete",
                fileName = "audio.wav",
                rawData = ByteArray(size = 4 * 1024 * 1024),
                sampleRate = 44100,
                channels = 1,
                bitDepth = 16,
            )
            val before = ProjectPcmFiles.temporaryFileCount()
            val workspace = AmethystProtoBuf.encodeToByteArray(
                value = SavableWorkspaceData(audioSources = listOf(source)),
            )
            val incompleteSource = byteArrayOf(0x5a, 3, 0x0a, 1, 'x'.code.toByte())
            val file = directory.resolve("incomplete.ame")
            Files.write(file, Zip.encode(data = workspace + incompleteSource))
            assertNull(actual = readStreamingAmethystWorkspace(file = PlatformFile(file.toFile())))
            assertEquals(expected = before, actual = ProjectPcmFiles.temporaryFileCount())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun truncatedAudioReleasesOnlyItsOwnPcmFiles() {
        val directory = Files.createTempDirectory("amethyst-stream-truncated")
        try {
            val source = AudioSource(
                id = "complete",
                fileName = "audio.wav",
                rawData = ByteArray(size = 4 * 1024 * 1024),
                sampleRate = 44100,
                channels = 1,
                bitDepth = 16,
            )
            val before = ProjectPcmFiles.temporaryFileCount()
            val workspace = AmethystProtoBuf.encodeToByteArray(
                value = SavableWorkspaceData(audioSources = listOf(source)),
            )
            val truncatedSource = byteArrayOf(0x5a, 5, 0x1a, 4, 1, 2, 3)
            val file = directory.resolve("truncated.ame")
            Files.write(file, Zip.encode(data = workspace + truncatedSource))
            assertFailsWith<IOException> {
                readStreamingAmethystWorkspace(file = PlatformFile(file.toFile()))
            }
            assertEquals(expected = before, actual = ProjectPcmFiles.temporaryFileCount())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun unknownFieldsAreSkippedWithAllSupportedWireTypes() {
        val file = Files.createTempFile("amethyst-stream-future", ".ame")
        try {
            val metadata = AmethystProtoBuf.encodeToByteArray(
                value = SavableWorkspaceData(title = "Future fields"),
            )
            val unknownFields = byteArrayOf(
                0xa0.toByte(), 6,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 1,
                0xa9.toByte(), 6, 1, 2, 3, 4, 5, 6, 7, 8,
                0xb5.toByte(), 6, 1, 2, 3, 4,
                0xba.toByte(), 6, 3, 1, 2, 3,
            )
            Files.write(file, Zip.encode(data = metadata + unknownFields))
            val loaded = assertNotNull(actual = readStreamingAmethystWorkspace(file = PlatformFile(file.toFile())))
            assertContentEquals(expected = metadata, actual = AmethystProtoBuf.encodeToByteArray(value = loaded))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun uncompressedProjectsUseTheExistingFallback() {
        val file = Files.createTempFile("amethyst-stream-raw", ".ame")
        try {
            Files.write(file, AmethystProtoBuf.encodeToByteArray(value = SavableWorkspaceData(title = "Raw")))
            assertNull(actual = readStreamingAmethystWorkspace(file = PlatformFile(file.toFile())))
            val loaded = AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(bytes = Files.readAllBytes(file))
            assertEquals(expected = "Raw", actual = loaded.title)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
