package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CopyCoordinateSupportTest {

    @Test
    fun copyBoundsForLayout_resolvesMainGridForEachSupportedDeviceLayout() {
        assertEquals(
            CopyCoordinateBounds(minX = 0, maxX = 7, minY = 0, maxY = 7),
            copyBoundsForLayout(
                deviceStartX = 0,
                deviceStartY = 0,
                layout = LaunchpadLayout.LAYOUT_8X8,
                mode = CopyBoundsMode.EDGELESS,
            )
        )
        assertEquals(
            CopyCoordinateBounds(minX = 10, maxX = 17, minY = 21, maxY = 28),
            copyBoundsForLayout(
                deviceStartX = 10,
                deviceStartY = 20,
                layout = LaunchpadLayout.LAYOUT_9X9,
                mode = CopyBoundsMode.EDGELESS,
            )
        )
        assertEquals(
            CopyCoordinateBounds(minX = 6, maxX = 13, minY = 8, maxY = 15),
            copyBoundsForLayout(
                deviceStartX = 5,
                deviceStartY = 7,
                layout = LaunchpadLayout.LAYOUT_10X10,
                mode = CopyBoundsMode.EDGELESS,
            )
        )
    }

    @Test
    fun applyCopyCoordinatePolicy_wrapUsesResolvedBoundsInsteadOfHardcodedTenByTen() {
        val signal = Signal.LED(origin = null, x = 0, y = 0, color = Color.White)

        val transformed = applyCopyCoordinatePolicy(
            signal = signal,
            rawX = 13,
            rawY = 9,
            wrapBounds = CopyCoordinateBounds(minX = 5, maxX = 12, minY = 10, maxY = 17),
            isolateBounds = null,
        )

        assertNotNull(transformed)
        assertEquals(5, transformed.x)
        assertEquals(17, transformed.y)
    }

    @Test
    fun applyCopyCoordinatePolicy_isolateDropsSignalsOutsideResolvedBounds() {
        val signal = Signal.LED(origin = null, x = 0, y = 0, color = Color.White)

        val transformed = applyCopyCoordinatePolicy(
            signal = signal,
            rawX = 8,
            rawY = 4,
            wrapBounds = null,
            isolateBounds = CopyCoordinateBounds(minX = 0, maxX = 7, minY = 0, maxY = 7),
        )

        assertNull(transformed)
    }

    @Test
    fun buildInterpolatedCopyFrames_resolvesAbsoluteTargetsPerSignal() {
        val frames = buildInterpolatedCopyFrames(
            triggerSignals = listOf(
                Signal.LED(origin = null, x = 2, y = 2, color = Color.White),
                Signal.LED(origin = null, x = 5, y = 5, color = Color.White),
            ),
            offsets = listOf(
                CopyChainDeviceState.Offset(
                    x = 0,
                    y = 0,
                    isAbsolute = true,
                    absoluteX = 4,
                    absoluteY = 4,
                )
            ),
            reverse = false,
            transformSignal = { signal, x, y -> signal.copy(x = x, y = y) },
        )

        assertEquals(
            listOf(2 to 2, 5 to 5),
            frames.first().map { it.x to it.y }
        )
        assertEquals(
            listOf(4 to 4, 4 to 4),
            frames.last().map { it.x to it.y }
        )
    }

    @Test
    fun buildInterpolatedCopyFrames_usesArcPointsForAngledSegments() {
        val frames = buildInterpolatedCopyFrames(
            triggerSignals = listOf(
                Signal.LED(origin = null, x = 0, y = 0, color = Color.White)
            ),
            offsets = listOf(
                CopyChainDeviceState.Offset(x = 4, y = 0, angle = 90)
            ),
            reverse = false,
            transformSignal = { signal, x, y -> signal.copy(x = x, y = y) },
        )

        assertEquals(0 to 0, frames.first().single().let { it.x to it.y })
        assertEquals(4 to 0, frames.last().single().let { it.x to it.y })
        assertTrue(frames.any { frame -> frame.single().y < 0 })
    }
}
