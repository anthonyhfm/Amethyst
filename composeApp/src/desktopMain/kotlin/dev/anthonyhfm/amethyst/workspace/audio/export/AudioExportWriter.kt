package dev.anthonyhfm.amethyst.workspace.audio.export

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.roundToLong

internal fun writeAudioExport(
    snapshot: AudioExportSnapshot,
    options: AudioExportOptions,
    destination: Path,
    checkpoint: (Float) -> Unit = {},
) {
    require(options.bitDepth in setOf(16, 24))
    require(options.bitrate in setOf(128, 192, 320))
    require(options.format != AudioExportFormat.MP3 || options.sampleRate in setOf(44_100, 48_000))
    val target = destination.toAbsolutePath().normalize()
    val temporary = Files.createTempFile(target.parent, ".amethyst-export-", ".tmp")
    try {
        RandomAccessFile(temporary.toFile(), "rw").use { output ->
            when (options.format) {
                AudioExportFormat.WAV -> writeWave(snapshot = snapshot, options = options, output = output, checkpoint = checkpoint)
                AudioExportFormat.MP3 -> writeMp3(snapshot = snapshot, options = options, output = output, checkpoint = checkpoint)
            }
            output.fd.sync()
        }
        checkpoint(1f)
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun writeWave(
    snapshot: AudioExportSnapshot,
    options: AudioExportOptions,
    output: RandomAccessFile,
    checkpoint: (Float) -> Unit,
) {
    val bytesPerSample = options.bitDepth / 8
    val frameSize = bytesPerSample * snapshot.channels
    val dataSize = audioExportFrameCount(snapshot = snapshot, sampleRate = options.sampleRate) * frameSize
    val padding = dataSize % 2L
    require(dataSize + padding + 36L <= 0xffff_ffffL) { "This audio exceeds the WAV size limit. Select a shorter range." }
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".encodeToByteArray())
        putInt((dataSize + padding + 36L).toInt())
        put("WAVEfmt ".encodeToByteArray())
        putInt(16)
        putShort(1)
        putShort(snapshot.channels.toShort())
        putInt(options.sampleRate)
        putInt(options.sampleRate * frameSize)
        putShort(frameSize.toShort())
        putShort(options.bitDepth.toShort())
        put("data".encodeToByteArray())
        putInt(dataSize.toInt())
    }
    output.write(header.array())
    val bytes = ByteArray(size = 4096 * frameSize)
    val scale = if (options.bitDepth == 16) 32768.0 else 8388608.0
    renderAudioExport(snapshot = snapshot, options = options, checkpoint = checkpoint) { samples, offset, frames ->
        repeat(times = frames * snapshot.channels) { index ->
            val value = (samples[offset + index].toDouble() * scale).roundToLong()
                .coerceIn(minimumValue = -scale.toLong(), maximumValue = scale.toLong() - 1L)
            repeat(times = bytesPerSample) { byte ->
                bytes[index * bytesPerSample + byte] = (value shr (byte * 8)).toByte()
            }
        }
        output.write(bytes, 0, frames * frameSize)
    }
    if (padding != 0L) {
        output.write(0)
    }
}

private fun writeMp3(
    snapshot: AudioExportSnapshot,
    options: AudioExportOptions,
    output: RandomAccessFile,
    checkpoint: (Float) -> Unit,
) {
    StreamingMp3Encoder(options = options, channels = snapshot.channels).use { encoder ->
        val left = IntArray(size = 4096)
        val right = IntArray(size = 4096)
        val encoded = ByteArray(size = 16_384)
        renderAudioExport(snapshot = snapshot, options = options, checkpoint = checkpoint) { samples, offset, frames ->
            repeat(times = frames) { frame ->
                left[frame] = mp3Sample(value = samples[offset + frame * snapshot.channels])
                right[frame] = mp3Sample(value = samples[offset + frame * snapshot.channels + snapshot.channels - 1])
            }
            val count = encoder.encode(left = left, right = right, frames = frames, output = encoded)
            output.write(encoded, 0, count)
        }
        output.write(encoded, 0, encoder.finish(output = encoded))
        val tagSize = encoder.tag(output = encoded)
        check(tagSize in 1..encoded.size) { "Unable to finish MP3 duration metadata." }
        output.seek(0L)
        output.write(encoded, 0, tagSize)
    }
}

private fun mp3Sample(value: Float): Int {
    return (value.toDouble() * 2147483648.0).roundToLong()
        .coerceIn(minimumValue = Int.MIN_VALUE.toLong(), maximumValue = Int.MAX_VALUE.toLong()).toInt()
}

private class StreamingMp3Encoder(options: AudioExportOptions, channels: Int) : Closeable {
    private val lame = Lame()
    private val tag = VBRTag()
    private val flags: LameGlobalFlags

    init {
        val gain = GainAnalysis()
        val bits = BitStream()
        val presets = Presets()
        val quantizePrivate = QuantizePVT()
        val quantize = Quantize()
        val version = Version()
        val id3 = ID3Tag()
        val reservoir = Reservoir()
        val takehiro = Takehiro()
        val decoder = MPGLib()
        val decoderInterface = Interface()
        val common = Common()
        lame.setModules(gain, bits, presets, quantizePrivate, quantize, tag, version, id3, decoder)
        bits.setModules(gain, decoder, version, tag)
        id3.setModules(bits, version)
        presets.setModules(lame)
        quantize.setModules(bits, reservoir, quantizePrivate, takehiro)
        quantizePrivate.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bits)
        takehiro.setModules(quantizePrivate)
        tag.setModules(lame, bits, version)
        decoder.setModules(decoderInterface, common)
        decoderInterface.setModules(tag, common)
        flags = lame.lame_init()
        flags.num_channels = channels
        flags.in_samplerate = options.sampleRate
        flags.out_samplerate = options.sampleRate
        flags.brate = options.bitrate
        flags.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        flags.quality = 2
        flags.bWriteVbrTag = true
        flags.write_id3tag_automatic = false
        id3.id3tag_init(flags)
        val result = lame.lame_init_params(flags)
        if (result < 0) {
            close()
            error("Unable to initialize the MP3 encoder ($result).")
        }
    }

    fun encode(left: IntArray, right: IntArray, frames: Int, output: ByteArray): Int {
        return checked(count = lame.lame_encode_buffer_int(flags, left, right, frames, output, 0, output.size))
    }

    fun finish(output: ByteArray): Int = checked(count = lame.lame_encode_flush(flags, output, 0, output.size))

    fun tag(output: ByteArray): Int = tag.getLameTagFrame(flags, output)

    private fun checked(count: Int): Int {
        check(count >= 0) { "MP3 encoding failed ($count)." }
        return count
    }

    override fun close() {
        lame.lame_close(flags)
    }
}
