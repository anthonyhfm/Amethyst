package dev.anthonyhfm.amethyst.nativeengine

import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiAccess

object AmethystNativeEngine {
    fun info(): NativeEngineInfo = nativeEngineInfo()

    fun ping(): String = NativeEngine().use { engine ->
        engine.ping()
    }

    fun createMidiAccess(): NativeMidiAccess = NativeMidiAccess()
}
