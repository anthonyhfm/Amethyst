package dev.anthonyhfm.amethyst.core.engine.audio.trigger

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PadTriggerEventTest {
    @Test
    fun downAndUpShareKeyAndUseAudioFrameTime() {
        val down = Signal.Midi("launchpad-a", 3, 5, 127).toPadTriggerEvent(1_024)
        val up = Signal.Midi("launchpad-a", 3, 5, 0).toPadTriggerEvent(1_120)

        assertEquals(down.key, up.key)
        assertEquals(TriggerPhase.Down, down.phase)
        assertEquals(TriggerPhase.Up, up.phase)
        assertEquals(1_024, down.targetFrame)
        assertEquals(1_120, up.targetFrame)
    }

    @Test
    fun physicalVirtualAndAutoplayOriginsRemainIndependent() {
        val physical = Signal.Midi("launchpad-a", 3, 5, 127).toPadTriggerEvent(0)
        val virtual = Signal.Midi("virtual-pad", 3, 5, 127).toPadTriggerEvent(0)
        val autoplay = Signal.Midi("autoplay", 3, 5, 127).toPadTriggerEvent(0)

        assertNotEquals(physical.key, virtual.key)
        assertNotEquals(virtual.key, autoplay.key)
        assertEquals("autoplay", autoplay.key.originId)
    }
}
