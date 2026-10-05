package dev.anthonyhfm.amethyst.conversion.unipad

import dev.anthonyhfm.amethyst.core.util.ProjectArchiveEntry
import dev.anthonyhfm.amethyst.core.util.ProjectArchiveReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnipadArchiveLifetimeTest {
    @Test
    fun nestedProjectReadsOnlyRequiredEntriesAndClosesArchive() {
        val archive = TrackingArchive(
            contents = mapOf(
                "Pack/info" to "title=Streaming project\nproducerName=Fixture\nchain=1",
                "Pack/keyLED/1 1 1" to "on 1 1 a 5\ndelay 100",
                "Pack/unused.bin" to "Unused payload",
                "__MACOSX/unused.bin" to "Unused payload",
            ),
        )
        val workspace = UnipadConverter.convertArchiveToWorkspace(reader = archive)

        assertEquals("Streaming project", workspace.title)
        assertEquals("Fixture", workspace.author)
        assertEquals(listOf("Pack/info", "Pack/keyLED/1 1 1"), archive.readPaths)
        assertTrue(archive.closed)
        assertTrue(UnipadConverter.entries.isEmpty())
        assertNull(UnipadConverter.readEntry(path = "info"))
    }

    @Test
    fun invalidProjectClosesArchiveAndAllowsNextConversion() {
        val invalid = TrackingArchive(contents = mapOf("info" to "chain=99"))
        assertFailsWith<IllegalArgumentException> {
            UnipadConverter.convertArchiveToWorkspace(reader = invalid)
        }
        assertTrue(invalid.closed)
        assertTrue(UnipadConverter.entries.isEmpty())

        val valid = TrackingArchive(contents = mapOf("info" to "title=Next\nchain=1"))
        assertEquals("Next", UnipadConverter.convertArchiveToWorkspace(reader = valid).title)
        assertTrue(valid.closed)
    }

    @Test
    fun failedMetadataReadAlsoClosesArchive() {
        val archive = object : ProjectArchiveReader {
            var closed = false
            override val entries: List<ProjectArchiveEntry>
                get() = error("Unreadable directory")
            override fun readEntry(path: String): ByteArray? = null
            override fun close() {
                closed = true
            }
        }
        assertFailsWith<IllegalStateException> {
            UnipadConverter.convertArchiveToWorkspace(reader = archive)
        }
        assertTrue(archive.closed)
        assertTrue(UnipadConverter.entries.isEmpty())
    }

    private class TrackingArchive(private val contents: Map<String, String>) : ProjectArchiveReader {
        val readPaths = mutableListOf<String>()
        var closed = false
        override val entries = contents.map { (path, content) ->
            ProjectArchiveEntry(
                path = path,
                isDirectory = false,
                compressedSize = content.length.toLong(),
                uncompressedSize = content.length.toLong(),
            )
        }

        override fun readEntry(path: String): ByteArray? {
            readPaths.add(path)
            check(!path.endsWith("unused.bin"))
            return contents[path]?.encodeToByteArray()
        }

        override fun close() {
            closed = true
        }
    }
}
