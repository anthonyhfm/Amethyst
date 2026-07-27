package dev.anthonyhfm.amethyst.core.engine.echo

import android.os.Process
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.nativeengine.EchoAudioBuffer
import dev.anthonyhfm.amethyst.nativeengine.EchoEngine as NativeEchoDecoder
import dev.anthonyhfm.amethyst.nativeengine.audio.NativePcmOutput
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.math.max

actual object Echo {
    private val decoder = NativeEchoDecoder()
    private val formats = listOf("wav", "mp3", "flac", "ogg", "aiff", "aif", "aifc")
    private val renderRunning = AtomicBoolean(false)

    /** Guards against duplicate render-thread/native-stream teardown across repeated app background/foreground transitions. */
    private val backgrounded = AtomicBoolean(false)

    @Volatile
    private var playback = AudioPlaybackEngine(AudioChain())

    @Volatile
    private var output: NativePcmOutput? = null

    @Volatile
    private var renderThread: Thread? = null

    @Volatile
    private var initialized = false

    private var preferredBufferFrames = NativePcmOutput.DEFAULT_PERIOD_FRAMES
    private var preferredOutputDevice: String? = null

    @Synchronized
    actual fun initialize(): Boolean {
        if (initialized) return true
        val nextOutput = NativePcmOutput()
        val info = nextOutput.initialize(
            preferredPeriodFrames = preferredBufferFrames,
            preferredOutputDevice = preferredOutputDevice,
        )
        if (!info.available || info.channels.toInt() != OUTPUT_CHANNELS) {
            nextOutput.close()
            return false
        }

        val periodFrames = info.periodFrames.toInt().takeIf { it > 0 }
            ?: preferredBufferFrames
        val configuration = AudioConfiguration(
            sampleRate = info.sampleRate.toInt(),
            channels = OUTPUT_CHANNELS,
            periodFrames = periodFrames,
            maximumBlockFrames = max(periodFrames, MAXIMUM_RENDER_BLOCK_FRAMES),
        )
        val nextPlayback = playback
        nextPlayback.prepare(configuration)
        nextPlayback.setMasterGain(AudioSettings.masterVolume.value, rampFrames = 0)

        val directBuffer = NativePcmOutput.allocateBuffer(periodFrames, OUTPUT_CHANNELS)
        val floatBuffer = directBuffer.asFloatBuffer()
        val renderBuffer = FloatArray(periodFrames * OUTPUT_CHANNELS)

        // Prime one period before the hardware starts to avoid a cold-start underrun.
        renderAndWritePeriod(
            playback = nextPlayback,
            output = nextOutput,
            directBuffer = directBuffer,
            floatBuffer = floatBuffer,
            renderBuffer = renderBuffer,
            periodFrames = periodFrames,
        )
        if (nextOutput.start() != null) {
            nextPlayback.release()
            nextOutput.close()
            return false
        }

        output = nextOutput
        renderRunning.set(true)
        renderThread = Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                renderLoop(
                    playback = nextPlayback,
                    output = nextOutput,
                    directBuffer = directBuffer,
                    floatBuffer = floatBuffer,
                    renderBuffer = renderBuffer,
                    periodFrames = periodFrames,
                    sampleRate = configuration.sampleRate,
                )
            },
            "echo-kotlin-audio-render",
        ).apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
        initialized = true
        return true
    }

    @Synchronized
    actual fun setPreferredBufferFrames(frames: Int) {
        val normalized = when {
            frames <= 64 -> 64
            frames <= 128 -> 128
            frames <= 256 -> 256
            else -> frames.coerceAtMost(2_048)
        }
        if (preferredBufferFrames == normalized) return
        preferredBufferFrames = normalized
        restartIfRunning()
    }

    /** Android output is system-default-only; no device picker is offered. */
    actual fun outputDevices(): List<String> = emptyList()

    /** Android output is system-default-only; no device picker is offered. */
    actual fun setPreferredOutputDevice(name: String?) = Unit

    actual fun setMasterGain(gain: Float) {
        playback.setMasterGain(gain)
    }

    @Synchronized
    actual fun attachAudioChain(chain: AudioChain) {
        if (playback.renderer.chain === chain) return
        val shouldRestart = initialized
        stopOutput()
        playback = AudioPlaybackEngine(chain)
        if (shouldRestart) initialize()
    }

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        decoder.decodeFile(filePath).buffer
            ?.toSignal("Echo.Decoder")
            ?.trim(sampleStart, sampleEnd)
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        decoder.decodeBytes(audioData, fileName).buffer
            ?.toSignal("Echo.Decoder")
            ?.trim(sampleStart, sampleEnd)
    }

    actual fun isFormatSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in formats

    actual fun getSupportedFormats(): List<String> = formats

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!initialize()) return null
        playback.setMasterGain(AudioSettings.masterVolume.value)
        return playback.play(audioSignal)
    }

    actual fun playSource(
        sourceId: String,
        startFrame: Long,
        endFrameExclusive: Long,
        gain: Float,
        pan: Float,
        origin: Any?,
    ): String? = playSources(
        listOf(
            AudioSourcePlayback(
                sourceId = sourceId,
                startFrame = startFrame,
                endFrameExclusive = endFrameExclusive,
                gain = gain,
                pan = pan,
                origin = origin,
            )
        )
    ).firstOrNull()

    actual fun playSources(sources: List<AudioSourcePlayback>): List<String?> {
        if (sources.isEmpty()) return emptyList()
        if (!initialize()) return List(sources.size) { null }
        playback.setMasterGain(AudioSettings.masterVolume.value)
        val targetFrame = playback.renderer.absoluteFrame
        return sources.map { request ->
            val source = AudioSourceLibrary.get(request.sourceId)
                ?: return@map null
            val pcm = runCatching {
                ByteArrayPcmAudioSource(
                    id = source.id,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth,
                    rawData = source.rawData,
                )
            }.getOrNull() ?: return@map null
            playback.play(
                source = pcm,
                sourceStartFrame = request.startFrame,
                sourceEndFrameExclusive = request.endFrameExclusive,
                origin = request.origin,
                gain = request.gain,
                pan = request.pan,
                targetFrame = targetFrame,
            )
        }
    }

    actual fun playMultiple(signals: List<Signal.AudioSignal>): List<String?> {
        if (signals.isEmpty()) return emptyList()
        if (!initialize()) return List(signals.size) { null }
        playback.setMasterGain(AudioSettings.masterVolume.value)
        val targetFrame = playback.renderer.absoluteFrame
        return signals.map { playback.play(it, targetFrame) }
    }

    actual fun update(sourceId: String, gain: Float, pan: Float) {
        playback.setMasterGain(AudioSettings.masterVolume.value)
        playback.update(sourceId, gain, pan)
    }

    actual fun stop(sourceId: String) = playback.stop(sourceId)

    actual fun stopAll() = playback.stopAll()

    actual fun stopByOrigin(origin: Any?) = playback.stopByOrigin(origin)

    /**
     * Compatibility entry point for non-chain callers. Sampling devices consume
     * their MIDI triggers directly and therefore never reach this path.
     */
    actual fun audioEnter(signals: List<Signal.AudioSignal>) {
        playMultiple(signals)
    }

    actual fun cancel(signalOrigin: Any?) = stopByOrigin(signalOrigin)

    actual fun reset() = playback.reset()

    @Synchronized
    actual fun shutdown() {
        stopOutput()
        decoder.shutdown()
    }

    /**
     * Tears the native stream and render thread down while the app is backgrounded.
     * Safe to call repeatedly; only the first call in a background span has an effect.
     */
    @Synchronized
    internal fun onBackground() {
        if (!initialized) return
        if (!backgrounded.compareAndSet(false, true)) return
        stopOutput()
    }

    /**
     * Restarts the native stream when the app returns to the foreground, but only
     * if it was torn down by [onBackground]. Safe to call repeatedly.
     */
    @Synchronized
    internal fun onForeground() {
        if (!backgrounded.compareAndSet(true, false)) return
        initialize()
    }

    @Synchronized
    private fun restartIfRunning() {
        if (!initialized) return
        stopOutput()
        initialize()
    }

    private fun stopOutput() {
        initialized = false
        renderRunning.set(false)
        renderThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                thread.join(RENDER_THREAD_JOIN_MILLIS)
            }
        }
        renderThread = null
        output?.close()
        output = null
        playback.release()
    }

    private fun renderLoop(
        playback: AudioPlaybackEngine,
        output: NativePcmOutput,
        directBuffer: ByteBuffer,
        floatBuffer: FloatBuffer,
        renderBuffer: FloatArray,
        periodFrames: Int,
        sampleRate: Int,
    ) {
        val periodNanos = periodFrames * NANOS_PER_SECOND / sampleRate.coerceAtLeast(1)
        var targetPeriods = 1
        var lastUnderrunCount = 0UL
        var lastUnderrunAtNanos = 0L
        var nextTelemetryAtNanos = 0L
        while (renderRunning.get()) {
            val now = System.nanoTime()
            if (now >= nextTelemetryAtNanos) {
                val telemetry = output.telemetry()
                if (telemetry.underruns > lastUnderrunCount) {
                    lastUnderrunCount = telemetry.underruns
                    lastUnderrunAtNanos = now
                    targetPeriods = 2
                } else if (
                    targetPeriods > 1 &&
                    now - lastUnderrunAtNanos >= STABLE_LATENCY_RECOVERY_NANOS
                ) {
                    targetPeriods = 1
                }
                nextTelemetryAtNanos = now + TELEMETRY_INTERVAL_NANOS
            }
            val targetQueuedFrames = periodFrames * targetPeriods
            if (output.queuedFrames() >= targetQueuedFrames) {
                LockSupport.parkNanos((periodNanos / 4L).coerceAtLeast(MINIMUM_PARK_NANOS))
                continue
            }
            renderAndWritePeriod(
                playback = playback,
                output = output,
                directBuffer = directBuffer,
                floatBuffer = floatBuffer,
                renderBuffer = renderBuffer,
                periodFrames = periodFrames,
            )
        }
    }

    private fun renderAndWritePeriod(
        playback: AudioPlaybackEngine,
        output: NativePcmOutput,
        directBuffer: ByteBuffer,
        floatBuffer: FloatBuffer,
        renderBuffer: FloatArray,
        periodFrames: Int,
    ) {
        playback.renderer.render(renderBuffer, periodFrames)
        directBuffer.clear()
        floatBuffer.clear()
        floatBuffer.put(renderBuffer)
        directBuffer.limit(renderBuffer.size * Float.SIZE_BYTES)
        var remainingFrames = periodFrames
        while (remainingFrames > 0 && (renderRunning.get() || !initialized)) {
            val written = output.writeInterleaved(directBuffer, remainingFrames)
            if (written <= 0) {
                LockSupport.parkNanos(MINIMUM_PARK_NANOS)
            } else {
                remainingFrames -= written
            }
        }
    }

    private fun EchoAudioBuffer.toSignal(origin: String): Signal.AudioSignal {
        val pcm = ByteArray(samples.size * 3)
        samples.forEachIndexed { index, sample ->
            val normalized = sample.coerceIn(-1f, 1f)
            val value = if (normalized <= -1f) {
                -8_388_608
            } else {
                (normalized * 8_388_607f).toInt()
            }
            pcm[index * 3] = (value and 0xFF).toByte()
            pcm[index * 3 + 1] = ((value ushr 8) and 0xFF).toByte()
            pcm[index * 3 + 2] = ((value ushr 16) and 0xFF).toByte()
        }
        return Signal.AudioSignal(
            origin = origin,
            rawData = pcm,
            sampleRate = sampleRate.toInt(),
            channels = channels.toInt(),
            bitDepth = 24,
            durationMs = (samples.size / channels.toInt().coerceAtLeast(1) * 1_000L) /
                sampleRate.toLong().coerceAtLeast(1),
        )
    }

    private fun Signal.AudioSignal.trim(start: Long?, end: Long?): Signal.AudioSignal {
        val bytes = rawData ?: return this
        val frameSize = (channels * (bitDepth / 8)).coerceAtLeast(1)
        val frames = bytes.size / frameSize
        val from = (start ?: 0).coerceIn(0, frames.toLong()).toInt()
        val until = (end ?: frames.toLong()).coerceIn(from.toLong(), frames.toLong()).toInt()
        return copy(
            rawData = bytes.copyOfRange(from * frameSize, until * frameSize),
            durationMs = (until - from) * 1_000L / sampleRate,
        )
    }

    private const val OUTPUT_CHANNELS = 2
    private const val MAXIMUM_RENDER_BLOCK_FRAMES = 256
    private const val RENDER_THREAD_JOIN_MILLIS = 1_000L
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val MINIMUM_PARK_NANOS = 50_000L
    private const val TELEMETRY_INTERVAL_NANOS = 250_000_000L
    private const val STABLE_LATENCY_RECOVERY_NANOS = 10L * NANOS_PER_SECOND
}
