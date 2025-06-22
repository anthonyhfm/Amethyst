package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.launch

class LaunchpadDevicePro(
    override var midiOutput: MidiOutput,
    val isCFW: Boolean = false,
) : LaunchpadDevice() {
    override fun clear() {
        val clearSysEx = byteArrayOf(0xF0.toByte(), 0x00.toByte(), 0x20.toByte(), 0x29.toByte(), 0x02.toByte(), 0x0E.toByte(), 0x03.toByte(), 0x00.toByte(), 0xF7.toByte())

        sendMidi(clearSysEx)
    }

    override fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>) {
        updates.forEach {
            sendMidi(getEffectSysEx(it))
        }
    }

    override fun getEffectSysEx(update: RawUpdate): ByteArray {
        return byteArrayOf(
            240.toByte(),
            0.toByte(),
            32.toByte(),
            41.toByte(),
            2.toByte(),
            16.toByte(),
            11.toByte(),
            update.index,
            (update.color.red * 63).toInt().toByte(),
            (update.color.green * 63).toInt().toByte(),
            (update.color.blue * 63).toInt().toByte(),
            247.toByte()
        )
    }

    private fun sendMidi(data: ByteArray) {
        outscope.launch {
            midiOutput.send(data, 0, data.size, 0)
        }
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

        /**
         * Special function for the Launchpad Pro to recognize the response of Mat1jaczyyy's lpp-performance-cfw
         */
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identifyCFW(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex)

                return cutdown.contentEquals(ubyteArrayOf(0u, 6u, 2u, 0u, 32u, 41u, 81u, 0u, 0u, 0u, 0u, 99u, 102u, 121u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
