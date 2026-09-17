package dev.anthonyhfm.amethyst.ui.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.util.mainDispatcherOrDefault
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val GAMMA_LUT = FloatArray(256) { i ->
    (i / 255f).pow(0.3f)
}

fun Color.applyLaunchpadGamma(): Color {
    if (this == Color.Black) return Color.Black
    val r = GAMMA_LUT[(red * 255f).roundToInt().coerceIn(0, 255)]
    val g = GAMMA_LUT[(green * 255f).roundToInt().coerceIn(0, 255)]
    val b = GAMMA_LUT[(blue * 255f).roundToInt().coerceIn(0, 255)]
    return Color(red = r, green = g, blue = b, alpha = alpha)
}

class LaunchpadPreviewState : AutoCloseable { // TODO: Replace with Heaven's screen-class
    private val scope = CoroutineScope(
        mainDispatcherOrDefault("LaunchpadPreviewState") + SupervisorJob()
    )
    private val lock = SynchronizedObject()
    private val updatePending = atomic(false)

    private val internalGrid = Array(100) { RawLEDUpdate(it.toByte(), Color.Black) }

    val grid: MutableState<List<RawLEDUpdate>> = mutableStateOf(
        List(100) {
            RawLEDUpdate(
                index = it,
                color = Color.Black
            )
        }
    )

    fun sendToPreview(updates: List<RawLEDUpdate>) {
        synchronized(lock) {
            for (u in updates) {
                val idx = u.index.toInt()
                if (idx in 0 until 100) {
                    internalGrid[idx].color = u.color
                }
            }
        }
        scheduleFlush()
    }

    fun clear() {
        synchronized(lock) {
            for (i in 0 until 100) {
                internalGrid[i].color = Color.Black
            }
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (updatePending.compareAndSet(expect = false, update = true)) {
            scope.launch {
                flush()
            }
        }
    }

    private fun flush() {
        val snapshot = synchronized(lock) {
            updatePending.value = false
            List(100) { i ->
                RawLEDUpdate(i.toByte(), internalGrid[i].color)
            }
        }
        grid.value = snapshot
    }

    override fun close() {
        scope.cancel()
    }
}

@Composable
fun rememberLaunchpadPreviewState(): LaunchpadPreviewState {
    val state = remember {
        LaunchpadPreviewState()
    }
    DisposableEffect(state) {
        onDispose {
            state.close()
        }
    }
    return state
}