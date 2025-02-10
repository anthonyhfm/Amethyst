package dev.anthonyhfm.amethyst.ui.previewdevices

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData

@Composable
fun LaunchpadPro(
    previewState: PreviewState,
    onClick: ((x: Int, y: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by previewState.grid.collectAsState()

    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(6))
            .clip(RoundedCornerShape(6))
            .aspectRatio(1f / 1f)
            .background(Color(0xFF141414))
            .border(1.5.dp, Color(0xFF787878), RoundedCornerShape(6)),
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
                        onClick = if (onClick == null) {
                            null
                        } else {
                            {
                                onClick(x, y)
                            }
                        }
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
            RegularPad(
                effectData = effectData
            )
        }
    }
}

@Composable
private fun CircularPad(effectData: MidiEffectData) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .fillMaxSize(0.8f)
            .background(computeColor(effectData)),

        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxSize(0.8f)
                .background(Color(0xFF0A0A0A))
        )
    }
}

@Composable
private fun RegularPad(effectData: MidiEffectData) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10))
            .fillMaxSize(0.9f)
            .background(computeColor(effectData))
    )
}

@Composable
private fun ClippedPad(
    topLeft: Boolean,
    topRight: Boolean,
    bottomLeft: Boolean,
    bottomRight: Boolean,
    effectData: MidiEffectData
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10))
            .clip(
                CutCornerShape(
                    bottomEndPercent = if (topLeft) 30 else 0,
                    bottomStartPercent = if (topRight) 30 else 0,
                    topEndPercent = if (bottomLeft) 30 else 0,
                    topStartPercent = if (bottomRight) 30 else 0,
                )
            )
            .fillMaxSize(0.9f)
            .background(computeColor(effectData))
    )
}

private fun computeColor(effectData: MidiEffectData): Color {
    val minComponent = 0x50 // Dunkelste Komponente
    val maxComponent = 0xFF // Hellste Komponente

    fun scaleColor(component: Int): Int {
        // Skaliert den Farbwert von 0..63 auf 0x50..0xFF
        return ((component / 63f) * (maxComponent - minComponent) + minComponent).toInt()
    }

    val red = scaleColor(effectData.r)
    val green = scaleColor(effectData.g)
    val blue = scaleColor(effectData.b)

    return Color(red, green, blue)
}