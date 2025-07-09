package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.launch

class LaunchpadDeviceX(
    override var midiOutput: MidiOutput,
) : LaunchpadDevice() {
    override fun clear() { }

    override fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>) {
        updates.chunked(78).forEach { chunked ->
            sendMidi(getEffectSysEx(chunked))
        }
    }

    override fun getEffectSysEx(updates: List<RawUpdate>): ByteArray {
        return mutableListOf<Byte>().apply {
            addAll(arrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 12.toByte(), 3.toByte()))

            updates.forEach { update ->
                addAll(
                    arrayOf(
                        3.toByte(),
                        update.index,
                        (update.color.red * 127).toInt().toByte(),
                        (update.color.green * 127).toInt().toByte(),
                        (update.color.blue * 127).toInt().toByte(),
                    )
                )
            }

            add(247.toByte())
        }.toByteArray()
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

                return cutdown.contentEquals(ubyteArrayOf(0u, 6u, 2u, 0u, 32u, 41u, 19u, 1u, 0u, 0u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
