package dev.anthonyhfm.amethyst.nativeengine

import kotlin.test.Test
import kotlin.test.assertEquals

class AmethystNativeEngineTest {
    @Test
    fun loadsNativeEngine() {
        assertEquals("Amethyst Native Engine", AmethystNativeEngine.info().name)
        assertEquals("native-engine-ready", AmethystNativeEngine.ping())
    }
}
