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

class ViewportMidiFighter64(
    override var shape: Shape = RoundedCornerShape(8),
    override var size: Size = Size(8f, 8f),
) : LaunchpadViewportElement() {
    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_8X8

    override val content: @Composable (() -> Unit) = {
        val previewGrid by previewState.grid

        GenericLaunchpadLayout(
            layoutType = layout,
            modifier = Modifier
                .size(width = size.width.dp * 40, height = size.height.dp * 40)
                .clip(shape)
                .background(Color(0xFF0d0d0d))
                .padding(8.dp),
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
        Box(
            modifier = Modifier
                .fillMaxSize(),

            contentAlignment = Alignment.Center
        ) {
            GenericLaunchpadButton(
                sizeModifier = Modifier
                    .fillMaxSize(0.8f),
                effect = effectData,
                enableLightSpot = false,
                shape = CircleShape
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.62f)
                    .background(Color(0xFF0A0A0A))
            )
        }
    }
}