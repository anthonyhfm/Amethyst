package dev.anthonyhfm.amethyst.core.midi.devices.others

import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType

class MatrixDevice : DeviceType {
    override val name: String = "Matrix"

    override fun getEffectSysEx(effect: MidiEffectData): ByteArray {
        return byteArrayOf(
            0xF0.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x03.toByte(),
            0x4D.toByte(),
            0x58.toByte(),
            0x5E.toByte(),
            (effect.y * 10 + effect.x).toByte(),
            (effect.r).toByte(),
            (effect.g).toByte(),
            (effect.b).toByte(),
            0xF7.toByte()
        )
    }
}