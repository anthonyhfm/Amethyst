package dev.anthonyhfm.amethyst.core.heaven.elements

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.utils.SortedList
import kotlinx.atomicfu.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Screen : AutoCloseable {
    private class Pixel(
        private val index: Byte
    ) {
        private val signals = SortedList<Int, Signal>()
        private val currentColor = atomic(Color.Black)

        private val locker = Mutex()

        suspend fun clear() = locker.withLock {
            signals.clear()
            signals[10000] = Signal(null, x = index % 10, y = index / 10, color = Color.Black, layer = -100)
            currentColor.value = Color.Black
        }

        suspend fun getColor(): Color = locker.withLock {
            var ret = Color.Black

            for (i in 0 until signals.size) {
                val signal = signals.getValueAt(i)

                if (signal.blendingMode != BlendingMode.Normal &&
                    (i == signals.size - 1 ||
                     signal.layer - signals.getValueAt(i + 1).layer > signal.blendingRange)) {
                    continue
                }

                if (signal.blendingMode == BlendingMode.Mask) break

                val multiply = i > 0 &&
                              signals.getValueAt(i - 1).blendingMode == BlendingMode.Multiply &&
                              signals.getValueAt(i - 1).layer - signal.layer <= signals.getValueAt(i - 1).blendingRange

                ret = ret.mix(signal.color, multiply)

                if (signal.blendingMode == BlendingMode.Normal) break
            }

            currentColor.value = ret
            ret
        }

        suspend fun midiEnter(n: Signal) = locker.withLock {
            if (n.y * 10 + n.x != index.toInt()) return@withLock

            val layer = -n.layer

            if (n.color.isLit()) {
                signals[layer] = n.copy()
            } else if (signals.containsKey(layer)) {
                signals.remove(layer)
            }
        }
    }

    var screenExit: ((List<RawUpdate>, Array<Color>) -> Unit)? = null

    private val screen = Array(101) { Pixel(it.toByte()) }
    private val snapshot = Array(101) { Color.Black }

    suspend fun clear() {
        screen.forEach { it.clear() }
        snapshot()
    }

    private suspend fun snapshot() {
        val updates = mutableListOf<RawUpdate>()

        for (i in screen.indices) {
            val newColor = screen[i].getColor()

            if (snapshot[i] != newColor) {
                updates.add(RawUpdate(i, newColor.copy()))
                snapshot[i] = newColor
            }
        }

        if (updates.isNotEmpty()) {
            screenExit?.invoke(updates, snapshot)
        }
    }

    companion object {
        // Use atomic list for thread-safe drawing handlers
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

    suspend fun midiEnter(n: Signal) {
        screen[n.x + n.y * 10].midiEnter(n)
    }

    override fun close() {
        removeDrawingHandler(snapshotHandler)
    }
}

// Extension functions for Color operations (similar to C# Color.Mix)
fun Color.mix(other: Color, multiply: Boolean = false): Color {
    return if (multiply) {
        Color(
            (red * other.red),
            (green * other.green),
            (blue * other.blue),
            alpha
        )
    } else {
        Color(
            (red + other.red).coerceIn(0f, 1f),
            (green + other.green).coerceIn(0f, 1f),
            (blue + other.blue).coerceIn(0f, 1f),
            alpha
        )
    }
}

fun Color.isLit(): Boolean = red > 0f || green > 0f || blue > 0f

// Assuming these enums exist
enum class BlendingType {
    Normal, Multiply, Mask
}

// Extension for Signal class to add blending properties
val Signal.blendingMode: BlendingType get() = BlendingType.Normal // Default implementation
val Signal.blendingRange: Int get() = 1 // Default implementation
