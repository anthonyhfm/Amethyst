package dev.anthonyhfm.amethyst.core.parameter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParameterFoundationTest {
    @Test
    fun logarithmicDescriptorRoundTripsNormalizedValues() {
        val descriptor = ParameterDescriptor(
            id = "cutoff",
            label = "Cutoff",
            unit = "Hz",
            minimum = 20f,
            maximum = 20_000f,
            defaultValue = 1_000f,
            scale = ParameterScale.Logarithmic,
        )

        val restored = descriptor.denormalize(descriptor.normalize(1_000f))

        assertTrue(kotlin.math.abs(restored - 1_000f) < 0.01f)
    }

    @Test
    fun descriptorClampsAndSnapsInvalidInput() {
        val descriptor = ParameterDescriptor(
            id = "slope",
            label = "Slope",
            minimum = 12f,
            maximum = 24f,
            defaultValue = 12f,
            scale = ParameterScale.Discrete,
            snapPoints = listOf(12f, 24f),
        )

        assertEquals(24f, descriptor.clamp(23f))
        assertEquals(12f, descriptor.clamp(Float.NaN))
        assertFailsWith<IllegalArgumentException> { ParameterAddress("", "gain") }
    }

    @Test
    fun smootherReachesPublishedTargetWithoutOvershoot() {
        val parameter = SmoothedParameter(0f)
        parameter.setTarget(1f)

        val values = List(5) {
            parameter.next(sampleRate = 1_000, smoothing = ParameterSmoothing(4f))
        }

        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f, 1f), values)
    }

    @Test
    fun snapshotCopiesProducerArrayBeforePublishing() {
        val producerValues = floatArrayOf(0.25f, 0.75f)
        val snapshot = ParameterSnapshot.of(producerValues)
        producerValues[0] = 1f

        assertEquals(0.25f, snapshot[0])
    }
}
