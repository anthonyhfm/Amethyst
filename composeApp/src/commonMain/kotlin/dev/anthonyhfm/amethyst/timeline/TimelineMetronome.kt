package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

object TimelineMetronome {
    private object AudioOrigin

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val clock = TimelineMetronomeClock()
    private var clickSampleRate = 0
    private var regularClick: Signal.AudioSignal? = null
    private var accentedClick: Signal.AudioSignal? = null

    fun setEnabled(enabled: Boolean) {
        if (_enabled.value == enabled) {
            return
        }
        _enabled.value = enabled
        if (enabled && TimelineRepository.isPlaying.value) {
            start(
                positionMs = TimelineRepository.playheadPositionMs.value,
                bpm = WorkspaceRepository.bpm.value,
            )
        } else {
            stop()
        }
    }

    fun start(positionMs: Long, bpm: Double) {
        stop()
        if (!_enabled.value) {
            return
        }
        Echo.initialize()
        clock.start(positionMs = positionMs, bpm = bpm)?.let { beat ->
            playClick(beat = beat)
        }
    }

    fun tick(positionMs: Long, bpm: Double) {
        if (!_enabled.value) {
            return
        }
        clock.tick(positionMs = positionMs, bpm = bpm)?.let { beat ->
            playClick(beat = beat)
        }
    }

    fun tickDelay(positionMs: Long, bpm: Double): Long {
        if (!_enabled.value) {
            return 8L
        }
        return clock.tickDelay(positionMs = positionMs, bpm = bpm)
    }

    fun stop() {
        clock.stop()
        Echo.stopByOrigin(origin = AudioOrigin)
    }

    private fun playClick(beat: Long) {
        val sampleRate = Echo.outputStatus.value.sampleRate.takeIf { it > 0 } ?: 44_100
        if (clickSampleRate != sampleRate) {
            clickSampleRate = sampleRate
            regularClick = createClick(sampleRate = sampleRate, accented = false)
            accentedClick = createClick(sampleRate = sampleRate, accented = true)
        }
        val click = if (beat % 4L == 0L) accentedClick else regularClick
        if (click != null) {
            Echo.play(audioSignal = click)
        }
    }

    private fun createClick(sampleRate: Int, accented: Boolean): Signal.AudioSignal {
        return Signal.AudioSignal(
            origin = AudioOrigin,
            rawData = timelineMetronomePcm(sampleRate = sampleRate, accented = accented),
            sampleRate = sampleRate,
            channels = 1,
            bitDepth = 16,
            durationMs = 35L,
        )
    }
}

internal class TimelineMetronomeClock {
    private var nextBeat: Long? = null
    private var tempo = 120.0
    private var lastPositionMs = 0L

    fun start(positionMs: Long, bpm: Double): Long? {
        tempo = normalizedTempo(bpm = bpm)
        lastPositionMs = positionMs.coerceAtLeast(0L)
        val beatPosition = lastPositionMs / beatDurationMs()
        val firstBeat = ceil(beatPosition).toLong()
        nextBeat = firstBeat
        return tick(positionMs = lastPositionMs, bpm = tempo)
    }

    fun tick(positionMs: Long, bpm: Double): Long? {
        if (nextBeat == null) {
            return null
        }
        val position = positionMs.coerceAtLeast(0L)
        val updatedTempo = normalizedTempo(bpm = bpm)
        if (updatedTempo != tempo) {
            tempo = updatedTempo
            nextBeat = floor(position / beatDurationMs()).toLong() + 1L
            lastPositionMs = position
            return null
        }
        if (position < lastPositionMs) {
            return start(positionMs = position, bpm = updatedTempo)
        }
        lastPositionMs = position
        val dueBeat = floor(position / beatDurationMs()).toLong()
        if (dueBeat < requireNotNull(nextBeat)) {
            return null
        }
        nextBeat = dueBeat + 1L
        if (position - dueBeat * beatDurationMs() > 35.0) {
            return null
        }
        return dueBeat
    }

    fun tickDelay(positionMs: Long, bpm: Double): Long {
        val beat = nextBeat ?: return 8L
        if (normalizedTempo(bpm = bpm) != tempo) {
            return 1L
        }
        return ceil(beat * beatDurationMs() - positionMs).toLong().coerceIn(1L, 8L)
    }

    fun stop() {
        nextBeat = null
    }

    private fun beatDurationMs(): Double = 60_000.0 / tempo

    private fun normalizedTempo(bpm: Double): Double {
        return if (bpm.isFinite() && bpm > 0.0) bpm.coerceIn(1.0, 1_000.0) else 120.0
    }
}

internal fun timelineMetronomePcm(sampleRate: Int, accented: Boolean): ByteArray {
    require(sampleRate > 0)
    val frameCount = (sampleRate * 0.035).roundToInt().coerceAtLeast(2)
    val data = ByteArray(size = frameCount * 2)
    val frequency = if (accented) 2_200.0 else 1_500.0
    for (frame in 0 until frameCount) {
        val progress = frame.toDouble() / (frameCount - 1)
        val attack = (frame.toDouble() / (sampleRate * 0.0008)).coerceAtMost(1.0)
        val envelope = attack * exp(-8.0 * progress) * (1.0 - progress)
        val sample = (sin(2.0 * PI * frequency * frame / sampleRate) * envelope * 12_000.0).roundToInt()
        data[frame * 2] = sample.toByte()
        data[frame * 2 + 1] = (sample shr 8).toByte()
    }
    return data
}
