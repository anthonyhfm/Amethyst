package dev.anthonyhfm.amethyst.core.midi.data

@OptIn(ExperimentalUnsignedTypes::class)
actual fun getMidiInputData(byteArray: ByteArray): MidiInputData? {
    if (byteArray.size == 3 && byteArray[0] in 144.toByte() .. 159.toByte()) {
        return MidiInputData(
            pitch = DRUM_RACK_TO_XY[byteArray[1].toInt()],
            velocity = byteArray[2].toInt()
        )
    } else if (byteArray.size == 3 && byteArray[0] in 128.toByte() .. 143.toByte()) {
        return MidiInputData(
            pitch = DRUM_RACK_TO_XY[byteArray[1].toInt()],
            velocity = 0
        )
    }

    return null
}