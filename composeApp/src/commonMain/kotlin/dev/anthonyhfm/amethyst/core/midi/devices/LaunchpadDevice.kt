package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.heaven.Screen
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiOutput
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection
import dev.anthonyhfm.amethyst.core.util.FastLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

abstract class LaunchpadDevice(
    open val connection: AmethystMidiDeviceConnection,
    val firmware: LaunchpadFirmware,
) {
    val screen: Screen = Screen()

    protected val outscope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val sendChannel = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        screen.screenExit = { updates, colors ->
            sendUpdate(updates, colors)
        }

        outscope.launch {
            for (data in sendChannel) {
                runCatching {
                    midiOutput.send(data)
                }
            }
        }
    }

    val midiOutput: AmethystMidiOutput get() = connection.output

    abstract fun clear()

    abstract fun sendUpdate(updates: List<RawLEDUpdate>, colors: Array<Color>)

    abstract fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray

    open fun handleMidiInput(inputData: ByteArray): MidiInputData? {
        return dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData(inputData)
    }

    protected fun sendMidi(data: ByteArray) {
        sendChannel.trySend(data)
    }

    protected fun sendFastLedUpdates(updates: List<RawLEDUpdate>): Boolean {
        if (!usesFastLedFormat) return false

        val addressableUpdates = prepareFastLedUpdates(updates)
        FastLED.compressToChunks(addressableUpdates, fastLedMaxPayloadSize).forEach { payload ->
            sendMidi(getFastLedSysEx(payload))
        }
        return true
    }

    protected fun getFastLedEffectSysEx(updates: List<RawLEDUpdate>): ByteArray {
        return getFastLedSysEx(FastLED.compress(prepareFastLedUpdates(updates)))
    }

    /**
     * FastLED reserves 0 and 100..119 for surface, row and column macros.
     * Devices must remove non-existent edge coordinates before encoding so an
     * invalid physical LED can never turn into one of those commands.
     */
    protected open fun prepareFastLedUpdates(
        updates: List<RawLEDUpdate>,
    ): List<RawLEDUpdate> = updates

    private fun getFastLedSysEx(payload: ByteArray): ByteArray {
        return FAST_LED_HEADER + payload + SYSEX_END
    }

    protected val usesFastLedFormat: Boolean
        get() = firmware == LaunchpadFirmware.Mat1jaczyyy ||
            firmware == LaunchpadFirmware.CoreFW

    private val fastLedMaxPayloadSize: Int
        get() = when (firmware) {
            LaunchpadFirmware.Mat1jaczyyy -> MAT1JACZYYY_FAST_LED_MAX_PAYLOAD_SIZE
            LaunchpadFirmware.CoreFW -> CORE_FW_FAST_LED_MAX_PAYLOAD_SIZE
            LaunchpadFirmware.Original -> error("Original firmware does not support FastLED")
        }

    private companion object {
        // F0 5F <payload> F7: Mat1jaczyyy allows 320 total bytes; CoreFW uses
        // 256-byte SysEx buffers on most supported Launchpad targets.
        const val MAT1JACZYYY_FAST_LED_MAX_PAYLOAD_SIZE = 317
        const val CORE_FW_FAST_LED_MAX_PAYLOAD_SIZE = 253
        val FAST_LED_HEADER = byteArrayOf(0xF0.toByte(), 0x5F)
        val SYSEX_END = byteArrayOf(0xF7.toByte())
    }
}

sealed class LaunchpadFirmware(val label: String) {
    data object Original : LaunchpadFirmware("Original")
    data object Mat1jaczyyy : LaunchpadFirmware("Mat1jaczyyy")
    data object CoreFW : LaunchpadFirmware("CoreFW")
}

enum class LaunchpadDeviceType(val label: String) {
    LAUNCHPAD_PRO_MK3(
        label = "Launchpad Pro Mk3"
    ),
    LAUNCHPAD_X(
        label = "Launchpad X"
    ),
    LAUNCHPAD_MINI_MK3(
        label = "Launchpad Mini Mk3"
    ),
    LAUNCHPAD_PRO(
        label = "Launchpad Pro"
    ),
    LAUNCHPAD_MK2(
        label = "Launchpad MK2"
    ),
    MYSTRIX(
        label = "Mystrix"
    ),
}
