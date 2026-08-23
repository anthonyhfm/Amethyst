package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.MidiAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class NativeMidiAccess : AutoCloseable {
    private val access = MidiAccess()
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val closed = AtomicBoolean(false)

    val backendName: String get() = access.backendName()

    private val deviceChangeStream = flow {
        while (currentCoroutineContext().isActive) {
            val changed = access.waitForDeviceChange(500uL)
            if (changed) {
                emit(Unit)
            }
        }
    }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    val deviceChanges: Flow<Unit> get() = deviceChangeStream

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
        if (closed.compareAndSet(false, true)) {
            // Native jobs must leave FFI before MidiAccess is destroyed.
            runBlocking { scopeJob.cancelAndJoin() }
            try {
                access.closeAll()
            } finally {
                access.close()
            }
        }
    }
}
