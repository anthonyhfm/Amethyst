package dev.anthonyhfm.amethyst.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable

@Composable
fun TimeDial(
    title: String? = stringResource(Res.string.ui_timedial_default_headline),
    timing: Timing,
    onSelectTiming: (timing: Timing, msValue: Long) -> Unit,
    onStartValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    onFinishValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    text: String? = null,
    flat: Boolean = false,
) {
    val millisecondMode = timing is Timing.Duration
    val bpm by WorkspaceRepository.bpm.collectAsState()

    var lastRythmTiming by remember {
        mutableStateOf(
            (timing as? Timing.Rythm)?.timing ?: Timing.Rythm.RythmTiming._1_4
        )
    }
    val currentRythmTiming = (timing as? Timing.Rythm)?.timing
    if (currentRythmTiming != null && currentRythmTiming != lastRythmTiming) {
        lastRythmTiming = currentRythmTiming
    }

    val toggleTimingModeModifier = if (enabled) {
        Modifier.rightClickable {
            if (timing is Timing.Rythm) {
                val msVal = timing.toMsValue(bpm)
                val durationTiming = Timing.Duration(msVal.milliseconds)
                onSelectTiming(durationTiming, msVal)
            } else {
                val rythmTiming = Timing.Rythm(lastRythmTiming)
                onSelectTiming(rythmTiming, rythmTiming.toMsValue(bpm))
            }
        }
    } else {
        Modifier
    }

    Box(modifier = toggleTimingModeModifier) {
        if (millisecondMode) {
            if (flat) {
                FlatDial(
                    type = DialType.Continuous,
                    value = (timing as Timing.Duration).duration.inWholeMilliseconds.toFloat() / 1000,
                    onStartValueChange = {
                        onStartValueChange(timing, (it * 1000).roundToInt().milliseconds.inWholeMilliseconds)
                    },
                    onValueChange = {
                        onSelectTiming(
                            Timing.Duration((it * 1000).roundToInt().milliseconds),
                            (it * 1000).roundToInt().milliseconds.inWholeMilliseconds
                        )
                    },
                    onFinishValueChange = {
                        onFinishValueChange(timing, (it * 1000).roundToInt().milliseconds.inWholeMilliseconds)
                    },
                    title = title,
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
                    enabled = enabled,
                    defaultValue = 0.5f,
                    isAutomatable = false,
                )
            } else {
                Dial(
                    type = DialType.Continuous,
                    value = (timing as Timing.Duration).duration.inWholeMilliseconds.toFloat() / 1000,
                    onStartValueChange = {
                        onStartValueChange(timing, (it * 1000).roundToInt().milliseconds.inWholeMilliseconds)
                    },
                    onValueChange = {
                        onSelectTiming(
                            Timing.Duration((it * 1000).roundToInt().milliseconds),
                            (it * 1000).roundToInt().milliseconds.inWholeMilliseconds
                        )
                    },
                    onFinishValueChange = {
                        onFinishValueChange(timing, (it * 1000).roundToInt().milliseconds.inWholeMilliseconds)
                    },
                    title = title,
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
                    enabled = enabled,
                    defaultValue = 0.5f,
                    isAutomatable = false,
                )
            }
        } else {
            if (flat) {
                FlatDial(
                    type = DialType.Steps(Timing.Rythm.RythmTiming.entries),
                    text = if (timing is Timing.Rythm) {
                        text ?: timing.timing.text
                    } else {
                        lastRythmTiming.text
                    },
                    value = currentRythmTiming ?: lastRythmTiming,
                    title = title,
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
                    enabled = enabled,
                    defaultValue = Timing.Rythm.RythmTiming._1_4,
                    isAutomatable = false,
                )
            } else {
                Dial(
                    type = DialType.Steps(Timing.Rythm.RythmTiming.entries),
                    text = if (timing is Timing.Rythm) {
                        text ?: timing.timing.text
                    } else {
                        lastRythmTiming.text
                    },
                    value = currentRythmTiming ?: lastRythmTiming,
                    title = title,
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
                    enabled = enabled,
                    defaultValue = Timing.Rythm.RythmTiming._1_4,
                    isAutomatable = false,
                )
            }
        }
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

        else -> trimmed.toIntOrNull()?.let { Timing.Duration(it.milliseconds) }
    }
}
