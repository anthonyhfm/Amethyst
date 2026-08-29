@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.devices.effects

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import dev.anthonyhfm.amethyst.core.parameter.resolveControlParameter
import dev.anthonyhfm.amethyst.core.parameter.resolveRealtimeParameter
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.audio.effects.AudioDelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.LightDelayRouting
import dev.anthonyhfm.amethyst.devices.effects.delay.applyLightDelay
import dev.anthonyhfm.amethyst.devices.effects.reverb.LightReverbChainDevice
import dev.anthonyhfm.amethyst.devices.effects.reverb.LightReverbChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.reverb.diffused
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightAudioInspiredEffectsTest {
    private val led = Signal.LED("test", 3, 4, Color.Red, layer = 2, opacity = 0.8f)

    @Test
    fun lightDelayPingPongsAcrossConfiguredSidesAndLayers() {
        val sides = DelayChainDeviceState(
            routing = LightDelayRouting.PingPongSides,
            sideA = 1,
            sideB = 6,
        )
        val first = led.applyLightDelay(sides.routing, 1, 0.5f, sides) as Signal.LED
        val second = led.applyLightDelay(sides.routing, 2, 0.25f, sides) as Signal.LED
        assertEquals(6, first.x)
        assertEquals(1, second.x)
        assertEquals(0.4f, first.opacity, 0.0001f)
        assertEquals(0.2f, second.opacity, 0.0001f)

        val layers = sides.copy(routing = LightDelayRouting.PingPongLayers, layerA = 3, layerB = 9)
        assertEquals(9, (led.applyLightDelay(layers.routing, 1, 1f, layers) as Signal.LED).layer)
        assertEquals(3, (led.applyLightDelay(layers.routing, 2, 1f, layers) as Signal.LED).layer)
    }

    @Test
    fun lightReverbDiffusionIsBoundedAndDecaysOpacity() {
        val diffused = led.diffused(step = 8, amount = 1f, opacityMultiplier = 0.2f) as Signal.LED
        assertTrue(diffused.x in 0..7)
        assertTrue(diffused.y in 0..7)
        assertEquals(0.16f, diffused.opacity, 0.0001f)
        assertTrue(LightReverbChainDevice.decaySteps(1f) <= LightReverbChainDevice.MAX_STEPS)
    }

    @Test
    fun heavenJobsAreHardLimitedAndChokeable() {
        val delay = DelayChainDevice().apply {
            state.value = DelayChainDeviceState(repeats = DelayChainDevice.MAX_REPEATS)
        }
        repeat(20) { delay.signalEnter(emptyList()) }
        assertTrue(delay.activeJobCount <= DelayChainDevice.MAX_ACTIVE_JOBS)
        delay.onChoke()
        assertEquals(0, delay.activeJobCount)

        val reverb = LightReverbChainDevice().apply {
            state.value = LightReverbChainDeviceState(size = 1f)
        }
        repeat(20) { reverb.signalEnter(emptyList()) }
        assertTrue(reverb.activeJobCount <= LightReverbChainDevice.MAX_ACTIVE_JOBS)
        reverb.onChoke()
        assertEquals(0, reverb.activeJobCount)
    }

    @Test
    fun legacyDelayAndNewLightStatesRoundTrip() {
        val legacy = LegacyDelayState(Timing.Rythm(Timing.Rythm.RythmTiming._1_8), 250, 0.75f)
        val restored = ProtoBuf.decodeFromByteArray<DelayChainDeviceState>(
            ProtoBuf.encodeToByteArray(legacy),
        )
        assertEquals(legacy.timing, restored.timing)
        assertEquals(legacy.gate, restored.gate)
        assertEquals(3, restored.repeats)
        assertEquals(LightDelayRouting.Direct, restored.routing)

        val delay = DelayChainDeviceState(feedback = 0.8f, repeats = 6, routing = LightDelayRouting.PingPongSides)
        val reverb = LightReverbChainDeviceState(size = 0.8f, decay = 0.9f)
        assertEquals(delay, DeviceRegistry.deepCopyState(delay))
        assertEquals(reverb, DeviceRegistry.deepCopyState(reverb))
    }

    @Test
    fun oneMacroCanDriveAudioAndLightParametersTogether() {
        val previousMacros = WorkspaceRepository.macros.value
        val previousMappings = WorkspaceRepository.parameterMappings.value
        val macro = Macro(value = 127, id = "shared-macro", name = "Shared FX")
        val light = DelayChainDevice().apply { selectionUUID = "light-delay" }
        val audio = AudioDelayChainDevice().apply { selectionUUID = "audio-delay" }
        try {
            WorkspaceRepository.setMacros(listOf(macro), undoable = false)
            WorkspaceRepository.setParameterMappings(
                listOf(
                    ParameterMapping(macroId = macro.id, target = ParameterAddress(light.selectionUUID, "feedback")),
                    ParameterMapping(macroId = macro.id, target = ParameterAddress(audio.selectionUUID, "feedback")),
                ),
                undoable = false,
            )
            assertEquals(1f, light.resolveControlParameter(DelayChainDevice.PARAMETERS[1], 0f), 0.0001f)
            assertEquals(0.98f, audio.resolveRealtimeParameter(AudioDelayChainDevice.PARAMETERS[1], 0f, 0), 0.0001f)
        } finally {
            WorkspaceRepository.setMacros(previousMacros, undoable = false)
            WorkspaceRepository.setParameterMappings(previousMappings, undoable = false)
        }
    }
}

@Serializable
private data class LegacyDelayState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f,
)
