package dev.anthonyhfm.amethyst.home.account

import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException
import dev.anthonyhfm.amethyst.hub.data.HubSessionTokens
import java.nio.file.Files
import java.nio.file.Path
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopHubTokenStoreTest {
    @Test
    fun theSessionLeaseAlsoExcludesAnotherJvmProcess() {
        val directory = Files.createTempDirectory("amethyst-session-test-")
        val path = directory.resolve("session.lock")
        val lease = DesktopSessionLease(path = path)

        try {
            lease.acquire()
            assertEquals("blocked", probeLeaseInAnotherProcess(path = path))
            lease.close()
            assertEquals("acquired", probeLeaseInAnotherProcess(path = path))
        } finally {
            lease.close()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private fun probeLeaseInAnotherProcess(path: Path): String {
        val classpath = listOf(
            DesktopSessionLease::class.java,
            DesktopSessionLeaseProbe::class.java,
            Unit::class.java,
        ).map { type ->
            Path.of(type.protectionDomain.codeSource.location.toURI()).toString()
        }.distinct().joinToString(separator = File.pathSeparator)
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            classpath,
            DesktopSessionLeaseProbe::class.java.name,
            path.toString()
        ).redirectErrorStream(true).start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            return process.inputStream.bufferedReader().readText().trim()
        } finally {
            process.destroyForcibly()
        }
    }

    @Test
    fun rotationSurvivesRepeatedStoreRecreation() {
        val directory = Files.createTempDirectory("amethyst-session-test-")
        val path = directory.resolve("session.lock")
        val backend = MemoryBackend()

        try {
            repeat(4) { index ->
                DesktopHubTokenStore(
                    backend = backend,
                    lease = DesktopSessionLease(path = path)
                ).use { store ->
                    if (index == 0) {
                        assertNull(store.load())
                    } else {
                        assertEquals("refresh-${index - 1}", store.load()?.refreshToken)
                    }
                    store.save(
                        tokens = HubSessionTokens(
                            accessToken = "access-$index",
                            refreshToken = "refresh-$index",
                            expiresIn = 300
                        )
                    )
                }
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun storageFailureIsDifferentFromAnAbsentSession() {
        val directory = Files.createTempDirectory("amethyst-session-test-")
        val path = directory.resolve("session.lock")
        val backend = MemoryBackend()

        try {
            DesktopHubTokenStore(
                backend = backend,
                lease = DesktopSessionLease(path = path)
            ).use { store ->
                assertNull(store.load())
                backend.readFailure = true
                assertFailsWith<HubSessionStorageException> { store.load() }
                backend.readFailure = false
                backend.value = "invalid-payload"
                assertFailsWith<HubSessionStorageException> { store.load() }
                backend.value = null
                backend.writeFailure = true
                assertFailsWith<HubSessionStorageException> {
                    store.save(
                        tokens = HubSessionTokens(accessToken = "access", refreshToken = "refresh", expiresIn = 300)
                    )
                }
                assertNull(store.load())
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun anotherStoreCannotReadOrRotateTheSessionUntilTheOwnerCloses() {
        val directory = Files.createTempDirectory("amethyst-session-test-")
        val path = directory.resolve("session.lock")
        val firstBackend = MemoryBackend()
        val secondBackend = MemoryBackend()
        val first = DesktopHubTokenStore(backend = firstBackend, lease = DesktopSessionLease(path = path))
        val second = DesktopHubTokenStore(backend = secondBackend, lease = DesktopSessionLease(path = path))

        try {
            first.load()
            assertFailsWith<HubSessionStorageException> { second.load() }
            assertFailsWith<HubSessionStorageException> {
                second.save(
                    tokens = HubSessionTokens(accessToken = "access", refreshToken = "refresh", expiresIn = 300)
                )
            }
            assertEquals(0, secondBackend.reads)
            assertNull(secondBackend.value)
            first.close()
            assertNull(second.load())
            assertEquals(1, secondBackend.reads)
        } finally {
            first.close()
            second.close()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private class MemoryBackend : DesktopSessionBackend {
        var value: String? = null
        var readFailure = false
        var writeFailure = false
        var reads = 0

        override fun read(): String? {
            reads++
            if (readFailure) {
                throw HubSessionStorageException(message = "Storage is temporarily unavailable.")
            }
            return value
        }

        override fun write(value: String?) {
            if (writeFailure) {
                throw HubSessionStorageException(message = "Storage is temporarily unavailable.")
            }
            this.value = value
        }
    }
}

internal object DesktopSessionLeaseProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val lease = DesktopSessionLease(path = Path.of(args.single()))
        try {
            lease.acquire()
            println("acquired")
        } catch (_: HubSessionStorageException) {
            println("blocked")
        } finally {
            lease.close()
        }
    }
}
