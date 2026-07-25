package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.AVFAudio.*
import platform.CoreAudioTypes.AudioBufferList
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSRecursiveLock
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.posix.memset
import kotlin.math.roundToInt

/**
 * Pull-based iOS output for live performance.
 *
 * AVAudioEngine's real-time thread calls [render]. The callback has no locks,
 * coroutines, Objective-C allocation, or intermediate ring buffer. All DSP and
 * sample-rate conversion remains in the common renderer, which renders at the
 * hardware's native sample rate.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosAudioOutput(
    private val playback: AudioPlaybackEngine,
) {
    private val lifecycleLock = NSRecursiveLock()
    private val session = AVAudioSession.sharedInstance()
    private val renderEnabled = atomic(false)
    private val wantsRunning = atomic(false)
    private val firstRenderFailureReported = atomic(false)
    private val foreground = atomic(
        UIApplication.sharedApplication.applicationState !=
            UIApplicationState.UIApplicationStateBackground,
    )
    private val renderBuffer = FloatArray(MAX_RENDER_FRAMES * OUTPUT_CHANNELS)
    private val observers = mutableListOf<Any>()

    private var engine: AVAudioEngine? = null
    private var sourceNode: AVAudioSourceNode? = null
    private var preferredBufferFrames = DEFAULT_BUFFER_FRAMES
    private var initialMasterGain = 1f
    private var configuredSampleRate = 0
    private var configuredPeriodFrames = 0
    private var lastStartFailure: String? = null

    val isRunning: Boolean
        get() = renderEnabled.value

    internal val startFailure: String?
        get() = lastStartFailure

    fun initialize(
        preferredBufferFrames: Int,
        initialMasterGain: Float,
    ): Boolean = withLifecycleLock {
        this.preferredBufferFrames = preferredBufferFrames.coerceIn(
            MIN_BUFFER_FRAMES,
            MAX_PREFERRED_BUFFER_FRAMES,
        )
        this.initialMasterGain = initialMasterGain.coerceAtLeast(0f)
        wantsRunning.value = true
        installObserversIfNeeded()
        if (!foreground.value) return@withLifecycleLock false
        if (renderEnabled.value && engine?.running == true) return@withLifecycleLock true
        startHardware()
    }

    fun updatePreferredBufferFrames(frames: Int): Boolean = withLifecycleLock {
        preferredBufferFrames = frames.coerceIn(
            MIN_BUFFER_FRAMES,
            MAX_PREFERRED_BUFFER_FRAMES,
        )
        if (!wantsRunning.value || !foreground.value) return@withLifecycleLock true
        rebuildHardware()
    }

    fun pause() = withLifecycleLock {
        renderEnabled.value = false
        engine?.pause()
        deactivateSession()
    }

    fun resume(): Boolean = withLifecycleLock {
        if (!wantsRunning.value || !foreground.value) return@withLifecycleLock false
        val currentEngine = engine
        if (currentEngine == null) {
            return@withLifecycleLock startHardware()
        }
        if (currentEngine.running) {
            renderEnabled.value = true
            return@withLifecycleLock true
        }
        if (!session.setActive(true, null)) return@withLifecycleLock false
        currentEngine.prepare()
        val started = currentEngine.startAndReturnError(null)
        renderEnabled.value = started
        started
    }

    fun close() = withLifecycleLock {
        wantsRunning.value = false
        stopHardware(releasePlayback = true)
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers.clear()
    }

    private fun startHardware(): Boolean {
        lastStartFailure = null
        stopHardware(releasePlayback = false)
        if (!configureSession()) {
            wantsRunning.value = false
            return false
        }

        val nextEngine = AVAudioEngine()
        val hardwareFormat = nextEngine.outputNode.outputFormatForBus(0u)
        val sampleRate = hardwareFormat.sampleRate.roundToInt()
        if (sampleRate <= 0 || hardwareFormat.channelCount == 0u) {
            lastStartFailure =
                "Invalid hardware output format: ${hardwareFormat.sampleRate} Hz, " +
                    "${hardwareFormat.channelCount} channels"
            deactivateSession()
            wantsRunning.value = false
            return false
        }

        val actualPeriodFrames = (session.IOBufferDuration * sampleRate)
            .roundToInt()
            .coerceAtLeast(1)
        val rendererPeriodFrames = actualPeriodFrames.coerceAtMost(MAX_RENDER_FRAMES)
        playback.prepare(
            AudioConfiguration(
                sampleRate = sampleRate,
                channels = OUTPUT_CHANNELS,
                periodFrames = rendererPeriodFrames,
                maximumBlockFrames = MAX_RENDER_FRAMES,
            )
        )
        playback.setMasterGain(initialMasterGain, rampFrames = 0)

        val sourceFormat = AVAudioFormat(
            AVAudioPCMFormatFloat32,
            sampleRate.toDouble(),
            OUTPUT_CHANNELS.toUInt(),
            false,
        )
        val nextSourceNode = AVAudioSourceNode(sourceFormat) { isSilence, _, frameCount, outputData ->
            render(isSilence, frameCount, outputData)
        }
        nextEngine.attachNode(nextSourceNode)
        nextEngine.connect(node1 = nextSourceNode, to = nextEngine.outputNode, format = sourceFormat)
        nextEngine.prepare()

        engine = nextEngine
        sourceNode = nextSourceNode
        configuredSampleRate = sampleRate
        configuredPeriodFrames = actualPeriodFrames
        // AVAudioEngine may synchronously prime the source node while starting.
        // Let that callback render normally instead of marking the first period
        // as silence.
        renderEnabled.value = true
        if (!nextEngine.startAndReturnError(null)) {
            lastStartFailure = "AVAudioEngine failed to start"
            stopHardware(releasePlayback = true)
            wantsRunning.value = false
            return false
        }
        renderEnabled.value = true
        return true
    }

    private fun configureSession(): Boolean {
        if (
            !session.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = 0u,
                error = null,
            )
        ) {
            lastStartFailure = "AVAudioSession.setCategory failed"
            return false
        }

        val referenceRate = session.sampleRate
            .takeIf { it > 0.0 }
            ?: DEFAULT_SAMPLE_RATE
        val requestedDuration = preferredBufferFrames / referenceRate
        if (!session.setPreferredIOBufferDuration(requestedDuration, null)) {
            lastStartFailure = "AVAudioSession.setPreferredIOBufferDuration failed"
            return false
        }
        if (!session.setActive(true, null)) {
            lastStartFailure = "AVAudioSession.setActive failed"
            return false
        }
        return true
    }

    private fun rebuildHardware(): Boolean {
        renderEnabled.value = false
        return startHardware()
    }

    private fun stopHardware(releasePlayback: Boolean) {
        renderEnabled.value = false
        val currentEngine = engine
        currentEngine?.stop()
        sourceNode?.let { node ->
            currentEngine?.disconnectNodeOutput(node)
            currentEngine?.detachNode(node)
        }
        currentEngine?.reset()
        sourceNode = null
        engine = null
        configuredSampleRate = 0
        configuredPeriodFrames = 0
        if (releasePlayback) playback.release()
        deactivateSession()
    }

    private fun deactivateSession() {
        session.setActive(
            active = false,
            withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
            error = null,
        )
    }

    /**
     * Real-time callback. The common renderer and this copy loop are the entire
     * hot path; no queueing period is added on top of the hardware I/O period.
     */
    private fun render(
        isSilence: kotlinx.cinterop.CPointer<kotlinx.cinterop.BooleanVar>?,
        frameCount: UInt,
        outputData: kotlinx.cinterop.CPointer<AudioBufferList>?,
    ): Int {
        val buffers = outputData ?: return NO_ERR
        if (!renderEnabled.value || frameCount == 0u) {
            clear(buffers)
            isSilence?.pointed?.value = true
            return NO_ERR
        }

        return try {
            var destinationFrameOffset = 0
            var remainingFrames = frameCount.toInt()
            while (remainingFrames > 0) {
                val blockFrames = minOf(remainingFrames, MAX_RENDER_FRAMES)
                playback.renderer.render(renderBuffer, blockFrames)
                copyToAudioBufferList(
                    buffers = buffers,
                    sourceFrames = blockFrames,
                    destinationFrameOffset = destinationFrameOffset,
                )
                destinationFrameOffset += blockFrames
                remainingFrames -= blockFrames
            }
            isSilence?.pointed?.value = false
            NO_ERR
        } catch (error: Throwable) {
            if (firstRenderFailureReported.compareAndSet(expect = false, update = true)) {
                println("Echo/iOS: render callback failed: ${error.stackTraceToString()}")
            }
            clear(buffers)
            isSilence?.pointed?.value = true
            NO_ERR
        }
    }

    private fun copyToAudioBufferList(
        buffers: kotlinx.cinterop.CPointer<AudioBufferList>,
        sourceFrames: Int,
        destinationFrameOffset: Int,
    ) {
        val list = buffers.pointed
        val bufferCount = list.mNumberBuffers.toInt()
        if (bufferCount >= OUTPUT_CHANNELS) {
            val left = list.mBuffers[0].mData?.reinterpret<FloatVar>() ?: return
            val right = list.mBuffers[1].mData?.reinterpret<FloatVar>() ?: return
            var frame = 0
            while (frame < sourceFrames) {
                val sourceIndex = frame * OUTPUT_CHANNELS
                val destinationIndex = destinationFrameOffset + frame
                left[destinationIndex] = renderBuffer[sourceIndex]
                right[destinationIndex] = renderBuffer[sourceIndex + 1]
                frame++
            }
            return
        }

        if (bufferCount == 1) {
            val interleaved = list.mBuffers[0].mData?.reinterpret<FloatVar>() ?: return
            var frame = 0
            while (frame < sourceFrames) {
                val sourceIndex = frame * OUTPUT_CHANNELS
                val destinationIndex = (destinationFrameOffset + frame) * OUTPUT_CHANNELS
                interleaved[destinationIndex] = renderBuffer[sourceIndex]
                interleaved[destinationIndex + 1] = renderBuffer[sourceIndex + 1]
                frame++
            }
        }
    }

    private fun clear(buffers: kotlinx.cinterop.CPointer<AudioBufferList>) {
        val list = buffers.pointed
        var index = 0
        while (index < list.mNumberBuffers.toInt()) {
            val buffer = list.mBuffers[index]
            buffer.mData?.let { memset(it, 0, buffer.mDataByteSize.toULong()) }
            index++
        }
    }

    private fun installObserversIfNeeded() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = null,
        ) { notification ->
            handleInterruption(notification)
        }
        observers += center.addObserverForName(
            name = AVAudioEngineConfigurationChangeNotification,
            `object` = null,
            queue = null,
        ) {
            handleHardwareConfigurationChange()
        }
        observers += center.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = session,
            queue = null,
        ) {
            handleHardwareConfigurationChange()
        }
        observers += center.addObserverForName(
            name = AVAudioSessionMediaServicesWereResetNotification,
            `object` = session,
            queue = null,
        ) {
            handleMediaServicesReset()
        }
        observers += center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) {
            foreground.value = false
            pause()
        }
        observers += center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) {
            foreground.value = true
            resume()
        }
    }

    private fun handleInterruption(notification: NSNotification?) {
        val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)
            ?.unsignedIntegerValue
            ?: return
        if (type == AVAudioSessionInterruptionTypeBegan) {
            pause()
        } else {
            resume()
        }
    }

    private fun handleHardwareConfigurationChange() = withLifecycleLock {
        if (!wantsRunning.value || !foreground.value || engine == null) return@withLifecycleLock
        val actualRate = session.sampleRate.roundToInt()
        val actualPeriod = (session.IOBufferDuration * actualRate).roundToInt()
        if (actualRate == configuredSampleRate && actualPeriod == configuredPeriodFrames) return@withLifecycleLock
        rebuildHardware()
    }

    private fun handleMediaServicesReset() = withLifecycleLock {
        if (!wantsRunning.value || !foreground.value) return@withLifecycleLock
        rebuildHardware()
    }

    private inline fun <T> withLifecycleLock(block: () -> T): T {
        lifecycleLock.lock()
        return try {
            block()
        } finally {
            lifecycleLock.unlock()
        }
    }

    private companion object {
        const val OUTPUT_CHANNELS = 2
        const val MIN_BUFFER_FRAMES = 64
        const val DEFAULT_BUFFER_FRAMES = 128
        const val MAX_PREFERRED_BUFFER_FRAMES = 2_048
        const val MAX_RENDER_FRAMES = 4_096
        const val DEFAULT_SAMPLE_RATE = 48_000.0
        const val NO_ERR = 0
    }
}
