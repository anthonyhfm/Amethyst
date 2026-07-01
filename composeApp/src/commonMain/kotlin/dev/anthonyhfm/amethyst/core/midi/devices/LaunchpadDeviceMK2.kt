package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection
import kotlinx.coroutines.launch

class LaunchpadDeviceMK2(
    connection: AmethystMidiDeviceConnection,
) : LaunchpadDevice(connection) {
    override fun clear() {
        val clearSysEx = byteArrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 24.toByte(), 14.toByte(), 0.toByte(), 247.toByte())

        sendMidi(clearSysEx)
    }

    override fun sendUpdate(updates: List<RawLEDUpdate>, colors: Array<Color>) {
        updates.chunked(78).forEach { chunked ->
            sendMidi(getEffectSysEx(chunked))
        }
    }

    override fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray {
        return mutableListOf<Byte>().apply {
            addAll(arrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 24.toByte(), 11.toByte()))

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

    private fun sendMidi(data: ByteArray) {
        outscope.launch {
            midiOutput.send(data)
        }
    }

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 4)

                return cutdown.contentEquals(ubyteArrayOf(0u, 6u, 2u, 0u, 32u, 41u, 105u, 0u, 0u, 0u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
