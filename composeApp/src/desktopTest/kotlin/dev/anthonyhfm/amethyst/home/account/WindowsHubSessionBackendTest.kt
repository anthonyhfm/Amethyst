package dev.anthonyhfm.amethyst.home.account

import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsHubSessionBackendTest {
    @Test
    fun sessionPathsRemainLiteralDataOutsideThePowerShellProgram() {
        val directory = Files.createTempDirectory("amethyst windows session & test-")
        val stored = directory.resolve("session & account.dpapi")
        var writes = 0
        val backend = WindowsHubSessionBackend(
            file = stored.toFile(),
            command = DesktopSessionCommand { args, input, filePath ->
                assertEquals(5, args.size)
                assertEquals("-Command", args[3])
                assertTrue(args.last().contains("\$env:AMETHYST_SESSION_FILE"))
                assertFalse(args.last().contains("\$args[0]"))
                assertFalse(args.any { argument -> argument.contains(directory.toString()) })

                if (input != null) {
                    assertEquals("synthetic-session-payload", input)
                    writes++
                    Files.write(Path.of(requireNotNull(filePath)), byteArrayOf(4, 2))
                    SessionCommandResult(status = 0, output = "", hasErrorOutput = false)
                } else {
                    assertEquals(stored.toString(), filePath)
                    SessionCommandResult(status = 0, output = "synthetic-session-payload", hasErrorOutput = false)
                }
            }
        )

        try {
            backend.write(value = "synthetic-session-payload")
            assertContentEquals(byteArrayOf(4, 2), Files.readAllBytes(stored))
            assertEquals("synthetic-session-payload", backend.read())
            assertEquals(1, writes)
        } finally {
            Files.deleteIfExists(stored)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun failedEncryptionDoesNotReplaceAnExistingSessionFile() {
        val directory = Files.createTempDirectory("amethyst-session-test-")
        val stored = directory.resolve("session.dpapi")
        Files.write(stored, byteArrayOf(1, 2, 3))
        val backend = WindowsHubSessionBackend(
            file = stored.toFile(),
            command = DesktopSessionCommand { _, _, _ ->
                SessionCommandResult(status = 1, output = "", hasErrorOutput = true)
            }
        )

        try {
            assertFailsWith<HubSessionStorageException> { backend.write(value = "synthetic-session-payload") }
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(stored))
            Files.list(directory).use { files ->
                assertEquals(1L, files.count())
            }
        } finally {
            Files.deleteIfExists(stored)
            Files.deleteIfExists(directory)
        }
    }
}
