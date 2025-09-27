package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.koin.compose.koinInject
import org.koin.core.time.inMs
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TimeDial(
    headline: String = "Duration",
    timing: Timing,
    onSelectTiming: (timing: Timing, msValue: Long) -> Unit,
    enabled: Boolean = true,
    text: String? = null,
) {
    val millisecondMode by remember { derivedStateOf { timing is Timing.Duration } }
    val bpm by WorkspaceRepository.bpm.collectAsState()

    LaunchedEffect(bpm) {
        print("TimeDial: BPM changed to $bpm, current timing is $timing which is ${timing.toMsValue(bpm)} ms")
        onSelectTiming(timing, timing.toMsValue(bpm))
    }

    if (millisecondMode) {
        TextDial(
            value = (timing as Timing.Duration).duration.inMs.toFloat() / 1000,
            onValueChange = {
                onSelectTiming(
                    Timing.Duration((it * 1000).toInt().milliseconds),
                    (it * 1000).toInt().milliseconds.inWholeMilliseconds
                )
            },
            headline = headline,
            dialColor = MaterialTheme.colorScheme.secondary,
            text = text ?: "${timing.duration.inMs.toInt()} ms",
            onResolveTextValue = {
                val timing = it.asTiming()

                timing?.let { timing ->
                    if (timing.toMsValue(bpm) <= 1000) {
                        onSelectTiming(
                            timing,
                            timing.toMsValue(bpm)
                        )
                    }
                }
            },
            enabled = enabled
        )
    } else {
        StepTextDial(
            text = if (timing is Timing.Rythm) {
                text ?: timing.timing.text
            } else {
                ""
            },
            steps = Timing.Rythm.RythmTiming.entries,
            value = (timing as? Timing.Rythm)?.timing ?: Timing.Rythm.RythmTiming._1_128,
            headline = headline,
            onValueChange = {
                onSelectTiming(
                    Timing.Rythm(it),
                    Timing.Rythm(it).toMsValue(bpm)
                )
            },
            onResolveTextValue = {
                val timing = it.asTiming()

                timing?.let { timing ->
                    onSelectTiming(
                        timing,
                        timing.toMsValue(bpm)
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

            if (value != null) {
                Timing.Duration(value.milliseconds)
            } else {
                null
            }
        }

        "/" in this -> {
            val parts = trimmed.split("/").map { it.trim() }
            if (parts.size == 2) {
                val numerator = parts[0]
                val denominator = parts[1]

                Timing.Rythm.RythmTiming.entries.find {
                    val textParts = it.text.split("/").map { it.trim() }

                    textParts[0] == numerator && textParts[1] == denominator
                }?.let {
                    Timing.Rythm(it)
                }
            } else {
                null
            }
        }

        else -> {
            null
        }
    }
}