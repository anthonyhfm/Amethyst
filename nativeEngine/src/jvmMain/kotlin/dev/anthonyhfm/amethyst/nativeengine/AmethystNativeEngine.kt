package dev.anthonyhfm.amethyst.nativeengine

object AmethystNativeEngine {
    fun info(): NativeEngineInfo = nativeEngineInfo()

    fun ping(): String = NativeEngine().use { engine ->
        engine.ping()
    }
}
