package dev.anthonyhfm.amethyst.core.midi.devices

import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection

class LaunchpadDeviceMiniMk3(
    connection: AmethystMidiDeviceConnection,
    firmware: LaunchpadFirmware = LaunchpadFirmware.Original,
) : LaunchpadDevice(connection, firmware) {
    override fun prepareFastLedUpdates(
        updates: List<RawLEDUpdate>,
    ): List<RawLEDUpdate> = updates.filter(::isNineByNineFastLedPitch)

    override fun clear() { }

    override fun encodeUpdate(updates: List<RawLEDUpdate>): List<ByteArray> {
        val addressableUpdates = prepareFastLedUpdates(updates)
        return encodeFastLedUpdates(addressableUpdates)
            // Launchpad Mini MK3 Programmer's Reference: RGB LED SysEx supports up to 81 LEDs.
            ?: addressableUpdates.chunked(81).map(::getEffectSysEx)
    }

    override fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray {
        if (usesFastLedFormat) {
            return getFastLedEffectSysEx(updates)
        }

        return mutableListOf<Byte>().apply {
            addAll(arrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 13.toByte(), 3.toByte()))

            updates.forEach { update ->
                addAll(
                    arrayOf(
                        3.toByte(),
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
        private fun isNineByNineFastLedPitch(update: RawLEDUpdate): Boolean {
            val pitch = update.index.toInt() and 0xFF
            return pitch / 10 in 1..9 && pitch % 10 in 1..9
        }

        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 4)

                return cutdown.contentEquals(ubyteArrayOf(0u, 6u, 2u, 0u, 32u, 41u, 19u, 1u, 0u, 0u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
