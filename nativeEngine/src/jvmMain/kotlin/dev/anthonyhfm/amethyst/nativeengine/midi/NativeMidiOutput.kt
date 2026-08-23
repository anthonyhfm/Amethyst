package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.MidiConnection
import dev.anthonyhfm.amethyst.nativeengine.MidiEvent
import dev.anthonyhfm.amethyst.nativeengine.midiControlChange
import dev.anthonyhfm.amethyst.nativeengine.midiDeviceInquiry
import dev.anthonyhfm.amethyst.nativeengine.midiNoteOff
import dev.anthonyhfm.amethyst.nativeengine.midiNoteOn
import dev.anthonyhfm.amethyst.nativeengine.midiSysex
import java.util.concurrent.atomic.AtomicBoolean

class NativeMidiOutput internal constructor(
    private val connection: MidiConnection,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val portId: String = connection.portId()
    val isOpen: Boolean get() = !closed.get() && connection.isOpen()

    fun send(data: ByteArray) {
        check(isOpen) { "MIDI output $portId is closed" }
        connection.send(data)
    }

    fun sendEvent(event: MidiEvent) {
        check(isOpen) { "MIDI output $portId is closed" }
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
        if (closed.compareAndSet(false, true)) {
            try {
                connection.disconnect()
            } finally {
                connection.close()
            }
        }
    }
}
