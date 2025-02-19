package dev.anthonyhfm.amethyst.ui.launchpad.previews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState

@Composable
fun LaunchpadPro(
    modifier: Modifier = Modifier
) {
    val state = rememberPreviewState().grid.value

    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(6))
            .clip(RoundedCornerShape(6))
            .aspectRatio(1f / 1f)
            .background(Color(0xFF0d0d0d))
    ) {
        GenericLaunchpadButton(
            effect = state[9][9],
            enableLightSpot = false,
            sizeModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.02f)
                .aspectRatio(1 / 1f)
        )

        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            for (y in 9 downTo 0) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(0.5f))

                    for (x in 0..9) {
                        GridPad(
                            x = x,
                            y = y,
                            effectData = state[x][y],
                            onClick = null
                        )

                        if (x != 9) {
                            Spacer(modifier = Modifier.weight(0.1f))
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.5f))
                }

                Spacer(modifier = Modifier.weight(0.1f))
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.GridPad(
    x: Int,
    y: Int,
    effectData: MidiEffectData,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            ),

        contentAlignment = Alignment.Center
    ) {
        if ((y == 0 || y == 9) && x > 0 && x < 9) {
            CircularPad(
                effectData = effectData
            )
        } else if ((x == 0 || x == 9) && y > 0 && y < 9) {
            CircularPad(
                effectData = effectData
            )
        } else if (x in 4..5 && y in 4..5) {
            ClippedPad(
                topLeft = x == 4 && y == 5,
                topRight = x == 5 && y == 5,
                bottomLeft = x == 4 && y == 4,
                bottomRight = x == 5 && y == 4,
                effectData = effectData
            )
        } else if (x in 1..8 && y in 1..8) {
            GenericLaunchpadButton(
                sizeModifier = Modifier
                    .fillMaxSize(0.9f),
                effect = effectData
            )
        } else if (x == 0 && y == 9) {
            SetupButton()
        }
    }
}

@Composable
private fun CircularPad(effectData: MidiEffectData) {
    Box(
        modifier = Modifier
            .fillMaxSize(0.8f),

        contentAlignment = Alignment.Center
    ) {
        GenericLaunchpadButton(
            sizeModifier = Modifier
                .fillMaxSize(0.9f),
            enableLightSpot = false,
            shape = CircleShape,
            effect = effectData
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxSize(0.75f)
                .background(Color(0xFF0A0A0A))
        )
    }
}

@Composable
private fun ClippedPad(
    topLeft: Boolean,
    topRight: Boolean,
    bottomLeft: Boolean,
    bottomRight: Boolean,
    effectData: MidiEffectData
) {
    GenericLaunchpadButton(
        sizeModifier = Modifier
            .clip(
                CutCornerShape(
                    bottomEndPercent = if (topLeft) 30 else 0,
                    bottomStartPercent = if (topRight) 30 else 0,
                    topEndPercent = if (bottomLeft) 30 else 0,
                    topStartPercent = if (bottomRight) 30 else 0,
                )
            )
            .fillMaxSize(0.9f),
        effect = effectData
    )
}

@Composable
private fun SetupButton() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .fillMaxSize(0.4f)
            .background(Color(0xFF121212))
            .border(1.dp, Color(0xFF171717), CircleShape),
    )
}