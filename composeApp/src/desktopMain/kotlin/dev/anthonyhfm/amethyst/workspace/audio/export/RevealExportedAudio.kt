package dev.anthonyhfm.amethyst.workspace.audio.export

import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal fun revealExportedAudio(path: Path) {
    val target = path.toAbsolutePath().normalize()
    check(Files.isRegularFile(target)) { "The exported file is no longer available: $target" }
    when (platform) {
        Platform.Desktop.MacOS -> runFileManager(command = listOf("/usr/bin/open", "-R", target.toString()))
        Platform.Desktop.Windows -> {
            ProcessBuilder("explorer.exe", "/select,$target").start()
        }
        Platform.Desktop.Linux -> {
            val uri = target.toUri().toASCIIString().replace(oldValue = ",", newValue = "%2C")
            try {
                runFileManager(
                    command = listOf(
                        "dbus-send",
                        "--session",
                        "--type=method_call",
                        "--print-reply",
                        "--dest=org.freedesktop.FileManager1",
                        "/org/freedesktop/FileManager1",
                        "org.freedesktop.FileManager1.ShowItems",
                        "array:string:$uri",
                        "string:",
                    ),
                )
            } catch (_: Exception) {
                runFileManager(command = listOf("xdg-open", target.parent.toString()))
            }
        }
        else -> error("Audio export is only available on desktop")
    }
}

private fun runFileManager(command: List<String>) {
    val process = ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    try {
        check(process.waitFor(5L, TimeUnit.SECONDS)) { "The file manager did not respond." }
        check(process.exitValue() == 0) { "The file manager could not reveal the exported file." }
    } finally {
        if (process.isAlive) {
            process.destroy()
        }
    }
}
