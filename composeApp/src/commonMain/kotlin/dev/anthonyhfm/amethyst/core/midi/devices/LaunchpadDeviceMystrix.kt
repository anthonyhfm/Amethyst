package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LaunchpadDeviceMystrix(
    override var midiOutput: MidiOutput
) : LaunchpadDevice() {
    val outscope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun clear() { }

    override fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>) {
        updates.forEach {
            sendMidi(getEffectSysEx(it))
        }
    }

    override fun getEffectSysEx(update: RawUpdate): ByteArray {
        return byteArrayOf(
            0xF0.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x03.toByte(),
            0x4D.toByte(),
            0x58.toByte(),
            0x5E.toByte(),
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
}
