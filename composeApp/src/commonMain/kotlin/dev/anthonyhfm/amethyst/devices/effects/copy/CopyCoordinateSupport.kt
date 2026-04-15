package dev.anthonyhfm.amethyst.devices.effects.copy

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class CopyBoundsMode {
    NONE,
    EDGELESS,
    FULL,
}

internal data class CopyCoordinateBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    val width: Int = maxX - minX + 1
    val height: Int = maxY - minY + 1

    fun contains(x: Int, y: Int): Boolean = x in minX..maxX && y in minY..maxY

    fun wrap(x: Int, y: Int): Pair<Int, Int> {
        return wrapCoordinate(x, minX, width) to wrapCoordinate(y, minY, height)
    }

    private fun wrapCoordinate(value: Int, min: Int, size: Int): Int {
        return min + ((value - min) % size + size) % size
    }
}

internal fun CopyChainDeviceState.GridMode.toBoundsMode(): CopyBoundsMode = when (this) {
    CopyChainDeviceState.GridMode.NONE -> CopyBoundsMode.NONE
    CopyChainDeviceState.GridMode.EDGELESS -> CopyBoundsMode.EDGELESS
    CopyChainDeviceState.GridMode.FULL -> CopyBoundsMode.FULL
}

internal fun CopyChainDeviceState.IsolationType.toBoundsMode(): CopyBoundsMode = when (this) {
    CopyChainDeviceState.IsolationType.NONE -> CopyBoundsMode.NONE
    CopyChainDeviceState.IsolationType.EDGELESS -> CopyBoundsMode.EDGELESS
    CopyChainDeviceState.IsolationType.FULL -> CopyBoundsMode.FULL
}

internal fun copyBoundsForLayout(
    deviceStartX: Int,
    deviceStartY: Int,
    layout: LaunchpadLayout,
    mode: CopyBoundsMode,
): CopyCoordinateBounds? {
    return when (mode) {
        CopyBoundsMode.NONE -> null
        CopyBoundsMode.EDGELESS -> CopyCoordinateBounds(
            minX = deviceStartX + layout.mainOffsetX,
            maxX = deviceStartX + layout.mainGridMaxX,
            minY = deviceStartY + layout.mainOffsetY,
            maxY = deviceStartY + layout.mainGridMaxY,
        )

        CopyBoundsMode.FULL -> CopyCoordinateBounds(
            minX = deviceStartX,
            maxX = deviceStartX + layout.cols - 1,
            minY = deviceStartY,
            maxY = deviceStartY + layout.rows - 1,
        )
    }
}

internal fun resolveCopyCoordinateBounds(origin: Any?, mode: CopyBoundsMode): CopyCoordinateBounds? {
    val device = origin as? LaunchpadViewportElement ?: return null
    return copyBoundsForLayout(
        deviceStartX = device.position.value.x.toInt(),
        deviceStartY = device.position.value.y.toInt(),
        layout = device.layout,
        mode = mode,
    )
}

internal fun resolveCopyTarget(
    signal: Signal.LED,
    offset: CopyChainDeviceState.Offset,
): Pair<Int, Int> {
    return if (offset.isAbsolute) {
        offset.absoluteX to offset.absoluteY
    } else {
        signal.x + offset.x to signal.y - offset.y
    }
}

internal fun applyCopyCoordinatePolicy(
    signal: Signal.LED,
    rawX: Int,
    rawY: Int,
    wrapBounds: CopyCoordinateBounds?,
    isolateBounds: CopyCoordinateBounds?,
): Signal.LED? {
    val (boundedX, boundedY) = wrapBounds?.wrap(rawX, rawY) ?: (rawX to rawY)
    if (isolateBounds != null && !isolateBounds.contains(boundedX, boundedY)) {
        return null
    }

    return signal.copy(
        x = boundedX,
        y = boundedY,
        origin = signal.origin,
    )
}

internal fun buildInterpolatedCopyFrames(
    triggerSignals: List<Signal.LED>,
    offsets: List<CopyChainDeviceState.Offset>,
    reverse: Boolean,
    transformSignal: (Signal.LED, Int, Int) -> Signal.LED?,
): List<List<Signal.LED>> {
    if (triggerSignals.isEmpty() || offsets.isEmpty()) return emptyList()

    val targetsBySignal = triggerSignals.map { signal ->
        buildList {
            add(CopyInterpolationTarget(signal.x, signal.y))
            offsets.forEach { offset ->
                val (x, y) = resolveCopyTarget(signal, offset)
                add(CopyInterpolationTarget(x, y, offset.angle))
            }
        }.let { targets ->
            if (reverse) targets.asReversed() else targets
        }
    }

    return buildList {
        for (segmentIndex in 0 until offsets.size) {
            val pointsBySignal = targetsBySignal.map { targets ->
                val start = targets[segmentIndex]
                val end = targets[segmentIndex + 1]
                val angle = when {
                    end.angle == 0 -> 0
                    reverse -> -end.angle
                    else -> end.angle
                }

                if (angle != 0) {
                    calculateArcPoints(start.x, start.y, end.x, end.y, angle)
                } else {
                    bresenhamLine(start.x, start.y, end.x, end.y)
                }
            }

            val stepCount = pointsBySignal.maxOfOrNull { it.size } ?: 0
            for (stepIndex in 0 until stepCount) {
                if (segmentIndex > 0 && stepIndex == 0) continue

                add(
                    buildList {
                        triggerSignals.indices.forEach { signalIndex ->
                            val pointPath = pointsBySignal[signalIndex]
                            val point = pointPath[stepIndex.coerceAtMost(pointPath.lastIndex)]
                            transformSignal(triggerSignals[signalIndex], point.first, point.second)?.let(::add)
                        }
                    }
                )
            }
        }
    }
}

internal fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
    val points = mutableListOf<Pair<Int, Int>>()

    var x = x0
    var y = y0

    val dx = abs(x1 - x0)
    val dy = abs(y1 - y0)

    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1

    var err = dx - dy

    while (true) {
        points.add(x to y)
        if (x == x1 && y == y1) break

        val e2 = 2 * err
        if (e2 > -dy) {
            err -= dy
            x += sx
        }

        if (e2 < dx) {
            err += dx
            y += sy
        }
    }

    return points
}

internal fun calculateArcPoints(
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    angle: Int,
): List<Pair<Int, Int>> {
    val points = mutableListOf<Pair<Int, Int>>()
    val radAngle = angle.toDouble() * (kotlin.math.PI / 180.0)

    val dx = (x1 - x0).toDouble()
    val dy = (y1 - y0).toDouble()
    val dist = sqrt(dx * dx + dy * dy)
    if (dist < 0.001) return listOf(x0 to y0)

    val r = dist / (2.0 * sin(abs(radAngle) / 2.0))
    val h = r * cos(abs(radAngle) / 2.0)

    val midX = (x0 + x1) / 2.0
    val midY = (y0 + y1) / 2.0
    val vx = -(dy / dist)
    val vy = dx / dist

    val sign = if (angle > 0) 1.0 else -1.0
    val cx = midX + sign * h * vx
    val cy = midY + sign * h * vy
    val startAngle = atan2(y0.toDouble() - cy, x0.toDouble() - cx)
    val steps = max(abs(dx), abs(dy)).toInt() * 2

    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val currentAngle = startAngle + t * radAngle
        val px = (cx + r * cos(currentAngle)).roundToInt()
        val py = (cy + r * sin(currentAngle)).roundToInt()
        points.add(px to py)
    }

    return points.distinct()
}

private data class CopyInterpolationTarget(
    val x: Int,
    val y: Int,
    val angle: Int = 0,
)
