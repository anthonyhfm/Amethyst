package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val colors = updates.map { update -> update.color }.filter { it != Color.Black }.toSet()
        val paletteMap: MutableMap<Color, Int> = mutableMapOf()

        colors.forEachIndexed { index, color ->
            paletteMap[color] = 127 - index

            val r = (color.red * 255).toInt().toUByte()
            val g = (color.green * 255).toInt().toUByte()
            val b = (color.blue * 255).toInt().toUByte()

            val byteArray = ubyteArrayOf(
                240u, 0u, 33u, 29u, 1u, 1u, 3u, (127 - index).toUByte(), r and 0x7Fu, (r.toInt() ushr 7 and 0x01).toUByte(), g and 0x7Fu, (g.toInt() ushr 7 and 0x01).toUByte(), b and 0x7Fu, (b.toInt() ushr 7 and 0x01).toUByte(), 126u, 0u, 247u
            )

            sendMidi(byteArray.toByteArray())
        }

        updates.forEach { update ->
            val x: Int = update.index % 10
            val y: Int = update.index / 10

            if (y < 0 || y > 8 || x < 0 || x > 8) {
                return@forEach
            }

            sendMidi(
                data = ubyteArrayOf(
                    144u, (36 + x - 1+ ((y - 1) * 8)).toUByte(), (paletteMap[update.color] ?: 0).toUByte()
                ).toByteArray(),
            )
        }
    }

    override fun getEffectSysEx(updates: List<RawUpdate>): ByteArray {
        throw UnsupportedOperationException("Push2 doesn't support regular rgb effects!")
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

                return cutdown.contentEquals(ubyteArrayOf(1u, 6u, 2u, 0u, 33u, 29u, 103u, 50u, 2u, 0u))
            } catch (e: Exception) {
                return false
            }
        }

        fun getMidiInputData(raw: ByteArray): MidiInputData? {
            if (raw.size == 3 && raw[0] in 144.toByte() .. 159.toByte()) {
                return MidiInputData(
                    pitch = 11 + ((raw[1].toInt() - 36) % 8) + (((raw[1].toInt() - 36) / 8) * 10),
                    velocity = raw[2].toInt()
                )
            } else if (raw.size == 3 && raw[0] in 128.toByte() .. 143.toByte()) {
                return MidiInputData(
                    pitch = 11 + ((raw[1].toInt() - 36) % 8) + (((raw[1].toInt() - 36) / 8) * 10),
                    velocity = 0
                )
            }

            return null
        }
    }
}
