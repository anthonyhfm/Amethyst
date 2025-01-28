package dev.anthonyhfm.amethyst.core.midi.devices

import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData

interface DeviceType {
    val name: String

    fun getEffectSysEx(effect: MidiEffectData): ByteArray {
        return byteArrayOf()
    }
}