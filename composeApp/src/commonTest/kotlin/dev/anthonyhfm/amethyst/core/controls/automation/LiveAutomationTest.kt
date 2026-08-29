package dev.anthonyhfm.amethyst.core.controls.automation

import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterValueResolver
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.audio.automation.AutomationChainDevice
import dev.anthonyhfm.amethyst.devices.audio.automation.AutomationChainDeviceState
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import dev.anthonyhfm.amethyst.workspace.data.ParameterMappingMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveAutomationTest {
    @Test
    fun presetsKeepEndpointsAndHaveDistinctShapes() {
        LiveAutomationCurve.entries.filterNot { it == LiveAutomationCurve.Bezier }.forEach { curve ->
            val automation = LiveAutomation(settings = LiveAutomationSettings(curve = curve))
            assertEquals(-1f, automation.valueAt(0f, 0f), 0.0001f)
            assertEquals(1f, automation.valueAt(1f, 0f), 0.0001f)
        }
        val exponential = LiveAutomation(settings = LiveAutomationSettings(curve = LiveAutomationCurve.Exponential))
        val logarithmic = LiveAutomation(settings = LiveAutomationSettings(curve = LiveAutomationCurve.Logarithmic))
        assertTrue(exponential.valueAt(0.5f, 0f) < 0f)
        assertTrue(logarithmic.valueAt(0.5f, 0f) > 0f)
    }

    @Test
    fun beatDurationSnapshotsBpmAtTrigger() {
        val runtime = LiveAutomationRuntime(
            LiveAutomation(settings = LiveAutomationSettings(durationValue = 2f, timingUnit = LiveAutomationTimingUnit.Beats)),
        )
        runtime.trigger(frame = 100, sampleRate = 1_000, bpm = 120f)

        assertEquals(1_000L, runtime.durationFrames)
        assertEquals(0.5f, runtime.progressAt(600), 0.0001f)
        assertEquals(120f, runtime.startBpm)
    }

    @Test
    fun retriggerPoliciesAreDeterministic() {
        val ignored = LiveAutomationRuntime(
            LiveAutomation(settings = LiveAutomationSettings(durationValue = 100f, timingUnit = LiveAutomationTimingUnit.Milliseconds)),
        )
        ignored.trigger(0, 1_000, 120f)
        ignored.trigger(40, 1_000, 90f)
        assertEquals(0L, ignored.startFrame)

        val restarted = LiveAutomationRuntime(
            LiveAutomation(settings = LiveAutomationSettings(
                durationValue = 100f,
                timingUnit = LiveAutomationTimingUnit.Milliseconds,
                retriggerMode = LiveAutomationRetriggerMode.Restart,
            )),
        )
        restarted.trigger(0, 1_000, 120f)
        restarted.trigger(40, 1_000, 90f)
        assertEquals(40L, restarted.startFrame)
    }

    @Test
    fun continueAndBlendRetriggersStartWithoutAValueJump() {
        val continued = LiveAutomationRuntime(
            LiveAutomation(settings = LiveAutomationSettings(
                durationValue = 100f,
                timingUnit = LiveAutomationTimingUnit.Milliseconds,
                retriggerMode = LiveAutomationRetriggerMode.ContinueFromCurrent,
            )),
        )
        continued.trigger(0, 1_000, 120f)
        val continuedBefore = continued.valueAtFrame(40)
        continued.trigger(40, 1_000, 120f)
        assertEquals(continuedBefore, continued.valueAtFrame(40), 0.0001f)
        assertEquals(1f, continued.valueAtFrame(140), 0.0001f)

        val blended = LiveAutomationRuntime(
            LiveAutomation(settings = LiveAutomationSettings(
                durationValue = 100f,
                timingUnit = LiveAutomationTimingUnit.Milliseconds,
                retriggerMode = LiveAutomationRetriggerMode.Blend,
                blendDurationMs = 20f,
            )),
        )
        blended.trigger(0, 1_000, 120f)
        val blendedBefore = blended.valueAtFrame(40)
        blended.trigger(40, 1_000, 120f)
        assertEquals(blendedBefore, blended.valueAtFrame(40), 0.0001f)
        assertTrue(blended.valueAtFrame(60).isFinite())
    }

    @Test
    fun mappingsResolveInStableOrderBeforeDirectAutomation() {
        val descriptor = ParameterDescriptor("gain", "Gain", minimum = 0f, maximum = 100f)
        val target = ParameterAddress("device", "gain")
        val mappings = listOf(
            ParameterMapping("a", "macro-a", target, 0.2f, 0.8f, inverted = true),
            ParameterMapping("b", "macro-b", target, 0f, 0.2f, mode = ParameterMappingMode.Additive),
        )
        val mapped = ParameterValueResolver.resolve(
            baseValue = 50f,
            descriptor = descriptor,
            target = target,
            mappings = mappings,
            macroValues = mapOf("macro-a" to 0.25f, "macro-b" to 0.5f),
        )
        assertEquals(75f, mapped, 0.0001f)
        val automated = ParameterValueResolver.resolve(
            50f, descriptor, target, mappings, emptyMap(), directAutomation = 0.3f,
        )
        assertEquals(30f, automated, 0.0001f)
    }

    @Test
    fun mappingsWithMissingMacrosOrTargetsAreSafeNoOps() {
        val descriptor = ParameterDescriptor("gain", "Gain", minimum = 0f, maximum = 100f)
        val target = ParameterAddress("device", "gain")
        val mappings = listOf(
            ParameterMapping("missing-macro", "deleted-macro", target, 0f, 1f),
            ParameterMapping(
                "missing-target",
                "macro-a",
                ParameterAddress("deleted-device", "gain"),
                0f,
                1f,
            ),
        )

        assertEquals(
            50f,
            ParameterValueResolver.resolve(50f, descriptor, target, mappings, mapOf("macro-a" to 1f)),
            0.0001f,
        )
    }

    @Test
    fun padStartsMacroAutomationOnAudioFrameClock() {
        val target = LiveAutomationTarget.Macro("macro-a")
        val device = AutomationChainDevice().apply {
            state.value = AutomationChainDeviceState(
                target = target,
                automation = LiveAutomation(settings = LiveAutomationSettings(
                    durationValue = 100f,
                    timingUnit = LiveAutomationTimingUnit.Milliseconds,
                )),
            )
        }
        val chain = AudioChain().apply {
            add(device, fromUser = false)
            prepareAudio(AudioConfiguration(1_000, 2, 64))
        }
        val block = AudioProcessingBlock(FloatArray(128), 2, 64).apply { configure(25, 0) }

        device.signalEnter(listOf(Signal.Midi("pad", 1, 1, 127)))
        chain.processAudio(block, AudioRenderContext(1_000, 0))

        val value = checkNotNull(device.audioTriggerRuntime?.automationValue(target, 25))
        assertTrue(abs(value - 0.25f) < 0.001f)
        assertTrue(device.isAutomationRunning)
        assertFalse(block.samples.any { !it.isFinite() })
    }

    @Test
    fun padUpStopsOnlyTheAutomationStartedByTheSamePad() {
        val device = AutomationChainDevice().apply {
            state.value = AutomationChainDeviceState(
                target = LiveAutomationTarget.Macro("macro-a"),
                automation = LiveAutomation(settings = LiveAutomationSettings(
                    durationValue = 1_000f,
                    timingUnit = LiveAutomationTimingUnit.Milliseconds,
                    stopOnPadUp = true,
                )),
            )
        }
        val chain = AudioChain().apply {
            add(device, fromUser = false)
            prepareAudio(AudioConfiguration(1_000, 2, 64))
        }
        val block = AudioProcessingBlock(FloatArray(128), 2, 64)

        device.signalEnter(listOf(Signal.Midi("pad", 1, 1, 127)))
        block.configure(1, 0)
        chain.processAudio(block, AudioRenderContext(1_000, 0))
        assertTrue(device.isAutomationRunning)

        device.signalEnter(listOf(Signal.Midi("pad", 2, 1, 0)))
        block.configure(1, 1)
        chain.processAudio(block, AudioRenderContext(1_000, 1))
        assertTrue(device.isAutomationRunning)

        device.signalEnter(listOf(Signal.Midi("pad", 1, 1, 0)))
        block.configure(1, 2)
        chain.processAudio(block, AudioRenderContext(1_000, 2))
        assertFalse(device.isAutomationRunning)
    }

    @Test
    fun automationDeviceStateRoundTripsItsPersistentTarget() {
        val original = AutomationChainDeviceState(
            target = LiveAutomationTarget.Parameter(ParameterAddress("device-a", "cutoff")),
            automation = LiveAutomation(
                parameterId = "cutoff",
                settings = LiveAutomationSettings(
                    curve = LiveAutomationCurve.SCurve,
                    retriggerMode = LiveAutomationRetriggerMode.Blend,
                    blendDurationMs = 42f,
                ),
            ),
        )

        val restored = DeviceRegistry.deepCopyState(original) as AutomationChainDeviceState

        assertEquals(original, restored)
    }
}
