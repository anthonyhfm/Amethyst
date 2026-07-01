package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class NativeMidiInput internal constructor(
    private val connection: MidiConnection,
    private val scope: CoroutineScope,
) : AutoCloseable {

    val portId: String get() = connection.portId()

    val messages: Flow<ByteArray> = flow {
        while (connection.isOpen()) {
            val msg = withContext(Dispatchers.IO) {
                connection.receiveTimeout(100uL)
            }
            if (msg != null) {
                emit(msg.data)
            }
        }
    }.flowOn(Dispatchers.IO)

    val events: Flow<MidiEvent> = messages.map { bytes ->
        parseMidiEvent(bytes)
    }

    override fun close() {
        connection.close()
    }
}
