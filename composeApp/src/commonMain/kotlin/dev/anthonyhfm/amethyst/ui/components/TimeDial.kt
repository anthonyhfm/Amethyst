package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    onSelectTiming: (timing: Timing, msValue: Int) -> Unit
) {
    var millisecondMode: Boolean by remember { mutableStateOf(false) }
    val bpm by WorkspaceRepository.bpm.collectAsState()

    LaunchedEffect(bpm) {
        onSelectTiming(timing, timing.toMsValue(bpm))
    }

    if (millisecondMode) {
        TextDial(
            value = if (timing is Timing.Duration) timing.duration.inMs.toFloat() / 1000 else 0.3f,
            onValueChange = {
                onSelectTiming(
                    Timing.Duration((it * 1000).toInt().milliseconds),
                    (it * 1000).toInt().milliseconds.inMs.toInt()
                )
            },
            headline = headline,
            dialColor = MaterialTheme.colorScheme.secondary,
            text = "${timing.toMsValue(bpm)} ms",
            modifier = Modifier
                .rightClickable {
                    millisecondMode = !millisecondMode
                },
        )
    } else {
        StepTextDial(
            text = if (timing is Timing.Rythm) {
                timing.timing.text
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
            modifier = Modifier
                .rightClickable {
                    millisecondMode = !millisecondMode
                    println("Rightclick")
                },
        )
    }
}

fun Timing.toMsValue(bpm: Double): Int = when (this) {
    is Timing.Duration -> this.duration.inMs.toInt()

    is Timing.Rythm -> {
        val fraction: Float = timing.factor * 4
        val secondsPerQuarter = 60.0 / bpm

        (secondsPerQuarter * fraction * 1000).toInt()
    }
}