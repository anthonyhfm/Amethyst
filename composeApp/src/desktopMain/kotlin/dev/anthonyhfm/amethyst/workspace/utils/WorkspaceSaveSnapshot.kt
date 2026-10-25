@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.StemMetadata
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber

internal class WorkspaceSaveSnapshot(
    private val metadata: ByteArray,
    private val audioSources: List<AudioSource>,
    val revision: Long,
    val title: String,
) {
    fun writeTo(output: OutputStream, onProgress: (Float) -> Unit = {}) {
        val totalBytes = metadata.size.toLong() + audioSources.sumOf { it.pcmByteCount.toLong() }
        var writtenBytes = metadata.size.toLong()
        output.write(metadata)
        for (source in audioSources) {
            val header = AmethystProtoBuf.encodeToByteArray(
                value = AudioSourceHeader(
                    id = source.id,
                    fileName = source.fileName,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth,
                    stemMetadata = source.stemMetadata,
                    isLibraryAsset = source.isLibraryAsset,
                ),
            )
            val length = source.pcmByteCount
            val headerSize = Math.addExact(header.size, 1 + varIntSize(value = length))
            val messageSize = Math.addExact(headerSize, length)
            output.writeVarInt(value = (11 shl 3) or 2)
            output.writeVarInt(value = messageSize)
            output.write(header)
            output.writeVarInt(value = (3 shl 3) or 2)
            output.writeVarInt(value = length)
            var offset = 0
            while (offset < length) {
                val end = minOf(offset.toLong() + AUDIO_CHUNK_SIZE, length.toLong()).toInt()
                val bytes = source.pcmRegionBytes(fromIndex = offset, toIndex = end)
                output.write(bytes)
                writtenBytes += bytes.size
                offset = end
                onProgress(writtenBytes.toFloat() / totalBytes.coerceAtLeast(minimumValue = 1L))
            }
        }
        onProgress(1f)
    }

    companion object {
        private const val AUDIO_CHUNK_SIZE = 256 * 1024

        fun capture(workspace: SavableWorkspaceData, revision: Long): WorkspaceSaveSnapshot {
            return WorkspaceSaveSnapshot(
                metadata = AmethystProtoBuf.encodeToByteArray(
                    value = workspace.copy(audioSources = emptyList()),
                ),
                audioSources = workspace.audioSources.toList(),
                revision = revision,
                title = workspace.title,
            )
        }
    }
}

@Serializable
private data class AudioSourceHeader(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val fileName: String,
    @ProtoNumber(4) val sampleRate: Int,
    @ProtoNumber(5) val channels: Int,
    @ProtoNumber(6) val bitDepth: Int,
    @ProtoNumber(7) val stemMetadata: StemMetadata? = null,
    @ProtoNumber(8) val isLibraryAsset: Boolean = true,
)

private fun OutputStream.writeVarInt(value: Int) {
    require(value >= 0)
    var remaining = value
    while (remaining >= 128) {
        write((remaining and 127) or 128)
        remaining = remaining ushr 7
    }
    write(remaining)
}

private fun varIntSize(value: Int): Int {
    var remaining = value
    var size = 1
    while (remaining >= 128) {
        remaining = remaining ushr 7
        size++
    }
    return size
}
