package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTimestamp
import android.os.Build
import android.os.Process
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.roundToInt

actual object AudioOutput {
    private var isInitialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = ConcurrentHashMap<String, Source>()

    private var deviceSampleRate = 48_000
    private var deviceFramesPerBuffer = 240

    private var chunkFrames = 256
    private val streamBuffers = 3

    private data class Source(
        val id: String,
        val track: AudioTrack,
        val writerJob: Job?,
        val startedAtMs: Long,
        val writtenFrames: AtomicLong,
        val sampleRate: Int,
        val channels: Int,
        val stopFlag: AtomicBoolean = AtomicBoolean(false)
    )

    init { initialize() }

    private fun initialize() {
        try {
            val am = runCatching {
                val app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? android.app.Application
                app?.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
            }.getOrNull()

            val srProp = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            val fpbProp = am?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()

            val native = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

            deviceSampleRate = when {
                srProp != null && srProp > 0 -> srProp
                native > 0 -> native
                else -> 44_100
            }

            deviceFramesPerBuffer = when {
                fpbProp != null && fpbProp > 0 -> fpbProp

                deviceSampleRate == 48_000 -> 240
                deviceSampleRate == 44_100 -> 256
                else -> 256
            }

            chunkFrames = maxOf(128, min(deviceFramesPerBuffer, 512))

            isInitialized = true
        } catch (_: Throwable) {
            val native = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            deviceSampleRate = if (native > 0) native else 48_000
            deviceFramesPerBuffer = if (deviceSampleRate == 48_000) 240 else 256
            chunkFrames = maxOf(128, min(deviceFramesPerBuffer, 512))
            isInitialized = true
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null
        val data = audioSignal.rawData ?: return null
        if (data.isEmpty()) return null

        val sr = if (audioSignal.sampleRate > 0) audioSignal.sampleRate else deviceSampleRate
        val targetSr = if (sr == deviceSampleRate) sr else deviceSampleRate
        val targetCh = when (audioSignal.channels) {
            1, 2 -> audioSignal.channels
            else -> 2
        }
        val targetEnc = if (audioSignal.bitDepth == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT

        val pcm = if (sr != targetSr && audioSignal.bitDepth == 16) {
            resampleLinear16(data, sr, targetSr, targetCh)
        } else data

        val isShort = pcm.size <= targetSr * targetCh * 2 / 2
        val mode = if (isShort) AudioTrack.MODE_STATIC else AudioTrack.MODE_STREAM

        val chMask = if (targetCh == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .also {
                if (Build.VERSION.SDK_INT >= 21) it.setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            }
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(targetEnc)
            .setChannelMask(chMask)
            .setSampleRate(targetSr)
            .build()

        val minBuf = AudioTrack.getMinBufferSize(targetSr, chMask, targetEnc)
        val bytesPerFrame = targetCh * (if (targetEnc == AudioFormat.ENCODING_PCM_8BIT) 1 else 2)
        val chunkBytes = chunkFrames * bytesPerFrame
        val streamBufBytes = maxOf(minBuf, chunkBytes * streamBuffers)

        val track = if (Build.VERSION.SDK_INT >= 26) {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(mode)
                .setBufferSizeInBytes(if (mode == AudioTrack.MODE_STATIC) pcm.size else streamBufBytes)
                .also {
                    try {
                        if (Build.VERSION.SDK_INT >= 26) {
                            it.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        }
                    } catch (_: Throwable) {}
                }
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                targetSr,
                chMask,
                targetEnc,
                if (mode == AudioTrack.MODE_STATIC) pcm.size else streamBufBytes,
                mode
            )
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }

        val id = "aud_${System.nanoTime()}"
        val src = Source(
            id = id,
            track = track,
            writerJob = null,
            startedAtMs = System.currentTimeMillis(),
            writtenFrames = AtomicLong(0),
            sampleRate = targetSr,
            channels = targetCh
        )

        if (mode == AudioTrack.MODE_STATIC) {
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) { track.release(); return null }
            src.writtenFrames.set((written / bytesPerFrame).toLong())
            track.play()

            val job = scope.launch {
                val frames = written / bytesPerFrame
                val ms = (frames * 1000L) / targetSr + 20
                delay(ms)
                stop(id)
            }

            active[id] = src.copy(writerJob = job)
            return id
        } else {
            val job = scope.launch {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                } catch (_: Throwable) {}

                var offset = 0
                val initial = min(pcm.size, chunkBytes * 2)
                var wrote = 0
                while (wrote < initial) {
                    val n = track.write(pcm, offset + wrote, min(chunkBytes, initial - wrote), AudioTrack.WRITE_BLOCKING)
                    if (n < 0) break
                    wrote += n
                }
                src.writtenFrames.addAndGet((wrote / bytesPerFrame).toLong())

                track.play()

                offset += wrote
                while (isActive && !src.stopFlag.get() && offset < pcm.size) {
                    val toWrite = min(chunkBytes, pcm.size - offset)
                    val n = track.write(pcm, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                    if (n < 0) break
                    offset += n
                    src.writtenFrames.addAndGet((n / bytesPerFrame).toLong())

                    if (chunkFrames <= 256) {
                        yield()
                    } else {
                        delay(1)
                    }
                }

                val remainFrames = track.playbackHeadPositionFramesSafe(bytesPerFrame)?.let { head ->
                    val writtenF = src.writtenFrames.get()
                    (writtenF - head).coerceAtLeast(0)
                } ?: 0
                val tailMs = (remainFrames * 1000L) / targetSr + 10
                if (tailMs > 0) delay(tailMs)

                stop(id)
            }
            active[id] = src.copy(writerJob = job)
            return id
        }
    }

    actual fun stop(sourceId: String) {
        val s = active.remove(sourceId) ?: return
        try {
            s.stopFlag.set(true)
            s.writerJob?.cancel()
            s.track.stop()
        } catch (_: Throwable) {}
        try { s.track.flush() } catch (_: Throwable) {}
        try { s.track.release() } catch (_: Throwable) {}
    }

    actual fun stopAll() {
        val ids = active.keys.toList()
        ids.forEach { stop(it) }
    }

    private fun AudioTrack.playbackHeadPositionFramesSafe(bytesPerFrame: Int): Long? {
        try {
            val ts = AudioTimestamp()
            if (this.getTimestamp(ts)) {
                return ts.framePosition
            }
        } catch (_: Throwable) { }

        return try {
            @Suppress("DEPRECATION")
            this.playbackHeadPosition.toLong()
        } catch (_: Throwable) { null }
    }

    private fun resampleLinear16(
        pcm: ByteArray,
        srcRate: Int,
        dstRate: Int,
        channels: Int
    ): ByteArray {
        if (srcRate == dstRate) return pcm
        val bps = 2
        val srcFrame = channels * bps
        val srcFrames = if (srcFrame > 0) pcm.size / srcFrame else 0
        if (srcFrames <= 1) return pcm

        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val dstFrames = (srcFrames * ratio).roundToInt().coerceAtLeast(1)
        val dst = ByteArray(dstFrames * srcFrame)

        fun rd16LE(buf: ByteArray, off: Int): Int {
            val s = (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)
            return if (s > 32767) s - 65536 else s
        }
        fun wr16LE(buf: ByteArray, off: Int, v: Int) {
            val cl = v.coerceIn(-32768, 32767)
            buf[off] = (cl and 0xFF).toByte()
            buf[off + 1] = ((cl ushr 8) and 0xFF).toByte()
        }

        var i = 0
        while (i < dstFrames) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - i0).coerceIn(0.0, 1.0)
            val base0 = i0 * srcFrame
            val base1 = i1 * srcFrame
            val outBase = i * srcFrame

            var c = 0
            while (c < channels) {
                val off = c * bps
                val s0 = rd16LE(pcm, base0 + off)
                val s1 = rd16LE(pcm, base1 + off)
                val interp = (s0 + (s1 - s0) * frac).toInt()
                wr16LE(dst, outBase + off, interp)
                c++
            }
            i++
        }
        return dst
    }
}
