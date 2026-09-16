package dev.anthonyhfm.amethyst.core.engine.elements

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerBatch
import kotlinx.atomicfu.atomic

const val SIGNAL_EXTRA_SILENT_REPLAY = "amethyst.silentReplay"

sealed interface Signal {
    val origin: Any?
    val extras: Map<String, Int>
    val macroValues: List<Int>

    data class LED(
        override val origin: Any?,
        val x: Int,
        val y: Int,
        val color: Color,
        val layer: Int = 0,
        val blendingMode: BlendingMode = BlendingMode.Normal,
        val blendingRange: Int = 200,
        val opacity: Float = 1f,
        override val extras: Map<String, Int> = mapOf(),
        override val macroValues: List<Int> = currentSignalMacroValues(),
    ) : Signal {
        enum class BlendingMode {
            Normal, Multiply, Screen, Mask
        }
    }

    data class Midi(
        override val origin: Any?,
        val x: Int,
        val y: Int,
        val velocity: Int,
        override val extras: Map<String, Int> = mapOf(),
        val audioTriggerBatch: AudioTriggerBatch? = null,
        override val macroValues: List<Int> = currentSignalMacroValues(),
    ) : Signal {
        override fun equals(other: Any?): Boolean =
            this === other || other is Midi &&
                origin == other.origin &&
                x == other.x &&
                y == other.y &&
                velocity == other.velocity &&
                extras == other.extras &&
                macroValues == other.macroValues

        override fun hashCode(): Int {
            var result = origin?.hashCode() ?: 0
            result = 31 * result + x
            result = 31 * result + y
            result = 31 * result + velocity
            result = 31 * result + extras.hashCode()
            return 31 * result + macroValues.hashCode()
        }
    }

    data class AudioSignal(
        override val origin: Any?,
        val rawData: ByteArray? = null,
        val sampleRate: Int = 44100,
        val channels: Int = 2,
        val bitDepth: Int = 16,
        val durationMs: Long = 0,
        val gain: Float = 1f,
        val pan: Float = 0f,
        override val extras: Map<String, Int> = mapOf(),
        override val macroValues: List<Int> = currentSignalMacroValues(),
    ) : Signal {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioSignal) return false
            if (origin != other.origin) return false
            if (rawData != null) {
                if (other.rawData == null) return false
                if (!rawData.contentEquals(other.rawData)) return false
            } else if (other.rawData != null) return false
            if (sampleRate != other.sampleRate) return false
            if (channels != other.channels) return false
            if (bitDepth != other.bitDepth) return false
            if (durationMs != other.durationMs) return false
            if (gain != other.gain) return false
            if (pan != other.pan) return false
            return true
        }

        override fun hashCode(): Int {
            var result = origin?.hashCode() ?: 0
            result = 31 * result + (rawData?.contentHashCode() ?: 0)
            result = 31 * result + sampleRate
            result = 31 * result + channels
            result = 31 * result + bitDepth
            result = 31 * result + durationMs.hashCode()
            result = 31 * result + gain.hashCode()
            result = 31 * result + pan.hashCode()
            return result
        }
    }
}

private val signalMacroValues = atomic<List<Int>>(listOf(1))

internal fun updateSignalMacroValues(values: List<Int>) {
    signalMacroValues.value = values.toList()
}

internal fun currentSignalMacroValues(): List<Int> = signalMacroValues.value

internal fun Signal.refreshMacroValues(): Signal {
    val values = currentSignalMacroValues()
    return when (this) {
        is Signal.LED -> copy(macroValues = values)
        is Signal.Midi -> copy(macroValues = values)
        is Signal.AudioSignal -> copy(macroValues = values)
    }
}

fun Signal.isOn(): Boolean = when (this) {
    is Signal.LED -> color != Color.Black && opacity > 0f && (color.red > 0f || color.green > 0f || color.blue > 0f)
    is Signal.Midi -> velocity > 0
    else -> true
}

fun Signal.isSilentReplay(): Boolean = extras[SIGNAL_EXTRA_SILENT_REPLAY] == 1

fun List<Signal>.isSilentReplay(): Boolean =
    any { it.isSilentReplay() }
