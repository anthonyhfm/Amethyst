package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection
import dev.anthonyhfm.amethyst.core.util.FastRgbMidiFighter

class LaunchpadDeviceMidiFighter(
    connection: AmethystMidiDeviceConnection,
    firmware: LaunchpadFirmware = LaunchpadFirmware.Mat1jaczyyy,
) : LaunchpadDevice(connection, firmware) {
    override fun clear() {
        val clearUpdates = (0 until FastRgbMidiFighter.BUTTON_COUNT).map {
            RawLEDUpdate(it.toByte(), Color.Black)
        }
        val payload = FastRgbMidiFighter.compress(clearUpdates)
        sendMidi(getFastLedSysEx(payload))
    }

    override fun prepareFastLedUpdates(updates: List<RawLEDUpdate>): List<RawLEDUpdate> {
        return FastRgbMidiFighter.prepareUpdates(updates)
    }

    override fun encodeUpdate(updates: List<RawLEDUpdate>): List<ByteArray> {
        val addressableUpdates = prepareFastLedUpdates(updates)
        if (addressableUpdates.isEmpty()) return emptyList()

        return FastRgbMidiFighter.compressToChunks(
            addressableUpdates,
            MAT1JACZYYY_FAST_LED_MAX_PAYLOAD_SIZE,
        ).map { payload ->
            getFastLedSysEx(payload)
        }
    }

    override fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray {
        val addressableUpdates = prepareFastLedUpdates(updates)
        val payload = FastRgbMidiFighter.compress(addressableUpdates)
        return getFastLedSysEx(payload)
    }

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 4)

                return cutdown.contentEquals(ubyteArrayOf(127u, 6u, 2u, 0x00u, 0x01u, 0x79u, 0x06u, 0x00u, 0x01u, 0x00u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
