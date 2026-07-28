package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection

class LaunchpadDevicePro(
    connection: AmethystMidiDeviceConnection,
    firmware: LaunchpadFirmware = LaunchpadFirmware.Original,
) : LaunchpadDevice(connection, firmware) {
    override fun prepareFastLedUpdates(
        updates: List<RawLEDUpdate>,
    ): List<RawLEDUpdate> = updates.filter { update ->
        val pitch = update.index.toInt() and 0xFF
        pitch in 1..99 && pitch != 9 && pitch != 90
    }

    override fun clear() {
        val clearSysEx = byteArrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 16.toByte(), 14.toByte(), 0.toByte(), 247.toByte())

        sendMidi(clearSysEx)
    }

    override fun sendUpdate(updates: List<RawLEDUpdate>, colors: Array<Color>) {
        if (sendFastLedUpdates(updates)) return

        updates.chunked(78).forEach { chunked ->
            sendMidi(getEffectSysEx(chunked))
        }
    }

    override fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray {
        if (usesFastLedFormat) {
            return getFastLedEffectSysEx(updates)
        }

        return mutableListOf<Byte>().apply {
            addAll(arrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 16.toByte(), 11.toByte()))

            updates.forEach { update ->
                addAll(
                    arrayOf(
                        update.index,
                        (update.color.red * 63).toInt().toByte(),
                        (update.color.green * 63).toInt().toByte(),
                        (update.color.blue * 63).toInt().toByte(),
                    )
                )
            }

            add(247.toByte())
        }.toByteArray()
    }

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 4)

                return cutdown.contentEquals(ubyteArrayOf(0u, 6u, 2u, 0u, 32u, 41u, 81u, 0u, 0u, 0u))
            } catch (e: Exception) {
                return false
            }
        }

    }
}
