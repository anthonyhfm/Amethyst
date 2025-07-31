package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.launch

class LaunchpadDeviceMystrix(
    override var midiOutput: MidiOutput
) : LaunchpadDevice() {
    override fun clear() { }

    override fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>) {
        updates.chunked(78).forEach { chunked ->
            sendMidi(getEffectSysEx(chunked))
        }
    }

    override fun getEffectSysEx(updates: List<RawUpdate>): ByteArray {
        return mutableListOf<Byte>().apply {
            addAll(arrayOf(0xF0.toByte(), 0x00.toByte(), 0x02.toByte(), 0x03.toByte(), 0x4D.toByte(), 0x58.toByte(), 0x5E.toByte()))

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
            midiOutput.send(data, 0, data.size, 0)
        }
    }

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 18) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 4)

                return cutdown.contentEquals(ubyteArrayOf(127u, 6u, 2u, 0u, 2u, 3u, 77u,88u, 17u, 1u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
