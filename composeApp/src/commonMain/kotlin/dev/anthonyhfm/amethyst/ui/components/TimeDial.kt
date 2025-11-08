package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TimeDial(
    headline: String = "Duration",
    timing: Timing,
    onSelectTiming: (timing: Timing, msValue: Long) -> Unit,
    onStartValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    onFinishValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    text: String? = null,
) {
    val millisecondMode by remember { derivedStateOf { timing is Timing.Duration } }
    val bpm by WorkspaceRepository.bpm.collectAsState()

    if (millisecondMode) {
        TextDial(
            value = (timing as Timing.Duration).duration.inWholeMilliseconds.toFloat() / 1000,
            onStartValueChange = {
                onStartValueChange(timing, (it * 1000).toInt().milliseconds.inWholeMilliseconds)
            },
            onValueChange = {
                onSelectTiming(
                    Timing.Duration((it * 1000).toInt().milliseconds),
                    (it * 1000).toInt().milliseconds.inWholeMilliseconds
                )
            },
            onFinishValueChange = {
                onFinishValueChange(timing, (it * 1000).toInt().milliseconds.inWholeMilliseconds)
            },
            headline = headline,
            dialColor = MaterialTheme.colorScheme.secondary,
            text = text ?: "${timing.duration.inWholeMilliseconds.toInt()} ms",
            onResolveTextValue = {
                val timing = it.asTiming()

                timing?.let { t ->
                    if (t.toMsValue(bpm) <= 1000) {
                        onSelectTiming(
                            t,
                            t.toMsValue(bpm)
                        )
                    }
                }
            },
            enabled = enabled
        )
    } else {
        // Merke letzten gültigen RythmTiming damit wir bei kurzzeitigem Wechsel (Undo) nicht auf Default fallen
        var lastRythmTiming by remember { mutableStateOf((timing as? Timing.Rythm)?.timing ?: Timing.Rythm.RythmTiming._1_4) }
        val currentRythmTiming = (timing as? Timing.Rythm)?.timing
        if (currentRythmTiming != null && currentRythmTiming != lastRythmTiming) {
            lastRythmTiming = currentRythmTiming
        }

        StepTextDial(
            text = if (timing is Timing.Rythm) {
                text ?: timing.timing.text
            } else {
                lastRythmTiming.text
            },
            steps = Timing.Rythm.RythmTiming.entries,
            value = currentRythmTiming ?: lastRythmTiming,
            headline = headline,
            onStartValueChange = {
                val startTiming = Timing.Rythm(it)
                onStartValueChange(startTiming, startTiming.toMsValue(bpm))
            },
            onValueChange = {
                val newTiming = Timing.Rythm(it)
                onSelectTiming(
                    newTiming,
                    newTiming.toMsValue(bpm)
                )
            },
            onFinishValueChange = {
                val finishTiming = Timing.Rythm(it)
                onFinishValueChange(finishTiming, finishTiming.toMsValue(bpm))
            },
            onResolveTextValue = {
                val parsed = it.asTiming()

                parsed?.let { t ->
                    onSelectTiming(
                        t,
                        t.toMsValue(bpm)
                    )
                }
            },
            enabled = enabled
        )
    }
}

fun Timing.toMsValue(bpm: Double): Long = when (this) {
    is Timing.Duration -> this.duration.inWholeMilliseconds
    is Timing.Rythm -> {
        val beats = timing.factor * 4.0
        val msPerBeat = 60_000.0 / bpm
        kotlin.math.round(beats * msPerBeat).toLong()
    }
}

fun String.asTiming(): Timing? {
    val trimmed = this.trim().lowercase()

    return when {
        trimmed.endsWith("ms") -> {
            val value = trimmed.removeSuffix("ms").trim().toIntOrNull()
            value?.let { Timing.Duration(it.milliseconds) }
        }
        "/" in trimmed -> {
            val parts = trimmed.split("/").map { it.trim() }
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull()
                val denom = parts[1].toIntOrNull()
                if (num != null && denom != null) {
                    Timing.Rythm.RythmTiming.entries.find { it.text == "$num/$denom" }?.let { Timing.Rythm(it) }
                } else null
            } else null
        }
        else -> null
    }
}
