package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable

@Composable
fun TimingControls(
    timing: Timing,
    onTimingChanged: (Timing) -> Unit,
    gate: Float,
    onGateChanged: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),

        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeDial(
            headline = "Duration",
            timing = timing,
            onSelectTiming = { timing, _ ->
                onTimingChanged(timing)
            }
        )

        TextDial(
            headline = "Gate",
            text = "${(gate * 200).toInt()}%",
            value = gate,
            onValueChange = { value ->
                onGateChanged(value)
            },
            modifier = Modifier
                .rightClickable {
                    onGateChanged(0.5f)
                },
        )
    }
}