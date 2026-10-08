package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal class PianoRollPadPreview(
    private val layer: Int = Int.MAX_VALUE,
    private val nowMs: () -> Double = { Heaven.time },
    private val frameIntervalMs: () -> Double = { 1000.0 / Heaven.fps.coerceAtLeast(1) },
    private val schedule: (Double, Any, () -> Unit) -> Unit = { delay, owner, frame ->
        Heaven.schedule(delayInMs = delay, owner = owner, job = frame)
    },
    private val cancel: (Any) -> Unit = { Heaven.cancelJobsForOwner(owner = it) },
    private val send: (Signal.LED) -> Unit = { Heaven.midiEnter(signals = listOf(it)) },
) {
    private class HeldPad(
        val signal: Signal.LED,
        val gradient: List<NoteGradientStop>?,
        val durationMs: Long,
        val startedAtMs: Double,
        val repeat: Boolean,
    )

    private val lock = SynchronizedObject()
    private val heldPads = mutableMapOf<Pair<Int, Int>, HeldPad>()

    fun press(
        key: Pair<Int, Int>,
        signal: Signal.LED,
        gradient: List<NoteGradientStop>?,
        durationMs: Long,
        repeat: Boolean = true,
    ) = synchronized(lock) {
        release(key = key)
        val pad = HeldPad(
            signal = signal.copy(layer = layer),
            gradient = gradient?.takeIf { it.size >= 2 }?.let(GradientInterpolator::normalize),
            durationMs = durationMs.coerceAtLeast(1L),
            startedAtMs = nowMs(),
            repeat = repeat,
        )
        heldPads[key] = pad
        render(key = key, pad = pad)
    }

    fun showSnapshot(key: Pair<Int, Int>, signal: Signal.LED) {
        press(
            key = key,
            signal = signal,
            gradient = null,
            durationMs = 1L,
            repeat = true,
        )
    }

    private fun render(key: Pair<Int, Int>, pad: HeldPad): Unit = synchronized(lock) {
        if (heldPads[key] !== pad) {
            return@synchronized
        }
        val elapsedMs = (nowMs() - pad.startedAtMs).coerceAtLeast(0.0)
        if (!pad.repeat && elapsedMs >= pad.durationMs) {
            release(key = key)
            return@synchronized
        }
        val color = pad.gradient?.let { gradient ->
            val fraction = (elapsedMs % pad.durationMs / pad.durationMs).toFloat()
            val (red, green, blue) = GradientInterpolator.interpolate(gradient, fraction)
            Color(red = red, green = green, blue = blue)
        } ?: pad.signal.color
        send(pad.signal.copy(color = color))
        if (pad.gradient != null) {
            val nextFrameMs = if (pad.repeat) {
                frameIntervalMs()
            } else {
                minOf(frameIntervalMs(), pad.durationMs - elapsedMs)
            }
            schedule(nextFrameMs, pad) { render(key = key, pad = pad) }
        } else if (!pad.repeat) {
            schedule(pad.durationMs - elapsedMs, pad) { render(key = key, pad = pad) }
        }
    }

    fun release(key: Pair<Int, Int>) = synchronized(lock) {
        val pad = heldPads.remove(key) ?: return@synchronized
        cancel(pad)
        send(pad.signal.copy(color = Color.Black))
    }

    fun clear() = synchronized(lock) {
        heldPads.keys.toList().forEach { release(key = it) }
    }
}
