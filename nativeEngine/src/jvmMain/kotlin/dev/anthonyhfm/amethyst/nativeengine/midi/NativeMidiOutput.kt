package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.*

class NativeMidiOutput internal constructor(
    private val connection: MidiConnection,
) : AutoCloseable {

    val portId: String get() = connection.portId()

    fun send(data: ByteArray) {
        connection.send(data)
    }

    fun sendEvent(event: MidiEvent) {
        connection.sendEvent(event)
    }

    fun noteOn(channel: Int, note: Int, velocity: Int) {
        val bytes = midiNoteOn(channel.toUByte(), note.toUByte(), velocity.toUByte())
        send(bytes)
    }

    fun noteOff(channel: Int, note: Int, velocity: Int = 0) {
        val bytes = midiNoteOff(channel.toUByte(), note.toUByte(), velocity.toUByte())
        send(bytes)
    }

    fun controlChange(channel: Int, controller: Int, value: Int) {
        val bytes = midiControlChange(channel.toUByte(), controller.toUByte(), value.toUByte())
        send(bytes)
    }

    fun sendSysEx(data: ByteArray) {
        val bytes = midiSysex(data)
        send(bytes)
    }

    fun sendDeviceInquiry() {
        val bytes = midiDeviceInquiry()
        send(bytes)
    }

    override fun close() {
        connection.disconnect()
        connection.close()
    }
}
