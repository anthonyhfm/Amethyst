package dev.anthonyhfm.amethyst.home.account

import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException
import dev.anthonyhfm.amethyst.hub.data.HubSessionStore
import dev.anthonyhfm.amethyst.hub.data.HubSessionTokens
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.concurrent.TimeUnit

internal interface DesktopSessionBackend {
    fun read(): String?
    fun write(value: String?)
}

internal class DesktopHubTokenStore(
    private val backend: DesktopSessionBackend = desktopSessionBackend(),
    private val lease: DesktopSessionLease = DesktopSessionLease(
        path = Path.of(System.getProperty("user.home"), ".amethyst", "hub-session.lock")
    ),
) : HubSessionStore, AutoCloseable {
    @Synchronized
    override fun load(): HubSessionTokens? {
        lease.acquire()
        val payload = backend.read() ?: return null

        return decode(value = payload.trim())
            ?: throw HubSessionStorageException(
                message = "Your saved session could not be read. Sign out and sign in again to replace it."
            )
    }

    @Synchronized
    override fun save(tokens: HubSessionTokens?) {
        lease.acquire()
        backend.write(value = tokens?.let { encode(tokens = it) })
    }

    @Synchronized
    override fun close() {
        lease.close()
    }

    private fun encode(tokens: HubSessionTokens): String = Base64.getEncoder().encodeToString(
        "${tokens.accessToken}\n${tokens.refreshToken}".toByteArray(Charsets.UTF_8)
    )

    private fun decode(value: String): HubSessionTokens? = runCatching {
        val pieces = String(
            bytes = Base64.getDecoder().decode(value),
            charset = Charsets.UTF_8
        ).split('\n', limit = 2)

        if (pieces.size != 2 || pieces.any { it.isBlank() }) {
            null
        } else {
            HubSessionTokens(
                accessToken = pieces[0],
                refreshToken = pieces[1],
                expiresIn = 300
            )
        }
    }.getOrNull()
}

internal class DesktopSessionLease(private val path: Path) : AutoCloseable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun acquire() {
        if (lock?.isValid == true) {
            return
        }

        try {
            Files.createDirectories(path.parent)
            val opened = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val acquired = try {
                opened.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (error: Exception) {
                opened.close()
                throw error
            }

            if (acquired == null) {
                opened.close()
                throw HubSessionStorageException(
                    message = "Another Amethyst instance is using this account. Close it and try again."
                )
            }

            channel = opened
            lock = acquired
        } catch (error: HubSessionStorageException) {
            throw error
        } catch (_: Exception) {
            throw HubSessionStorageException(message = "Your saved session could not be accessed. Try again.")
        }
    }

    @Synchronized
    override fun close() {
        try {
            lock?.release()
        } finally {
            lock = null
            channel?.close()
            channel = null
        }
    }
}

private fun desktopSessionBackend(): DesktopSessionBackend {
    val os = System.getProperty("os.name").lowercase()
    val service = "dev.anthonyhfm.amethyst.hub"
    val account = System.getProperty("user.name")

    return when {
        os.contains("mac") -> MacHubKeychain(service = service, account = account)
        os.contains("win") -> WindowsHubSessionBackend()
        else -> LinuxHubSessionBackend(service = service, account = account)
    }
}

internal data class SessionCommandResult(val status: Int, val output: String, val hasErrorOutput: Boolean)

internal fun interface DesktopSessionCommand {
    fun execute(args: List<String>, input: String?, filePath: String?): SessionCommandResult
}

private fun sessionCommand(
    args: List<String>,
    input: String? = null,
    filePath: String? = null,
): SessionCommandResult {
    val process = try {
        val builder = ProcessBuilder(args)
        if (filePath != null) {
            builder.environment()["AMETHYST_SESSION_FILE"] = filePath
        }
        builder.start()
    } catch (_: Exception) {
        throw HubSessionStorageException(message = "Secure session storage is unavailable. Try again.")
    }

    try {
        process.outputStream.bufferedWriter().use { writer ->
            if (input != null) {
                writer.write(input + "\n")
            }
        }

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            throw HubSessionStorageException(message = "Secure session storage did not respond. Try again.")
        }

        return SessionCommandResult(
            status = process.exitValue(),
            output = process.inputStream.bufferedReader().readText(),
            hasErrorOutput = process.errorStream.bufferedReader().readText().isNotBlank()
        )
    } catch (error: HubSessionStorageException) {
        throw error
    } catch (_: Exception) {
        throw HubSessionStorageException(message = "Secure session storage could not be accessed. Try again.")
    } finally {
        process.destroyForcibly()
    }
}

private class LinuxHubSessionBackend(
    private val service: String,
    private val account: String,
) : DesktopSessionBackend {
    override fun read(): String? {
        val result = sessionCommand(
            args = listOf("secret-tool", "lookup", "application", service, "account", account)
        )

        if (result.status == 1 && !result.hasErrorOutput) {
            return null
        }

        requireSuccess(result = result)
        return result.output
    }

    override fun write(value: String?) {
        val args = if (value == null) {
            listOf("secret-tool", "clear", "application", service, "account", account)
        } else {
            listOf(
                "secret-tool", "store", "--label=Amethyst Hub session",
                "application", service, "account", account
            )
        }
        val result = sessionCommand(args = args, input = value)
        if (value == null && result.status == 1 && !result.hasErrorOutput) {
            return
        }
        requireSuccess(result = result)
    }
}

private fun requireSuccess(result: SessionCommandResult) {
    if (result.status != 0) {
        throw HubSessionStorageException(
            message = "Secure session storage could not be accessed. Try again.",
            statusCode = result.status
        )
    }
}

internal class WindowsHubSessionBackend(
    private val file: File = windowsSessionFile(),
    private val command: DesktopSessionCommand = DesktopSessionCommand { args, input, filePath ->
        sessionCommand(args = args, input = input, filePath = filePath)
    },
) : DesktopSessionBackend {

    override fun read(): String? {
        val stored = file
        if (!stored.exists()) {
            return null
        }

        val script = """
            ${'$'}ErrorActionPreference = "Stop"
            Add-Type -AssemblyName System.Security
            ${'$'}cipher = [IO.File]::ReadAllBytes(${'$'}env:AMETHYST_SESSION_FILE)
            ${'$'}plain = [Security.Cryptography.ProtectedData]::Unprotect(${'$'}cipher,${'$'}null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Console]::Out.Write([Text.Encoding]::UTF8.GetString(${'$'}plain))
        """.trimIndent()
        val result = command.execute(
            args = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script),
            input = null,
            filePath = stored.absolutePath
        )
        requireSuccess(result = result)
        return result.output
    }

    override fun write(value: String?) {
        val stored = file
        if (value == null) {
            Files.deleteIfExists(stored.toPath())
            return
        }

        Files.createDirectories(stored.parentFile.toPath())
        val temporary = Files.createTempFile(stored.parentFile.toPath(), "hub-session-", ".dpapi")
        try {
            val script = """
                ${'$'}ErrorActionPreference = "Stop"
                Add-Type -AssemblyName System.Security
                ${'$'}plain = [Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd().Trim())
                ${'$'}cipher = [Security.Cryptography.ProtectedData]::Protect(${'$'}plain,${'$'}null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
                [IO.File]::WriteAllBytes(${'$'}env:AMETHYST_SESSION_FILE,${'$'}cipher)
            """.trimIndent()
            val result = command.execute(
                args = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script),
                input = value,
                filePath = temporary.toString()
            )
            requireSuccess(result = result)
            try {
                Files.move(temporary, stored.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, stored.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private fun windowsSessionFile(): File {
    val base = System.getenv("APPDATA") ?: System.getProperty("user.home")
    return File(File(base, "Amethyst"), "hub-session.dpapi")
}
