package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import kotlinx.coroutines.*
import kotlinx.coroutines.asCoroutineDispatcher
import org.lwjgl.openal.*
import org.lwjgl.openal.SOFTDeviceClock
import org.lwjgl.openal.SOFTSourceLatency
import org.lwjgl.openal.SOFTSourceStartDelay
import org.lwjgl.system.MemoryUtil
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Desktop OpenAL backend with strict thread affinity:
 * - A dedicated single thread owns the OpenAL context.
 * - All AL/ALC calls run only on that thread.
 * - Public APIs are blocking wrappers that dispatch work to the audio thread.
 */
actual object AudioOutput {

    // Platform-based defaults
    private val isWindows = (platform == Platform.Desktop.Windows)
    private val MIX_SAMPLE_RATE = if (isWindows) 48_000 else 44_100

    // Optimierte Buffergrößen für stabile, niedrige Latenz
    private val CHUNK_FRAMES = 256 // vorher 128, jetzt ca. 5,3ms bei 48kHz
    private const val STREAM_BUFFERS = 6 // vorher 4, jetzt ca. 32ms Gesamtpufferung
    private const val MAX_SOURCES = 16

    private val ALC_REFRESH_HZ = (MIX_SAMPLE_RATE / CHUNK_FRAMES).coerceIn(60, 960)

    // Dedicated audio thread + scope
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Amethyst-Audio-Thread").apply { isDaemon = true }
    }
    private val alDispatcher = executor.asCoroutineDispatcher()
    private val alScope = CoroutineScope(SupervisorJob() + alDispatcher)

    // OpenAL state (owned by audio thread)
    @Volatile private var device: Long = 0L
    @Volatile private var context: Long = 0L
    @Volatile private var isInitialized = false

    private var processingJob: Job? = null

    // We only touch this map on the audio thread
    private val sources = LinkedHashMap<String, StreamingSource>(MAX_SOURCES + 2)

    // Cached extension flags (read/used on audio thread only)
    private var hasDeviceClock = false
    private var hasStartDelay = false
    private var hasSourceLatency = false

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
        val createdAtNanos: Long = System.nanoTime(),
        val priority: Int = 0,
        val tmpUnqueue: IntArray = IntArray(1)
    ) {
        fun cleanup() {
            try {
                AL10.alSourceStop(alSource)
                var queued = AL10.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED)
                while (queued > 0) {
                    AL10.alSourceUnqueueBuffers(alSource, tmpUnqueue)
                    queued--
                }
                AL10.alDeleteSources(alSource)
                AL10.alDeleteBuffers(alBuffers)
            } catch (_: Throwable) { /* ignore */ }
            MemoryUtil.memFree(scratch)
        }
    }

    init {
        // Initialize on the audio thread
        alScope.launch { initializeOpenAL() }
    }

    /* ============================ lifecycle ============================ */

    private fun initializeOpenAL() {
        try {
            device = ALC10.alcOpenDevice(null as ByteBuffer?)
            if (device == 0L) return

            val attribs = intArrayOf(
                ALC10.ALC_FREQUENCY, MIX_SAMPLE_RATE,
                ALC11.ALC_REFRESH,   ALC_REFRESH_HZ,
                ALC11.ALC_SYNC,      ALC10.ALC_FALSE,
                0
            )
            context = ALC10.alcCreateContext(device, attribs)
            if (context == 0L) { ALC10.alcCloseDevice(device); return }
            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context); ALC10.alcCloseDevice(device); return
            }

            // Capabilities must be created on the same thread as the current context
            val alcCaps = ALC.createCapabilities(device)
            val alCaps  = AL.createCapabilities(alcCaps)

            if (!alCaps.OpenAL10) { cleanupOnAudioThread(); return }

            hasDeviceClock   = alcCaps.ALC_SOFT_device_clock
            hasStartDelay    = alCaps.AL_SOFT_source_start_delay
            hasSourceLatency = alCaps.AL_SOFT_source_latency

            // Basic listener setup
            AL10.alListenerf(AL10.AL_GAIN, 1f)
            // Make sure no distance attenuation is applied
            AL11.alDistanceModel(AL11.AL_NONE)

            isInitialized = true
            startWorkerOnAudioThread()
        } catch (_: Throwable) {
            cleanupOnAudioThread()
        }
    }

    private fun startWorkerOnAudioThread() {
        processingJob = alScope.launch {
            val tickMs = 3L // vorher 1-2ms, jetzt 3ms für weniger CPU-Last
            while (isActive && isInitialized) {
                try {
                    pumpStreaming()
                    delay(tickMs)
                } catch (_: Throwable) { /* ignore */ }
            }
        }
    }

    private fun cleanup() {
        // Ensure cleanup runs on the audio thread
        alScope.launch { cleanupOnAudioThread() }
    }

    private fun cleanupOnAudioThread() {
        try {
            processingJob?.cancel()
            // Stop all sources
            for (s in sources.values) s.cleanup()
            sources.clear()

            if (context != 0L) {
                ALC10.alcMakeContextCurrent(0L)
                ALC10.alcDestroyContext(context)
                context = 0L
            }
            if (device != 0L) {
                ALC10.alcCloseDevice(device)
                device = 0L
            }
            isInitialized = false
        } catch (_: Throwable) { /* ignore */ }
        finally {
            // Keep the executor alive; if you want full teardown, uncomment:
            // executor.shutdown()
        }
    }

    /* ============================ streaming ============================ */

    private fun pumpStreaming() {
        if (sources.isEmpty()) return

        val toRemove = ArrayList<String>(2)

        for ((key, src) in sources) {
            val processed = AL10.alGetSourcei(src.alSource, AL10.AL_BUFFERS_PROCESSED)
            val queued = AL10.alGetSourcei(src.alSource, AL11.AL_BUFFERS_QUEUED)
            val state = AL10.alGetSourcei(src.alSource, AL10.AL_SOURCE_STATE)
            if (processed > 0 || queued < STREAM_BUFFERS) {
                println("[AudioOutput] Source $key: processed=$processed, queued=$queued, state=$state, readBytes=${src.readBytes}/${src.pcm.size}")
            }
            var proc = processed
            while (proc > 0) {
                AL10.alSourceUnqueueBuffers(src.alSource, src.tmpUnqueue)
                val wrote = fillChunk(src)
                if (wrote > 0) {
                    src.scratch.limit(wrote)
                    AL10.alBufferData(src.tmpUnqueue[0], src.format, src.scratch, src.sampleRate)
                    AL10.alSourceQueueBuffers(src.alSource, src.tmpUnqueue)
                } else {
                    println("[AudioOutput] Source $key: fillChunk returned 0 (no more data)")
                }
                proc--
            }
            val queuedAfter = AL10.alGetSourcei(src.alSource, AL11.AL_BUFFERS_QUEUED)
            if (queuedAfter == 0 && src.readBytes < src.pcm.size) {
                println("[AudioOutput] Source $key: Buffer underrun! queued=0, readBytes=${src.readBytes}, pcm.size=${src.pcm.size}")
            }
            val finished = queuedAfter == 0 && src.readBytes >= src.pcm.size && state != AL10.AL_PLAYING
            if (finished) {
                src.cleanup()
                toRemove.add(key)
            }
        }

        if (toRemove.isNotEmpty()) {
            for (k in toRemove) sources.remove(k)
        }
    }

    private fun fillChunk(src: StreamingSource): Int {
        if (src.readBytes >= src.pcm.size) return 0
        val remain  = src.pcm.size - src.readBytes
        val toCopy  = min(remain, src.chunkBytes)
        src.scratch.clear()
        src.scratch.put(src.pcm, src.readBytes, toCopy)
        src.scratch.flip()
        src.readBytes += toCopy
        return toCopy
    }

    private fun enforceVoiceBudget() {
        if (sources.size < MAX_SOURCES) return
        // Evict lowest priority; if equal, oldest first.
        val victim = sources.values.minWithOrNull(
            compareBy<StreamingSource> { it.priority }.thenBy { it.createdAtNanos }
        )
        if (victim != null) {
            victim.cleanup()
            sources.remove(victim.id)
        }
    }

    /* ============================ public API ============================ */

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null
        val raw = audioSignal.rawData ?: return null
        if (raw.isEmpty()) return null

        return runBlocking {
            val result = CompletableDeferred<String?>()

            alScope.launch {
                try {
                    enforceVoiceBudget()

                    val format = when {
                        audioSignal.channels == 1 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_MONO16
                        audioSignal.channels == 2 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_STEREO16
                        audioSignal.channels == 1 && audioSignal.bitDepth == 8  -> AL10.AL_FORMAT_MONO8
                        audioSignal.channels == 2 && audioSignal.bitDepth == 8  -> AL10.AL_FORMAT_STEREO8
                        else -> AL10.AL_FORMAT_STEREO16
                    }

                    val sr = if (audioSignal.sampleRate > 0) audioSignal.sampleRate else MIX_SAMPLE_RATE

                    val sourceId = AL10.alGenSources()
                    if (AL10.alGetError() != AL10.AL_NO_ERROR) { result.complete(null); return@launch }

                    AL10.alSourcef(sourceId, AL10.AL_PITCH, 1f)
                    AL10.alSourcef(sourceId, AL10.AL_GAIN,  1f)
                    AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE)

                    val buffers = IntArray(STREAM_BUFFERS)
                    AL10.alGenBuffers(buffers)
                    if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                        AL10.alDeleteSources(sourceId)
                        result.complete(null)
                        return@launch
                    }

                    val bytesPerFrame = when (format) {
                        AL10.AL_FORMAT_MONO8    -> 1
                        AL10.AL_FORMAT_STEREO8  -> 2
                        AL10.AL_FORMAT_MONO16   -> 2
                        AL10.AL_FORMAT_STEREO16 -> 4
                        else -> 4
                    }
                    val chunkBytes = CHUNK_FRAMES * bytesPerFrame
                    val scratch    = MemoryUtil.memAlloc(chunkBytes)

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

                    // Prime queue
                    var queued = 0
                    while (queued < STREAM_BUFFERS) {
                        val wrote = fillChunk(stream)
                        if (wrote <= 0) break
                        scratch.limit(wrote)
                        AL10.alBufferData(buffers[queued], format, scratch, sr)
                        AL10.alSourceQueueBuffers(sourceId, intArrayOf(buffers[queued]))
                        queued++
                    }
                    if (queued == 0) {
                        stream.cleanup()
                        result.complete(null)
                        return@launch
                    }

                    // Start playback; prefer time-based start if extensions exist
                    try {
                        if (hasStartDelay && hasDeviceClock) {
                            val clk = BufferUtils.createLongBuffer(1)
                            SOFTDeviceClock.alcGetInteger64vSOFT(
                                device,
                                SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT,
                                clk
                            )
                            // Delay equals currently queued audio duration
                            val queuedFrames = queued.toLong() * CHUNK_FRAMES
                            val delayNs = (queuedFrames * 1_000_000_000L) / sr
                            SOFTSourceStartDelay.alSourcePlayAtTimeSOFT(sourceId, clk[0] + delayNs)
                        } else {
                            AL10.alSourcePlay(sourceId)
                        }
                    } catch (_: Throwable) {
                        AL10.alSourcePlay(sourceId)
                    }

                    sources[id] = stream

                    // Optional latency query (no-op if unsupported)
                    if (hasSourceLatency) {
                        try {
                            val v = BufferUtils.createLongBuffer(2)
                            SOFTSourceLatency.alGetSourcei64vSOFT(
                                sourceId,
                                SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT,
                                v
                            )
                            // v[0] = sample offset, v[1] = latency (ns)
                        } catch (_: Throwable) { /* ignore */ }
                    }

                    result.complete(id)
                } catch (_: Throwable) {
                    result.complete(null)
                }
            }

            result.await()
        }
    }

    actual fun stop(sourceId: String) {
        runBlocking {
            val done = CompletableDeferred<Unit>()
            alScope.launch {
                sources.remove(sourceId)?.cleanup()
                done.complete(Unit)
            }
            done.await()
        }
    }

    actual fun stopAll() {
        runBlocking {
            val done = CompletableDeferred<Unit>()
            alScope.launch {
                for (s in sources.values) s.cleanup()
                sources.clear()
                done.complete(Unit)
            }
            done.await()
        }
    }
}
