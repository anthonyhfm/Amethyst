package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.*
import kotlinx.coroutines.*

class NativeMidiAccess : AutoCloseable {
    private val access = MidiAccess()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val backendName: String get() = access.backendName()

    suspend fun discoverDevices(): List<NativeMidiDevice> = withContext(Dispatchers.IO) {
        access.discoverDevices().map { it.toNativeMidiDevice() }
    }

    fun getCachedDevices(): List<NativeMidiDevice> {
        return access.getCachedDevices().map { it.toNativeMidiDevice() }
    }

    suspend fun openInput(portId: String): NativeMidiInput = withContext(Dispatchers.IO) {
        val connection = access.openInput(portId)
        NativeMidiInput(connection, scope)
    }

    suspend fun openOutput(portId: String): NativeMidiOutput = withContext(Dispatchers.IO) {
        val connection = access.openOutput(portId)
        NativeMidiOutput(connection)
    }

    override fun close() {
        scope.cancel()
        access.closeAll()
        access.close()
    }
}
