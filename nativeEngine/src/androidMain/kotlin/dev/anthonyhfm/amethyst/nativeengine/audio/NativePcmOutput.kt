package dev.anthonyhfm.amethyst.nativeengine.audio

import com.sun.jna.Library
import com.sun.jna.Native
import dev.anthonyhfm.amethyst.nativeengine.PcmOutputDeviceInfo
import dev.anthonyhfm.amethyst.nativeengine.PcmOutputDevice
import dev.anthonyhfm.amethyst.nativeengine.PcmOutputService
import dev.anthonyhfm.amethyst.nativeengine.PcmOutputTelemetry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android-facing PCM output with a direct-buffer producer path.
 *
 * Android's Kotlin target compiles to the JVM, so the generated UniFFI bindings
 * use JNA here exactly like the desktop JVM target does. Lifecycle and telemetry
 * use UniFFI/JNA. Audio samples never become a Kotlin `List`: [writeInterleaved]
 * passes direct native-endian Float32 memory to the preallocated native ring.
 *
 * Exactly one render thread may call [writeInterleaved]. That render thread must
 * be stopped before [shutdown] or [close].
 */
class NativePcmOutput : AutoCloseable {
    private val service = PcmOutputService()
    private var ringHandle = 0UL
    private var channels = 0

    fun initialize(
        preferredPeriodFrames: Int = DEFAULT_PERIOD_FRAMES,
        preferredOutputDevice: String? = null,
        exclusive: Boolean = false,
    ): PcmOutputDeviceInfo {
        require(preferredPeriodFrames > 0) { "preferredPeriodFrames must be positive" }
        service.setPreferredPeriodFrames(preferredPeriodFrames.toUInt())
        service.setPreferredOutputDevice(preferredOutputDevice.orEmpty())
        service.setPreferredExclusive(exclusive)
        val info = service.initialize()
        ringHandle = if (info.available) service.ringHandle() else 0UL
        channels = info.channels.toInt()
        return info
    }

    /**
     * Writes complete interleaved frames from [buffer]'s current position.
     *
     * The buffer position advances by the bytes accepted by the ring. The return
     * value is the number of complete frames accepted without blocking.
     */
    fun writeInterleaved(buffer: ByteBuffer, frameCount: Int): Int {
        require(buffer.isDirect) { "PCM buffer must be a direct ByteBuffer" }
        require(buffer.order() == ByteOrder.nativeOrder()) {
            "PCM buffer must use native byte order"
        }
        require(frameCount >= 0) { "frameCount must not be negative" }
        check(ringHandle != 0UL && channels > 0) { "PCM output is not initialized" }
        val sampleCount = Math.multiplyExact(frameCount, channels)
        val byteCount = Math.multiplyExact(sampleCount, Float.SIZE_BYTES)
        require(buffer.remaining() >= byteCount) {
            "PCM buffer has ${buffer.remaining()} bytes remaining, but $byteCount are required"
        }
        if (sampleCount == 0) {
            return 0
        }

        val view = buffer.slice().order(ByteOrder.nativeOrder())
        val writtenSamples = PcmOutputDirectBridge.amethyst_pcm_output_write_direct(
            ringHandle.toLong(),
            view,
            sampleCount,
        )
        check(writtenSamples % channels == 0) {
            "Native PCM ring returned a partial frame"
        }
        buffer.position(buffer.position() + writtenSamples * Float.SIZE_BYTES)
        return writtenSamples / channels
    }

    fun start(): String? = service.start()

    fun pause(): String? = service.pause()

    fun telemetry(): PcmOutputTelemetry = service.telemetry()

    /** Allocation-free queue depth for the single producer's pacing loop. */
    fun queuedFrames(): Long {
        check(ringHandle != 0UL) { "PCM output is not initialized" }
        return PcmOutputDirectBridge.amethyst_pcm_output_queued_frames(ringHandle.toLong())
    }

    fun outputDevices(): List<PcmOutputDevice> = service.outputDevices()

    fun promoteCurrentThreadToRealtime(periodFrames: Int, sampleRate: Int): String? =
        service.promoteCurrentThreadToRealtime(
            periodFrames.coerceAtLeast(1).toUInt(),
            sampleRate.coerceAtLeast(1).toUInt(),
        )

    fun shutdown() {
        ringHandle = 0UL
        channels = 0
        service.shutdown()
    }

    override fun close() {
        shutdown()
        service.close()
    }

    companion object {
        const val DEFAULT_PERIOD_FRAMES = 128

        fun allocateBuffer(maximumFrames: Int, channels: Int): ByteBuffer {
            require(maximumFrames > 0) { "maximumFrames must be positive" }
            require(channels > 0) { "channels must be positive" }
            val samples = Math.multiplyExact(maximumFrames, channels)
            return ByteBuffer
                .allocateDirect(Math.multiplyExact(samples, Float.SIZE_BYTES))
                .order(ByteOrder.nativeOrder())
        }
    }
}

private object PcmOutputDirectBridge : Library {
    init {
        try {
            System.loadLibrary("c++_shared")
        } catch (_: UnsatisfiedLinkError) {
        }
        Native.register(
            PcmOutputDirectBridge::class.java,
            System.getProperty("uniffi.component.amethyst_native_engine.libraryOverride")
                ?: "amethyst_native_engine",
        )
    }

    @JvmStatic
    external fun amethyst_pcm_output_write_direct(
        handle: Long,
        samples: ByteBuffer,
        sampleCount: Int,
    ): Int

    @JvmStatic
    external fun amethyst_pcm_output_queued_frames(handle: Long): Long
}
