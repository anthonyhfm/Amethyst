package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LaunchpadDeviceProMk3(
    override var midiOutput: MidiOutput,
) : LaunchpadDevice() {
    val outscope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
            14.toByte(),
            3.toByte(),
            3.toByte(),
            update.index,
            (update.color.red * 127).toInt().toByte(),
            (update.color.green * 127).toInt().toByte(),
            (update.color.blue * 127).toInt().toByte(),
            247.toByte()
        )
    }

    private fun sendMidi(data: ByteArray) {
        outscope.launch {
            midiOutput.send(data, 0, data.size, 0)
        }
    }
}
