package dev.anthonyhfm.amethyst.workspace.utils

import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

internal fun writeWorkspaceAtomically(
    destination: Path,
    write: (OutputStream) -> Unit,
) {
    val outputPath = destination.toAbsolutePath().normalize()
    Files.createDirectories(outputPath.parent)
    val temporaryPath = Files.createTempFile(outputPath.parent, ".${outputPath.fileName}.", ".tmp")
    try {
        FileChannel.open(temporaryPath, WRITE).use { channel ->
            val output = Channels.newOutputStream(channel).buffered(bufferSize = WRITE_BUFFER_SIZE)
            FastWorkspaceGzip(output = output).use { gzip ->
                write(gzip)
                gzip.finish()
                gzip.flush()
                channel.force(true)
            }
        }
        Files.move(temporaryPath, outputPath, ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporaryPath)
    }
}

private class FastWorkspaceGzip(output: OutputStream) : GZIPOutputStream(output, WRITE_BUFFER_SIZE) {
    init {
        def.setLevel(Deflater.BEST_SPEED)
    }
}

private const val WRITE_BUFFER_SIZE = 64 * 1024
