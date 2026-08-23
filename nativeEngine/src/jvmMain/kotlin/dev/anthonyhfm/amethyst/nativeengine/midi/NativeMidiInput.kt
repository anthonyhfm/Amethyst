package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.MidiConnection
import dev.anthonyhfm.amethyst.nativeengine.MidiEvent
import dev.anthonyhfm.amethyst.nativeengine.MidiMessage
import dev.anthonyhfm.amethyst.nativeengine.parseMidiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.atomic.AtomicBoolean

class NativeMidiInput internal constructor(
    private val connection: MidiConnection,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val portId: String = connection.portId()
    val isOpen: Boolean get() = !closed.get() && connection.isOpen()

    private val nativeMessageStream = flow {
        while (isOpen) {
            connection.receiveTimeout(100uL)?.let { emit(it) }
        }
    }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    /** Messages with their normalized timestamp and originating port. */
    val messageRecords: Flow<MidiMessage> get() = nativeMessageStream

    val messages: Flow<ByteArray> = messageRecords.map { it.data }

    val events: Flow<MidiEvent> = messages.map { bytes ->
        parseMidiEvent(bytes)
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
