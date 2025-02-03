package dev.anthonyhfm.amethyst.core.midi.data

actual fun getMidiInputData(byteArray: ByteArray): MidiInputData? {
    if (byteArray[1] in 144.toByte()..159.toByte()) {
        return MidiInputData(
            pitch = DRUM_RACK_TO_XY[byteArray[2].toInt()],
            velocity = byteArray[3].toInt(),
        )
    } else if (byteArray[1] in 128.toByte()..143.toByte()) {
        return MidiInputData(
            pitch = DRUM_RACK_TO_XY[byteArray[2].toInt()],
            velocity = 0,
        )
    }

    return null
}