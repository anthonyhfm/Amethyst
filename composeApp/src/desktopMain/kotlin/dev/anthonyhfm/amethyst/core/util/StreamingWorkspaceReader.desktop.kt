@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmByteStorage
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.loading.ProjectLoadMetrics
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray

internal actual fun readStreamingAmethystWorkspace(file: PlatformFile): SavableWorkspaceData? {
    val createdPaths = mutableSetOf<String>()
    try {
        file.file.inputStream().buffered(bufferSize = STREAM_BUFFER_SIZE).use { buffered ->
            PushbackInputStream(buffered, 2).use { input ->
                val first = input.read()
                val second = input.read()
                if (first != 0x1f || second != 0x8b) {
                    return null
                }
                input.unread(second)
                input.unread(first)
                return ProjectLoadMetrics.measure(stage = "ame.stream") {
                    GZIPInputStream(input, STREAM_BUFFER_SIZE).use { gzip ->
                        readWorkspace(input = gzip, createdPaths = createdPaths)
                    }
                }
            }
        }
    } catch (failure: Throwable) {
        ProjectPcmFiles.discard(paths = createdPaths)
        if (failure is SerializationException) {
            return null
        }
        throw failure
    }
}

private fun readWorkspace(input: InputStream, createdPaths: MutableSet<String>): SavableWorkspaceData {
    val metadata = ByteArrayOutputStream()
    val sources = mutableListOf<AudioSource>()
    val buffer = ByteArray(size = STREAM_BUFFER_SIZE)
    while (true) {
        val tag = input.readVarIntOrNull() ?: break
        if (tag == AUDIO_SOURCES_TAG) {
            val length = input.readLength()
            val message = LimitedWorkspaceInput(input = input, length = length)
            sources.add(
                element = readAudioSource(
                    input = message,
                    buffer = buffer,
                    createdPaths = createdPaths,
                ),
            )
        } else {
            input.copyField(tag = tag, output = metadata, buffer = buffer)
        }
    }
    return ProjectLoadMetrics.measure(stage = "ame.deserialize") {
        AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(bytes = metadata.toByteArray())
            .copy(audioSources = sources)
    }
}

private fun readAudioSource(
    input: InputStream,
    buffer: ByteArray,
    createdPaths: MutableSet<String>,
): AudioSource {
    val metadata = ByteArrayOutputStream()
    var storage: PcmByteStorage? = null
    while (true) {
        val tag = input.readVarIntOrNull() ?: break
        if (tag == RAW_AUDIO_TAG) {
            val length = input.readLength()
            val previousPath = storage?.filePath
            storage = ProjectPcmFiles.storeStreaming(expectedBytes = length) { writer ->
                var remaining = length
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size))
                    if (count < 0) {
                        throw EOFException("Incomplete project audio")
                    }
                    writer.write(bytes = buffer, offset = 0, length = count)
                    remaining -= count
                }
            }
            storage.filePath?.let { path -> createdPaths.add(element = path) }
            if (previousPath != null) {
                ProjectPcmFiles.discard(paths = setOf(previousPath))
                createdPaths.remove(element = previousPath)
            }
        } else {
            input.copyField(tag = tag, output = metadata, buffer = buffer)
        }
    }
    val pcm = storage ?: throw SerializationException("Missing project audio data")
    metadata.writeVarInt(value = RAW_AUDIO_TAG)
    metadata.writeVarInt(value = 0)
    return AmethystProtoBuf.decodeFromByteArray<AudioSource>(bytes = metadata.toByteArray())
        .withPcmStorage(storage = pcm)
}

private fun InputStream.copyField(tag: Long, output: OutputStream, buffer: ByteArray) {
    if (tag ushr 3 == 0L) {
        throw SerializationException("Invalid project field")
    }
    output.writeVarInt(value = tag)
    when ((tag and 7).toInt()) {
        0 -> output.writeVarInt(value = readVarIntOrNull() ?: throw EOFException("Missing project value"))
        1 -> copyExactly(output = output, length = 8, buffer = buffer)
        2 -> {
            val length = readLength()
            output.writeVarInt(value = length.toLong())
            copyExactly(output = output, length = length, buffer = buffer)
        }
        5 -> copyExactly(output = output, length = 4, buffer = buffer)
        else -> throw SerializationException("Unsupported project field")
    }
}

private fun InputStream.copyExactly(output: OutputStream, length: Int, buffer: ByteArray) {
    var remaining = length
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(remaining, buffer.size))
        if (count < 0) {
            throw EOFException("Incomplete project data")
        }
        output.write(buffer, 0, count)
        remaining -= count
    }
}

private fun InputStream.readLength(): Int {
    val value = readVarIntOrNull() ?: throw EOFException("Missing project field length")
    if (value !in 0L..Int.MAX_VALUE.toLong()) {
        throw SerializationException("Project field is too large")
    }
    return value.toInt()
}

private fun InputStream.readVarIntOrNull(): Long? {
    var next = read()
    if (next < 0) {
        return null
    }
    var value = 0L
    var shift = 0
    while (true) {
        if (shift == 63 && next > 1) {
            throw SerializationException("Invalid project varint")
        }
        value = value or ((next and 127).toLong() shl shift)
        if (next < 128) {
            return value
        }
        shift += 7
        next = read()
        if (next < 0) {
            throw EOFException("Incomplete project varint")
        }
    }
}

private fun OutputStream.writeVarInt(value: Long) {
    var remaining = value
    while (remaining and -128L != 0L) {
        write((remaining.toInt() and 127) or 128)
        remaining = remaining ushr 7
    }
    write(remaining.toInt())
}

private class LimitedWorkspaceInput(input: InputStream, length: Int) : FilterInputStream(input) {
    private var remaining = length

    override fun read(): Int {
        if (remaining == 0) {
            return -1
        }
        val next = `in`.read()
        if (next < 0) {
            throw EOFException("Incomplete project message")
        }
        remaining--
        return next
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (remaining == 0) {
            return -1
        }
        val count = `in`.read(bytes, offset, minOf(length, remaining))
        if (count < 0) {
            throw EOFException("Incomplete project message")
        }
        remaining -= count
        return count
    }

}

private const val STREAM_BUFFER_SIZE = 256 * 1024
private const val AUDIO_SOURCES_TAG = (11L shl 3) or 2L
private const val RAW_AUDIO_TAG = (3L shl 3) or 2L
