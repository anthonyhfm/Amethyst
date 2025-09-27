package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import kotlinx.coroutines.*
import org.lwjgl.openal.*
import org.lwjgl.openal.SOFTPauseDevice.*
import org.lwjgl.openal.SOFTSourceLatency
import org.lwjgl.openal.SOFTSourceStartDelay
import org.lwjgl.openal.SOFTDeviceClock
import org.lwjgl.system.MemoryUtil
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

actual object AudioOutput {
    private val isWindows = (platform == Platform.Desktop.Windows)
    private val MIX_SAMPLE_RATE = if (isWindows) 48_000 else 44_100

    private val CHUNK_FRAMES = if (isWindows) 256 else 512
    private const val STREAM_BUFFERS = 4
    private const val MAX_SOURCES = 16

    private val ALC_REFRESH_HZ = (MIX_SAMPLE_RATE / CHUNK_FRAMES).coerceIn(60, 960)

    private var device: Long = 0L
    private var context: Long = 0L
    private var isInitialized = false

    private var processingJob: Job? = null

    private val sources = ConcurrentHashMap<String, StreamingSource>()

    private val warmQueue = ConcurrentLinkedQueue<() -> Unit>()

    private data class StreamingSource(
        val id: String,
        val alSource: Int,
        val alBuffers: IntArray,
        val channels: Int,
        val format: Int,
        val sampleRate: Int,
        val pcm: ByteArray,
        var readBytes: Int,
        val chunkBytes: Int,
        val scratch: ByteBuffer,
        val priority: Int = 0
    ) {
        fun cleanup() {
            try {
                AL10.alSourceStop(alSource)
                var queued = AL10.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED)
                while (queued > 0) {
                    val tmp = IntArray(1)
                    AL11.alSourceUnqueueBuffers(alSource, tmp)
                    queued--
                }
                AL10.alDeleteSources(alSource)
                AL10.alDeleteBuffers(alBuffers)
            } catch (_: Throwable) {}
            MemoryUtil.memFree(scratch)
        }
    }

    init { initializeOpenAL() }

    private fun initializeOpenAL() {
        try {
            device = ALC10.alcOpenDevice(null as ByteBuffer?)
            if (device == 0L) return

            val attribs = intArrayOf(
                ALC10.ALC_FREQUENCY, MIX_SAMPLE_RATE,
                ALC11.ALC_REFRESH, ALC_REFRESH_HZ,
                ALC11.ALC_SYNC, ALC10.ALC_FALSE,
                0
            )
            context = ALC10.alcCreateContext(device, attribs)
            if (context == 0L) { ALC10.alcCloseDevice(device); return }
            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context); ALC10.alcCloseDevice(device); return
            }

            val alcCaps = ALC.createCapabilities(device)
            val alCaps = AL.createCapabilities(alcCaps)
            if (!alCaps.OpenAL10) { cleanup(); return }

            AL10.alListenerf(AL10.AL_GAIN, 1f)

            isInitialized = true
            startWorker()
        } catch (_: Throwable) {
            cleanup()
        }
    }

    private fun startWorker() {
        processingJob = CoroutineScope(Dispatchers.Default).launch {
            val tickMs = if (isWindows) 1L else 2L
            while (isActive && isInitialized) {
                try {
                    drainWarmQueue()
                    pumpStreaming()
                    delay(tickMs)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun drainWarmQueue() {
        var job = warmQueue.poll()
        while (job != null) {
            try { job.invoke() } catch (_: Throwable) {}
            job = warmQueue.poll()
        }
    }

    private fun pumpStreaming() {
        for ((key, src) in sources) {
            var processed = AL10.alGetSourcei(src.alSource, AL10.AL_BUFFERS_PROCESSED)
            while (processed > 0) {
                val unq = IntArray(1)
                AL10.alSourceUnqueueBuffers(src.alSource, unq)

                val wrote = fillChunk(src)
                if (wrote > 0) {
                    src.scratch.limit(wrote)
                    AL10.alBufferData(unq[0], src.format, src.scratch, src.sampleRate)
                    AL10.alSourceQueueBuffers(src.alSource, unq)
                }
                processed--
            }

            val queued = AL10.alGetSourcei(src.alSource, AL11.AL_BUFFERS_QUEUED)
            val state = AL10.alGetSourcei(src.alSource, AL10.AL_SOURCE_STATE)
            val nothingLeft = queued == 0 && src.readBytes >= src.pcm.size
            if (nothingLeft && state != AL10.AL_PLAYING) {
                src.cleanup()
                sources.remove(key)
            }
        }
    }

    private fun fillChunk(src: StreamingSource): Int {
        if (src.readBytes >= src.pcm.size) return 0
        val remain = src.pcm.size - src.readBytes
        val toCopy = min(remain, src.chunkBytes)
        src.scratch.clear()
        val base = src.readBytes
        var i = 0
        while (i < toCopy) {
            src.scratch.put(src.pcm[base + i])
            i++
        }
        src.scratch.flip()
        src.readBytes += toCopy
        return toCopy
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null
        val raw = audioSignal.rawData ?: return null
        if (raw.isEmpty()) return null

        val format = when {
            audioSignal.channels == 1 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_MONO16
            audioSignal.channels == 2 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_STEREO16
            audioSignal.channels == 1 && audioSignal.bitDepth == 8  -> AL10.AL_FORMAT_MONO8
            audioSignal.channels == 2 && audioSignal.bitDepth == 8  -> AL10.AL_FORMAT_STEREO8
            else -> AL10.AL_FORMAT_STEREO16
        }

        val sr = if (audioSignal.sampleRate > 0) audioSignal.sampleRate else MIX_SAMPLE_RATE

        val sourceId = AL10.alGenSources()
        if (AL10.alGetError() != AL10.AL_NO_ERROR) return null
        AL10.alSourcef(sourceId, AL10.AL_PITCH, 1f)
        AL10.alSourcef(sourceId, AL10.AL_GAIN, 1f)
        AL10.alSource3f(sourceId, AL10.AL_POSITION, 0f, 0f, 0f)
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE)

        val buffers = IntArray(STREAM_BUFFERS)
        AL10.alGenBuffers(buffers)
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteSources(sourceId); return null
        }

        val bytesPerFrame = when (format) {
            AL10.AL_FORMAT_MONO8   -> 1
            AL10.AL_FORMAT_STEREO8 -> 2
            AL10.AL_FORMAT_MONO16  -> 2
            AL10.AL_FORMAT_STEREO16-> 4
            else -> 4
        }
        val chunkBytes = CHUNK_FRAMES * bytesPerFrame
        val scratch = MemoryUtil.memAlloc(chunkBytes)

        val id = "aud_${System.nanoTime()}"
        val stream = StreamingSource(
            id = id,
            alSource = sourceId,
            alBuffers = buffers,
            channels = audioSignal.channels,
            format = format,
            sampleRate = sr,
            pcm = raw,
            readBytes = 0,
            chunkBytes = chunkBytes,
            scratch = scratch
        )

        var queued = 0
        while (queued < STREAM_BUFFERS) {
            val wrote = fillChunk(stream)
            if (wrote <= 0) break
            scratch.limit(wrote)
            AL10.alBufferData(buffers[queued], format, scratch, sr)
            AL10.alSourceQueueBuffers(sourceId, intArrayOf(buffers[queued]))
            queued++
        }

        val alCaps = AL.getCapabilities()
        val alcCaps = ALC.createCapabilities(device)
        if (queued > 0) {
            if (alCaps.AL_SOFT_source_start_delay && alcCaps.ALC_SOFT_device_clock) {
                val clkBuf = BufferUtils.createLongBuffer(1)
                SOFTDeviceClock.alcGetInteger64vSOFT(device, SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT, clkBuf)
                val nowNs = clkBuf.get(0)
                val delayNs = 2_000_000L
                SOFTSourceStartDelay.alSourcePlayAtTimeSOFT(sourceId, nowNs + delayNs)
            } else {
                AL10.alSourcePlay(sourceId)
            }
        } else {
            stream.cleanup(); return null
        }

        sources[id] = stream

        try {
            if (alCaps.AL_SOFT_source_latency) {
                val v = BufferUtils.createLongBuffer(2)
                SOFTSourceLatency.alGetSourcei64vSOFT(
                    sourceId,
                    SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT,
                    v
                )
            }
        } catch (_: Throwable) {}

        return id
    }

    actual fun stop(sourceId: String) {
        sources.remove(sourceId)?.cleanup()
    }

    actual fun stopAll() {
        for (s in sources.values) s.cleanup()
        sources.clear()
    }

    private fun cleanup() {
        try {
            processingJob?.cancel()
            stopAll()
            if (context != 0L) {
                try { if (ALC.getCapabilities().ALC_SOFT_pause_device) alcDevicePauseSOFT(device) } catch (_: Throwable) {}
                ALC10.alcMakeContextCurrent(0L)
                ALC10.alcDestroyContext(context)
                context = 0L
            }
            if (device != 0L) {
                try { if (ALC.getCapabilities().ALC_SOFT_pause_device) alcDeviceResumeSOFT(device) } catch (_: Throwable) {}
                ALC10.alcCloseDevice(device)
                device = 0L
            }
            isInitialized = false
        } catch (_: Throwable) { }
    }
}
