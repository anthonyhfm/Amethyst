package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.launch
import kotlin.collections.chunked

@OptIn(ExperimentalUnsignedTypes::class)
class LaunchpadDevicePush2(
    override var midiOutput: MidiOutput,
) : LaunchpadDevice() {
    override fun clear() {
        val clearSysEx = byteArrayOf(240.toByte(), 0.toByte(), 32.toByte(), 41.toByte(), 2.toByte(), 16.toByte(), 14.toByte(), 0.toByte(), 247.toByte())

        sendMidi(clearSysEx)
    }

    override fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>) {
        updates.chunked(78).forEach { chunked ->
            /*sendMidi(
                data = ubyteArrayOf(
                    144u, 36u, 127u
                ).toByteArray()
            )*/
        }
    }

    override fun getEffectSysEx(updates: List<RawUpdate>): ByteArray {
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

    private fun sendMidi(data: ByteArray) {
        outscope.launch {
            midiOutput.send(data, 0, data.size, 0)
        }
    }

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun identify(inquiry: UByteArray): Boolean {
            if (inquiry.size > 24) return false

            try {
                val cutdown = inquiry.copyOfRange(2, inquiry.lastIndex - 10)

                println(cutdown.contentToString())

                return cutdown.contentEquals(ubyteArrayOf(1u, 6u, 2u, 0u, 33u, 29u, 103u, 50u, 2u, 0u))
            } catch (e: Exception) {
                return false
            }
        }
    }
}
