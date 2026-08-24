package dev.anthonyhfm.amethyst.core.engine.heaven

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.utils.SortedList
import kotlin.math.abs
import kotlinx.atomicfu.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Screen : AutoCloseable {
    private class Pixel(
        private val index: Byte
    ) {
        private val signals = SortedList<Int, Signal.LED>()
        private val currentColor = atomic(Color.Black)
        private val locker = Mutex()
        private val isDirty = atomic(false)

        init {
            signals[10000] = Signal.LED(null, x = index % 10, y = index / 10, color = Color.Black, layer = -100)
        }

        suspend fun clear() = locker.withLock {
            signals.clear()
            signals[10000] = Signal.LED(null, x = index % 10, y = index / 10, color = Color.Black, layer = -100)
            recomputeColorLocked()
            isDirty.value = true
        }

        private fun recomputeColorLocked(): Color {
            var ret = Color.Black

            for (i in 0 until signals.size) {
                val signal = signals.getValueAt(i)
                val opacity = signal.opacity.coerceIn(0f, 1f)
                val sigColor = if (opacity < 1f) {
                    Color(
                        (signal.color.red * opacity).coerceIn(0f, 1f),
                        (signal.color.green * opacity).coerceIn(0f, 1f),
                        (signal.color.blue * opacity).coerceIn(0f, 1f),
                        1f
                    )
                } else {
                    signal.color
                }

                if (signal.blendingMode != Signal.LED.BlendingMode.Normal &&
                    (if (i == signals.size - 1) true else signal.layer - signals.getValueAt(i + 1).layer > signal.blendingRange)
                ) {
                    continue
                }

                if (signal.blendingMode == Signal.LED.BlendingMode.Mask) {
                    break
                }

                val multiply = if (i == 0) false else (
                    signals.getValueAt(i - 1).blendingMode == Signal.LED.BlendingMode.Multiply &&
                    signals.getValueAt(i - 1).layer - signal.layer <= signals.getValueAt(i - 1).blendingRange
                )

                ret = if (multiply) {
                    Color(
                        (ret.red * sigColor.red).coerceIn(0f, 1f),
                        (ret.green * sigColor.green).coerceIn(0f, 1f),
                        (ret.blue * sigColor.blue).coerceIn(0f, 1f),
                        1f
                    )
                } else {
                    Color(
                        (1f - (1f - ret.red) * (1f - sigColor.red)).coerceIn(0f, 1f),
                        (1f - (1f - ret.green) * (1f - sigColor.green)).coerceIn(0f, 1f),
                        (1f - (1f - ret.blue) * (1f - sigColor.blue)).coerceIn(0f, 1f),
                        1f
                    )
                }

                if (signal.blendingMode == Signal.LED.BlendingMode.Normal) {
                    break
                }
            }

            currentColor.value = ret
            return ret
        }

        fun getCachedColor(): Color = currentColor.value

        suspend fun midiEnter(n: Signal.LED) = locker.withLock {
            if (n.y * 10 + n.x != index.toInt()) return@withLock

            val layer = -n.layer

            if (n.color.isLit()) {
                signals[layer] = n.copy()
            } else if (signals.containsKey(layer)) {
                signals.remove(layer)
            }

            recomputeColorLocked()
            isDirty.value = true
        }

        fun consumeDirtyFlag(): Boolean {
            return if (isDirty.value) {
                isDirty.value = false
                true
            } else false
        }
    }

    var screenExit: ((List<RawLEDUpdate>, Array<Color>) -> Unit)? = null

    private val screen = Array(101) { Pixel(it.toByte()) }
    private val snapshot = Array(101) { Color.Black }

    private val dirtyMutex = Mutex()
    private val dirtyIndices = mutableSetOf<Int>()

    suspend fun clear() {
        screen.forEach { it.clear() }
        dirtyMutex.withLock {
            dirtyIndices.clear()
            for (i in screen.indices) dirtyIndices.add(i)
        }
        snapshot()
    }

    private suspend fun snapshot() {
        val updates = mutableListOf<RawLEDUpdate>()

        val localDirty = dirtyMutex.withLock {
            val copy = dirtyIndices.toList()
            dirtyIndices.clear()
            copy
        }

        if (localDirty.isEmpty()) {
            return
        }

        for (i in localDirty) {
            val newColor = screen[i].getCachedColor()

            if (snapshot[i] != newColor) {
                updates.add(RawLEDUpdate(i, newColor))
                snapshot[i] = newColor
            }
        }

        if (updates.isNotEmpty()) {
            screenExit?.invoke(updates, snapshot)
        }
    }

    companion object {
        private val drawingHandlers = atomic(listOf<suspend () -> Unit>())

        suspend fun draw() {
            drawingHandlers.value.forEach { it.invoke() }
        }

        internal fun addDrawingHandler(handler: suspend () -> Unit) {
            while (true) {
                val current = drawingHandlers.value
                val new = current + handler
                if (drawingHandlers.compareAndSet(current, new)) break
            }
        }

        internal fun removeDrawingHandler(handler: suspend () -> Unit) {
            while (true) {
                val current = drawingHandlers.value
                val new = current - handler
                if (drawingHandlers.compareAndSet(current, new)) break
            }
        }
    }

    private val snapshotHandler: suspend () -> Unit = { snapshot() }

    init {
        addDrawingHandler(snapshotHandler)
    }

    fun getColor(index: Int): Color = snapshot[index]

    suspend fun midiEnter(n: Signal.LED) {
        val idx = n.x + n.y * 10
        screen[idx].midiEnter(n)

        dirtyMutex.withLock { dirtyIndices.add(idx) }
    }

    override fun close() {
        removeDrawingHandler(snapshotHandler)
    }
}

fun Color.mix(other: Color, mode: Signal.LED.BlendingMode = Signal.LED.BlendingMode.Normal): Color {
    return when (mode) {
        Signal.LED.BlendingMode.Multiply -> {
            Color(
                (red * other.red),
                (green * other.green),
                (blue * other.blue),
                alpha
            )
        }
        Signal.LED.BlendingMode.Screen -> {
            // Screen formula: 1 - (1 - a) * (1 - b)
            Color(
                1f - (1f - red) * (1f - other.red),
                1f - (1f - green) * (1f - other.green),
                1f - (1f - blue) * (1f - other.blue),
                alpha
            )
        }
        else -> {
            Color(
                (red + other.red).coerceIn(0f, 1f),
                (green + other.green).coerceIn(0f, 1f),
                (blue + other.blue).coerceIn(0f, 1f),
                alpha
            )
        }
    }
}

fun Color.isLit(): Boolean = red > 0f || green > 0f || blue > 0f
