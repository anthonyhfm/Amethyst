package dev.anthonyhfm.amethyst.core.midi.data

actual fun getMidiInputData(byteArray: ByteArray): MidiInputData? {
    if (byteArray.size == 3 && byteArray[0] in 144.toByte() .. 159.toByte()) {
        return MidiInputData(
            pitch = DRUM_RACK_TO_XY[byteArray[1].toInt()],
            velocity = byteArray[2].toInt()
        )
    }

    return null
}