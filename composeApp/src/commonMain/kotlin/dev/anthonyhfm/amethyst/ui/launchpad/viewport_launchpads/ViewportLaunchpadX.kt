package dev.anthonyhfm.amethyst.ui.launchpad.viewport_launchpads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

class ViewportLaunchpadX(
    override var shape: Shape = RoundedCornerShape(2),
    override var size: Size = Size(9f, 9f),
) : LaunchpadViewportElement() {
    override val content: @Composable (() -> Unit) = {
        val previewGrid by previewState.grid

        GenericLaunchpadLayout(
            layoutType = LaunchpadLayout.LAYOUT_9X9,
            modifier = Modifier
                .size(width = size.width.dp * 40, height = size.height.dp * 40)
                .clip(shape)
                .background(Color(0xFF0d0d0d))
                .padding(12.dp),
        ) { x, y ->
            GridPad(
                x = x,
                y = y,
                effectData = previewGrid[x + y * 10],
                onClick = null
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridPad(
    x: Int,
    y: Int,
    effectData: MidiEffectData,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
        if (y == 9 && x > 0 && x < 9) {
            EdgePad(
                effectData = effectData
            )
        } else if ( x == 9 && y > 0 && y < 9) {
            EdgePad(
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
                    .fillMaxSize(0.86f),
                effect = effectData,
                shape = RoundedCornerShape(4)
            )
        }
    }
}

@Composable
private fun EdgePad(effectData: MidiEffectData) {
    Box(
        modifier = Modifier
            .fillMaxSize(),

        contentAlignment = Alignment.Center
    ) {
        GenericLaunchpadButton(
            sizeModifier = Modifier
                .fillMaxSize(0.86f),
            enableLightSpot = false,
            effect = effectData,
            shape = RoundedCornerShape(4)
        )

        Box(
            modifier = Modifier
                .fillMaxSize(0.76f)
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
            .fillMaxSize(0.86f),
        effect = effectData,
        shape = RoundedCornerShape(4)
    )
}