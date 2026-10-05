package dev.anthonyhfm.amethyst.conversion.unipad

import dev.anthonyhfm.amethyst.core.util.ProjectArchiveEntry
import dev.anthonyhfm.amethyst.core.util.ProjectArchiveReader
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnipadStreamingAudioTest {
    @Test
    fun soundIsDecodedFromTemporaryFileAndRemovedAfterConversion() {
        val contents = mapOf(
            "Pack/info" to "title=Audio fixture\nchain=1".encodeToByteArray(),
            "Pack/keySound" to "1 1 1 nested/tone.wav".encodeToByteArray(),
            "Pack/sounds/nested/tone.wav" to shortWav(),
        )
        val extractedEntries = mutableListOf<String>()
        val extractedFiles = mutableListOf<Path>()
        val archive = object : ProjectArchiveReader {
            var closed = false
            override val entries = contents.map { (path, bytes) ->
                ProjectArchiveEntry(
                    path = path,
                    isDirectory = false,
                    compressedSize = bytes.size.toLong(),
                    uncompressedSize = bytes.size.toLong(),
                )
            }

            override fun readEntry(path: String): ByteArray? {
                check(!path.contains("sounds/")) { "Sound must be streamed to disk" }
                return contents[path]
            }

            override fun extractEntryToFile(path: String, destinationPath: String): Boolean {
                val destination = Path.of(destinationPath)
                extractedEntries.add(path)
                extractedFiles.add(destination)
                Files.write(destination, contents.getValue(path))
                return true
            }

            override fun close() {
                closed = true
            }
        }
        val workspace = UnipadConverter.convertArchiveToWorkspace(reader = archive)
        val pages = assertIs<GroupChainDeviceState>(workspace.sampling.devices.single())
        val page = pages.groups.single().stateChain
        val pads = assertIs<GroupChainDeviceState>(page.devices.last())
        val sample = assertIs<SampleChainDeviceState>(pads.groups.single().stateChain.devices.last())

        assertTrue(sample.isLoaded)
        assertEquals("nested/tone.wav", sample.fileName)
        assertEquals(8_000, sample.sampleRate)
        assertTrue(requireNotNull(sample.rawData).isNotEmpty())
        assertEquals(listOf("Pack/sounds/nested/tone.wav"), extractedEntries)
        extractedFiles.forEach { assertFalse(Files.exists(it)) }
        assertTrue(archive.closed)
        assertTrue(UnipadConverter.entries.isEmpty())
    }

    private fun shortWav(): ByteArray {
        val frames = 80
        val dataBytes = frames * 2
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".encodeToByteArray())
            putInt(36 + dataBytes)
            put("WAVEfmt ".encodeToByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(8_000)
            putInt(16_000)
            putShort(2)
            putShort(16)
            put("data".encodeToByteArray())
            putInt(dataBytes)
            repeat(frames) { putShort(1_000) }
        }.array()
    }
}
