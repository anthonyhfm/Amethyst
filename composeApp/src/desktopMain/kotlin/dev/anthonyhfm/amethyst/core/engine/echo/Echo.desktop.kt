package dev.anthonyhfm.amethyst.core.engine.echo

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.LockSupport
import kotlin.math.max

actual object Echo {
    private val decoder = NativeEchoDecoder()
    private val formats = listOf("wav", "mp3", "flac", "ogg", "aiff", "aif", "aifc")
    private val renderRunning = AtomicBoolean(false)
    private val healthMonitorRunning = AtomicBoolean(false)
    private val mutableOutputStatus = MutableStateFlow(AudioOutputStatus())
    actual val outputStatus: StateFlow<AudioOutputStatus> = mutableOutputStatus.asStateFlow()
    private val healthLogThrottle = AudioHealthLogThrottle()

    @Volatile
    private var totalUnderruns = 0L

    @Volatile
    private var totalStreamErrors = 0L
    private var loggedUnderruns = 0L
    private var loggedStreamErrors = 0L

    @Volatile
    private var controlThread: Thread? = null
    private val controlExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "echo-audio-control").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
            controlThread = this
        }
    }

    @Volatile
    private var playback = AudioPlaybackEngine(AudioChain())

    @Volatile
    private var output: NativePcmOutput? = null

    @Volatile
    private var renderThread: Thread? = null

    @Volatile
    private var healthMonitorThread: Thread? = null

    @Volatile
    private var initialized = false

    private var preferredBufferFrames = DEFAULT_BUFFER_FRAMES
    private var preferredOutputDevice: String? = null
    private var exclusiveMode = false

    actual fun initialize(): Boolean = onControlThread { initializeOnControlThread() }

    private fun initializeOnControlThread(): Boolean {
        if (initialized) return true
        val nextOutput = NativePcmOutput()
        val info = nextOutput.initialize(
            preferredPeriodFrames = preferredBufferFrames,
            preferredOutputDevice = preferredOutputDevice,
            exclusive = exclusiveMode,
        )
        if (!info.available || info.channels.toInt() != OUTPUT_CHANNELS) {
            mutableOutputStatus.value = AudioOutputStatus(
                requestedMode = if (exclusiveMode) AudioOutputMode.Exclusive else AudioOutputMode.Shared,
                error = info.error ?: "Stereo output is unavailable",
                underrunCount = totalUnderruns,
                streamErrorCount = totalStreamErrors,
            )
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
        val reusingPlayback = nextPlayback.configuration == configuration
        if (!reusingPlayback) {
            if (nextPlayback.configuration != null) {
                nextPlayback.release()
            }
            nextPlayback.prepare(configuration)
        }
        nextPlayback.setMasterGain(AudioSettings.masterVolume.value, rampFrames = 0)

        val directBuffer = NativePcmOutput.allocateBuffer(periodFrames, OUTPUT_CHANNELS)
        val floatBuffer = directBuffer.asFloatBuffer()
        val renderBuffer = FloatArray(periodFrames * OUTPUT_CHANNELS)
        val targetQueuedFrames = AudioOutputBufferingPolicy.targetQueuedFrames(
            periodFrames = periodFrames,
            ringCapacityFrames = info.ringCapacityFrames.toInt(),
        )

        // CPAL may use a larger callback than the requested period. Prime the
        // complete native ring so the first callback cannot exhaust the queue.
        repeat(targetQueuedFrames / periodFrames) {
            renderAndWritePeriod(
                playback = nextPlayback,
                output = nextOutput,
                directBuffer = directBuffer,
                floatBuffer = floatBuffer,
                renderBuffer = renderBuffer,
                periodFrames = periodFrames,
                allowWhenStopped = true,
            )
        }
        val startError = nextOutput.start()
        if (startError != null) {
            mutableOutputStatus.value = AudioOutputStatus(
                requestedMode = if (exclusiveMode) AudioOutputMode.Exclusive else AudioOutputMode.Shared,
                error = startError,
                underrunCount = totalUnderruns,
                streamErrorCount = totalStreamErrors,
            )
            if (!reusingPlayback) {
                nextPlayback.release()
            }
            nextOutput.close()
            return false
        }

        output = nextOutput
        mutableOutputStatus.value = AudioOutputStatus(
            available = true,
            deviceId = info.deviceId,
            deviceName = info.deviceName,
            backend = info.backend,
            requestedMode = if (info.requestedExclusive) {
                AudioOutputMode.Exclusive
            } else {
                AudioOutputMode.Shared
            },
            activeMode = if (info.activeExclusive) {
                AudioOutputMode.Exclusive
            } else {
                AudioOutputMode.Shared
            },
            sampleRate = configuration.sampleRate,
            periodFrames = periodFrames,
            fallbackReason = info.fallbackReason,
            underrunCount = totalUnderruns,
            streamErrorCount = totalStreamErrors,
        )
        renderRunning.set(true)
        renderThread = Thread(
            {
                nextOutput.promoteCurrentThreadToRealtime(periodFrames, configuration.sampleRate)
                renderLoop(
                    playback = nextPlayback,
                    output = nextOutput,
                    directBuffer = directBuffer,
                    floatBuffer = floatBuffer,
                    renderBuffer = renderBuffer,
                    periodFrames = periodFrames,
                    sampleRate = configuration.sampleRate,
                    targetQueuedFrames = targetQueuedFrames,
                )
            },
            "echo-kotlin-audio-render",
        ).apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
        initialized = true
        startHealthMonitor(nextOutput)
        return true
    }

    actual fun setPreferredBufferFrames(frames: Int) {
        val normalized = when {
            frames <= 64 -> 64
            frames <= 128 -> 128
            frames <= 256 -> 256
            else -> frames.coerceAtMost(2_048)
        }
        controlExecutor.execute {
            if (preferredBufferFrames == normalized) return@execute
            preferredBufferFrames = normalized
            restartIfRunning()
        }
    }

    actual fun outputDevices(): List<AudioOutputDevice> = onControlThread {
        val devices = runCatching {
            output?.outputDevices()
                ?: NativePcmOutput().use { it.outputDevices() }
        }.getOrDefault(emptyList())
        devices.map {
            AudioOutputDevice(
                id = it.id,
                displayName = it.displayName,
                isDefault = it.isDefault,
            )
        }
    }

    actual fun setPreferredOutputDevice(id: String?) {
        val normalized = id?.takeIf { it.isNotBlank() }
        controlExecutor.execute {
            if (preferredOutputDevice == normalized) return@execute
            preferredOutputDevice = normalized
            restartIfRunning()
        }
    }

    actual fun setExclusiveMode(enabled: Boolean) {
        controlExecutor.execute {
            if (exclusiveMode == enabled) return@execute
            exclusiveMode = enabled
            restartIfRunning()
        }
    }

    actual fun setMasterGain(gain: Float) {
        playback.setMasterGain(gain)
    }

    actual fun attachAudioChain(chain: AudioChain) {
        onControlThread {
            if (playback.renderer.chain === chain) return@onControlThread
            val shouldRestart = initialized
            stopOutput()
            playback = AudioPlaybackEngine(chain)
            if (shouldRestart) initializeOnControlThread()
        }
    }

    actual fun getActiveDragFile(): String? {
        return try {
            decoder.getActiveDragFile()
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun probeAudioFile(
        filePath: String
    ): AudioFileMetadata? = withContext(Dispatchers.IO) {
        val result = decoder.probeFile(filePath)
        val meta = result.metadata ?: return@withContext null
        AudioFileMetadata(
            durationMs = meta.durationMs.toLong(),
            sampleRate = meta.sampleRate.toInt(),
            channels = meta.channels.toInt(),
            totalSamples = meta.totalSamples.toLong(),
            bitDepth = meta.bitDepth.toInt(),
        )
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

    actual fun shutdown() {
        onControlThread {
            stopOutput()
            decoder.shutdown()
        }
    }

    private fun restartIfRunning() {
        if (!initialized) return
        stopOutput(releasePlayback = false)
        initializeOnControlThread()
    }

    private fun stopOutput(releasePlayback: Boolean = true) {
        initialized = false
        stopHealthMonitor()
        renderRunning.set(false)
        renderThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                thread.join(RENDER_THREAD_JOIN_MILLIS)
            }
        }
        renderThread = null
        output?.close()
        output = null
        if (releasePlayback) {
            playback.release()
        }
    }

    private fun <T> onControlThread(block: () -> T): T {
        if (Thread.currentThread() === controlThread) return block()
        val future: Future<T> = controlExecutor.submit<T> { block() }
        return future.get()
    }

    private fun renderLoop(
        playback: AudioPlaybackEngine,
        output: NativePcmOutput,
        directBuffer: ByteBuffer,
        floatBuffer: FloatBuffer,
        renderBuffer: FloatArray,
        periodFrames: Int,
        sampleRate: Int,
        targetQueuedFrames: Int,
    ) {
        val periodNanos = periodFrames * NANOS_PER_SECOND / sampleRate.coerceAtLeast(1)
        while (renderRunning.get()) {
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

    private fun startHealthMonitor(monitoredOutput: NativePcmOutput) {
        stopHealthMonitor()
        healthMonitorRunning.set(true)
        healthMonitorThread = Thread(
            {
                var lastUnderruns = 0UL
                var lastStreamErrors = 0UL
                while (healthMonitorRunning.get()) {
                    val telemetry = runCatching { monitoredOutput.telemetry() }.getOrNull()
                    if (telemetry != null) {
                        val underrunDelta = counterDelta(telemetry.underruns, lastUnderruns)
                        val streamErrorDelta =
                            counterDelta(telemetry.streamErrors, lastStreamErrors)
                        lastUnderruns = telemetry.underruns
                        lastStreamErrors = telemetry.streamErrors
                        if (underrunDelta > 0L || streamErrorDelta > 0L) {
                            totalUnderruns += underrunDelta
                            totalStreamErrors += streamErrorDelta
                            mutableOutputStatus.value = mutableOutputStatus.value.copy(
                                underrunCount = totalUnderruns,
                                streamErrorCount = totalStreamErrors,
                            )
                        }
                        val nowMillis = System.nanoTime() / NANOS_PER_MILLISECOND
                        val shouldLog = if (underrunDelta > 0L || streamErrorDelta > 0L) {
                            healthLogThrottle.markChanged(nowMillis)
                        } else {
                            healthLogThrottle.poll(nowMillis)
                        }
                        if (shouldLog) logAudioHealth()
                    }
                    LockSupport.parkNanos(HEALTH_MONITOR_INTERVAL_NANOS)
                }
            },
            "echo-audio-health",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun stopHealthMonitor() {
        healthMonitorRunning.set(false)
        healthMonitorThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                thread.join(HEALTH_MONITOR_JOIN_MILLIS)
            }
        }
        healthMonitorThread = null
    }

    private fun logAudioHealth() {
        loggedUnderruns = totalUnderruns
        loggedStreamErrors = totalStreamErrors
    }

    private fun counterDelta(current: ULong, previous: ULong): Long {
        val delta = if (current >= previous) current - previous else current
        return delta.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
    }

    private fun renderAndWritePeriod(
        playback: AudioPlaybackEngine,
        output: NativePcmOutput,
        directBuffer: ByteBuffer,
        floatBuffer: FloatBuffer,
        renderBuffer: FloatArray,
        periodFrames: Int,
        allowWhenStopped: Boolean = false,
    ) {
        playback.renderer.render(renderBuffer, periodFrames)
        directBuffer.clear()
        floatBuffer.clear()
        floatBuffer.put(renderBuffer)
        directBuffer.limit(renderBuffer.size * Float.SIZE_BYTES)
        var remainingFrames = periodFrames
        while (remainingFrames > 0 && (renderRunning.get() || allowWhenStopped)) {
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
    private const val HEALTH_MONITOR_JOIN_MILLIS = 1_000L
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val MINIMUM_PARK_NANOS = 50_000L
    private const val HEALTH_MONITOR_INTERVAL_NANOS = 250_000_000L
    private const val DEFAULT_BUFFER_FRAMES = 128
}
