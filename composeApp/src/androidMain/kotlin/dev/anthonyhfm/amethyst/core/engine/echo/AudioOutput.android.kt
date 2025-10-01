package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

actual object AudioOutput {
    // Mixer- und Geräteeinstellungen
    private var isInitialized = false

    private var deviceSampleRate = 48_000 // Default 48k (wird in initialize() mit Native-Rate überschrieben)
    private var bytesPerFrame = 4 // Stereo 16-bit
    private var deviceFramesPerBuffer = 240
    private var minBufferBytes = 0

    // Mixer-Chunking (ähnlich Desktop): Frames nahe HW-Puffergröße
    private var chunkFrames = 256

    // Single AudioTrack + Software-Mixer
    private var track: AudioTrack? = null
    private var mixerJob: Job? = null

    // Dedizierter Thread für deterministische Latenz
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Amethyst-Android-Audio").apply { isDaemon = true; priority = Thread.NORM_PRIORITY + 1 }
    }
    private val mixerScope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    // Aktive Quellen, geschützt durch 'lock'
    private val sources = LinkedHashMap<String, Source>()
    private val lock = Any()

    private data class Source(
        val id: String,
        val pcm: ByteArray, // 16-bit LE, Stereo, @deviceSampleRate
        var frameCursor: Int, // in Frames
        val totalFrames: Int,
        val gain: Float = 1f,
        val done: AtomicBoolean = AtomicBoolean(false)
    )

    init { initialize() }

    private fun initialize() {
        try {
            // Gerätesample-Rate und Frames/Buffer bestimmen
            val am = try {
                val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? android.app.Application
                app?.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
            } catch (_: Throwable) { null }

            val native = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            deviceSampleRate = if (native > 0) native else 48_000

            deviceFramesPerBuffer = am?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
                ?.takeIf { it > 0 } ?: if (deviceSampleRate == 48_000) 240 else 256

            // Chunk nahe HW-Buffergröße für Stabilität, aber min 128 / max 512
            chunkFrames = deviceFramesPerBuffer.coerceIn(128, 512)

            // AudioTrack anlegen (STREAM, 16-bit Stereo) mit kleinem Buffer nahe minBuf
            val chMask = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(deviceSampleRate, chMask, encoding).coerceAtLeast(0)
            val chunkBytes = chunkFrames * bytesPerFrame
            // Reduzierter Buffer für geringe Latenz, aber nicht unter minBuf
            val bufferSize = maxOf(minBuf, chunkBytes * 2)
            minBufferBytes = minBuf

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(deviceSampleRate)
                .setChannelMask(chMask)
                .setEncoding(encoding)
                .build()

            track = if (Build.VERSION.SDK_INT >= 26) {
                AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    .also {
                        try { it.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) } catch (_: Throwable) { }
                    }
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    deviceSampleRate,
                    chMask,
                    encoding,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            if (track?.state != AudioTrack.STATE_INITIALIZED) {
                try { track?.release() } catch (_: Throwable) {}
                track = null
                return
            }

            // Mixer starten
            startMixer()
            isInitialized = true
        } catch (_: Throwable) {
            isInitialized = false
        }
    }

    private fun startMixer() {
        val t = track ?: return
        mixerJob?.cancel()

        mixerJob = mixerScope.launch {
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) } catch (_: Throwable) {}

            val mixShorts = ShortArray(chunkFrames * 2) // Stereo Frames -> 2 shorts
            val accum = IntArray(chunkFrames * 2)
            val fadeFrames = (deviceSampleRate / 500).coerceIn(48, 256) // ~2 ms bei 48kHz
            val headroom = 0.96 // etwas Reserve

            // Soft-Limiter mit sanfteren Konstanten für weniger Artefakte
            var limiterGain = 1.0
            val attack = 0.15 // schneller als Release
            val release = 0.02 // langsamer für Transparenz

            // Start sofort
            try {
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
            } catch (_: Throwable) {}

            // Writer-Status
            var framesWritten: Long = 0
            var pendingBuf: ShortArray? = null
            var pendingOff = 0
            var pendingLen = 0

            fun mixInto(activeOnly: Boolean = false) {
                java.util.Arrays.fill(accum, 0)

                var hadActive = false
                synchronized(lock) {
                    val it = sources.values.iterator()
                    while (it.hasNext()) {
                        val src = it.next()
                        if (src.done.get()) { it.remove(); continue }

                        val remainFrames = src.totalFrames - src.frameCursor
                        if (remainFrames <= 0) { src.done.set(true); it.remove(); continue }
                        val framesNow = min(chunkFrames, remainFrames)

                        var f = 0
                        var byteOff = src.frameCursor * bytesPerFrame
                        var mixIdx = 0
                        while (f < framesNow) {
                            val l = ((src.pcm[byteOff].toInt() and 0xFF) or ((src.pcm[byteOff + 1].toInt() and 0xFF) shl 8)).let { if (it > 32767) it - 65536 else it }
                            val r = ((src.pcm[byteOff + 2].toInt() and 0xFF) or ((src.pcm[byteOff + 3].toInt() and 0xFF) shl 8)).let { if (it > 32767) it - 65536 else it }

                            // Kurze Fade-In/Out-Rampen
                            val frameIdx = src.frameCursor + f
                            val fadeInMul = (frameIdx.toFloat() / fadeFrames).coerceIn(0f, 1f)
                            val fadeOutMul = ((src.totalFrames - frameIdx).toFloat() / fadeFrames).coerceIn(0f, 1f)
                            val fadeMul = min(fadeInMul, fadeOutMul)

                            val g = (src.gain * fadeMul * headroom).toFloat()
                            accum[mixIdx]     = accum[mixIdx]     + (l * g).toInt()
                            accum[mixIdx + 1] = accum[mixIdx + 1] + (r * g).toInt()

                            byteOff += 4
                            mixIdx += 2
                            f++
                        }
                        src.frameCursor += framesNow
                        hadActive = true
                        if (src.frameCursor >= src.totalFrames) { src.done.set(true); it.remove() }
                    }
                }

                // Wenn nur aktive Quellen gewünscht und keine aktiv -> Stille erzeugen
                if (!hadActive && activeOnly) {
                    java.util.Arrays.fill(mixShorts, 0)
                    pendingBuf = mixShorts
                    pendingOff = 0
                    pendingLen = mixShorts.size
                    return
                }

                // Peak ermitteln und sanft zum Ziel gain glätten
                var peak = 0
                var i = 0
                while (i < accum.size) {
                    val a = abs(accum[i])
                    if (a > peak) peak = a
                    val b = abs(accum[i + 1])
                    if (b > peak) peak = b
                    i += 2
                }
                val target = if (peak > 32767) 32767.0 / peak.toDouble() else 1.0
                val alpha = if (target < limiterGain) attack else release
                limiterGain += (target - limiterGain) * alpha

                // In Shorts umwandeln
                i = 0
                var si = 0
                while (i < accum.size) {
                    val l = (accum[i] * limiterGain).toInt().coerceIn(-32768, 32767)
                    val r = (accum[i + 1] * limiterGain).toInt().coerceIn(-32768, 32767)
                    mixShorts[si] = l.toShort()
                    mixShorts[si + 1] = r.toShort()
                    i += 2
                    si += 2
                }

                pendingBuf = mixShorts
                pendingOff = 0
                pendingLen = mixShorts.size
            }

            suspend fun writeBlockingAll() {
                val buf = pendingBuf ?: return
                while (pendingLen > 0 && isActive) {
                    val wrote = t.write(buf, pendingOff, pendingLen, AudioTrack.WRITE_BLOCKING)
                    if (wrote > 0) {
                        pendingOff += wrote
                        pendingLen -= wrote
                        framesWritten += (wrote / 2).toLong() // shorts -> frames
                    } else {
                        // Fehlerfall -> kurz warten und erneut versuchen
                        try { t.play() } catch (_: Throwable) {}
                        delay(1)
                    }
                }
            }

            // Hauptschleife: kontinuierlich Chunks schreiben (Stille wenn keine aktiven Quellen)
            while (isActive) {
                if (pendingLen == 0) {
                    mixInto(activeOnly = false)
                }
                writeBlockingAll()
                // Minimale Pause, um CPU zu schonen; Chunks ~5ms-10ms
                delay(1)
            }
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null
        val raw = audioSignal.rawData ?: return null
        if (raw.isEmpty()) return null

        // In Ziel-Device-Format konvertieren (Stereo, 16-bit, @deviceSampleRate)
        val srcCh = if (audioSignal.channels in 1..2) audioSignal.channels else 2
        val srcRate = if (audioSignal.sampleRate > 0) audioSignal.sampleRate else deviceSampleRate
        val srcBits = if (audioSignal.bitDepth == 8) 8 else 16

        val pcm16 = if (srcBits == 16) raw else pcm8To16(raw)
        val rateFixed = if (srcRate != deviceSampleRate) resampleLinear16(pcm16, srcRate, deviceSampleRate, srcCh) else pcm16
        val stereo16 = if (srcCh != 2) convertChannels16(rateFixed, srcCh, 2) else rateFixed

        val totalFrames = stereo16.size / bytesPerFrame
        if (totalFrames <= 0) return null

        val id = "aud_${System.nanoTime()}"
        val src = Source(
            id = id,
            pcm = stereo16,
            frameCursor = 0,
            totalFrames = totalFrames,
            gain = 1f
        )

        synchronized(lock) { sources[id] = src }
        return id
    }

    actual fun stop(sourceId: String) {
        synchronized(lock) { sources.remove(sourceId)?.done?.set(true) }
    }

    actual fun stopAll() {
        synchronized(lock) {
            sources.values.forEach { it.done.set(true) }
            sources.clear()
        }
    }

    // ===== Hilfsfunktionen: Konvertierung =====

    private fun pcm8To16(src: ByteArray): ByteArray {
        val out = ByteArray(src.size * 2)
        var i = 0
        var o = 0
        while (i < src.size) {
            val s = (src[i].toInt() and 0xFF) - 128
            val s16 = s * 256
            out[o] = (s16 and 0xFF).toByte()
            out[o + 1] = ((s16 ushr 8) and 0xFF).toByte()
            i++
            o += 2
        }
        return out
    }

    private fun convertChannels16(pcmData: ByteArray, sourceChannels: Int, targetChannels: Int): ByteArray {
        if (sourceChannels == targetChannels) return pcmData
        val bps = 2
        val srcFrame = sourceChannels * bps
        val frames = if (srcFrame > 0) pcmData.size / srcFrame else 0
        if (frames == 0) return ByteArray(0)

        return when {
            sourceChannels == 1 && targetChannels == 2 -> {
                val dst = ByteArray(frames * targetChannels * bps)
                var inPos = 0
                var outPos = 0
                while (inPos + 1 < pcmData.size) {
                    val b0 = pcmData[inPos]
                    val b1 = pcmData[inPos + 1]
                    // L
                    dst[outPos] = b0; dst[outPos + 1] = b1
                    // R
                    dst[outPos + 2] = b0; dst[outPos + 3] = b1
                    inPos += 2
                    outPos += 4
                }
                dst
            }
            sourceChannels == 2 && targetChannels == 1 -> {
                val dst = ByteArray(frames * targetChannels * bps)
                var inPos = 0
                var outPos = 0
                while (inPos + 3 < pcmData.size) {
                    val l = (pcmData[inPos].toInt() and 0xFF) or ((pcmData[inPos + 1].toInt() and 0xFF) shl 8)
                    val r = (pcmData[inPos + 2].toInt() and 0xFF) or ((pcmData[inPos + 3].toInt() and 0xFF) shl 8)
                    val ls = if (l > 32767) l - 65536 else l
                    val rs = if (r > 32767) r - 65536 else r
                    val m = ((ls + rs) / 2).coerceIn(-32768, 32767)
                    dst[outPos] = (m and 0xFF).toByte()
                    dst[outPos + 1] = ((m ushr 8) and 0xFF).toByte()
                    inPos += 4
                    outPos += 2
                }
                dst
            }
            else -> pcmData
        }
    }

    private fun resampleLinear16(
        pcm: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        channels: Int
    ): ByteArray {
        if (sourceSampleRate == targetSampleRate) return pcm
        val bps = 2
        val srcFrame = channels * bps
        val srcFrames = if (srcFrame > 0) pcm.size / srcFrame else 0
        if (srcFrames <= 1) return pcm

        val ratio = targetSampleRate.toDouble() / sourceSampleRate.toDouble()
        val dstFrames = (srcFrames * ratio).toInt().coerceAtLeast(1)
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
                val interp = (s0 + ((s1 - s0) * frac)).toInt()
                wr16LE(dst, outBase + off, interp)
                c++
            }
            i++
        }
        return dst
    }
}
