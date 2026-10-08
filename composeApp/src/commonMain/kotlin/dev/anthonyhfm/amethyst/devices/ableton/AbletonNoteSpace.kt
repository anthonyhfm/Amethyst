package dev.anthonyhfm.amethyst.devices.ableton

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.midi.data.XY_TO_DRUM_RACK

internal object AbletonNoteSpace {
    const val PITCH = "ableton.pitch"
    private const val TARGET_X = "ableton.targetX"
    private const val TARGET_Y = "ableton.targetY"

    data class Note(val pitch: Int, val targetX: Int, val targetY: Int)

    fun note(signal: Signal): Note? {
        val storedPitch = signal.extras[PITCH]
        if (storedPitch != null) {
            val targetX = signal.extras[TARGET_X] ?: return null
            val targetY = signal.extras[TARGET_Y] ?: return null
            return Note(storedPitch, targetX, targetY)
        }

        val x = when (signal) {
            is Signal.LED -> signal.x
            is Signal.Midi -> signal.x
            is Signal.AudioSignal -> return null
        }
        val y = when (signal) {
            is Signal.LED -> signal.y
            is Signal.Midi -> signal.y
            is Signal.AudioSignal -> return null
        }

        val target = Heaven.devices.firstOrNull { device ->
            val position = device.position.value
            x in position.x.toInt() until position.x.toInt() + device.layout.cols &&
                y in position.y.toInt() until position.y.toInt() + device.layout.rows
        } ?: return null

        val targetX = target.position.value.x.toInt() + target.layout.mainOffsetX - 1
        val targetY = target.position.value.y.toInt() + target.layout.mainOffsetY - 1
        val localX = x - targetX
        val localY = y - targetY
        val localIndex = localX + (9 - localY) * 10
        val pitch = XY_TO_DRUM_RACK.getOrNull(localIndex) ?: return null
        if (padIndex(pitch) != localIndex) {
            return null
        }
        return Note(pitch, targetX, targetY)
    }

    fun withPitch(
        signal: Signal,
        note: Note,
        pitch: Int,
        extrasCache: MutableMap<Note, Map<String, Int>>? = null,
    ): Signal? {
        if (pitch !in 0..127) {
            return null
        }

        val pitchNote = if (note.pitch == pitch) {
            note
        } else {
            note.copy(pitch = pitch)
        }
        val pitchExtras = extrasCache?.getOrPut(pitchNote) {
            mapOf(
                PITCH to pitch,
                TARGET_X to note.targetX,
                TARGET_Y to note.targetY,
            )
        } ?: mapOf(
            PITCH to pitch,
            TARGET_X to note.targetX,
            TARGET_Y to note.targetY,
        )
        val extras = if (signal.extras.isEmpty()) {
            pitchExtras
        } else {
            signal.extras + pitchExtras
        }
        val index = padIndex(pitch)
        val x = index?.rem(10)?.plus(note.targetX)
        val y = index?.div(10)?.let { 9 - it + note.targetY }

        return when (signal) {
            is Signal.LED -> signal.copy(
                x = x ?: signal.x,
                y = y ?: signal.y,
                extras = extras,
            )
            is Signal.Midi -> signal.copy(
                x = x ?: signal.x,
                y = y ?: signal.y,
                extras = extras,
            )
            is Signal.AudioSignal -> signal
        }
    }

    fun project(signal: Signal.LED): Signal.LED? {
        val pitch = signal.extras[PITCH] ?: return signal
        if (signal.extras[TARGET_X] == null || signal.extras[TARGET_Y] == null) {
            return null
        }
        return if (padIndex(pitch) == null) null else signal
    }

    fun padIndex(pitch: Int): Int? {
        val index = DRUM_RACK_TO_XY.getOrNull(pitch) ?: return null
        if (index == 0 || XY_TO_DRUM_RACK.getOrNull(index) != pitch) {
            return null
        }
        return index
    }
}
