package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun CollapsedChainDevice(
    device: GenericChainDevice<*>,
    modifier: Modifier = Modifier
) {
    val selections by SelectionManager.selections.collectAsState()
    val isSelected = selections.any { it.selectionUUID == device.selectionUUID }

    CollapsedChainDevice(
        title = device.title,
        isSelected = isSelected,
        isDragging = device.isDragging.value,
        modifier = modifier,
        titleBarModifier = LocalTitleBarModifier.current
    )
}

@Composable
fun CollapsedChainDevice(
    title: String,
    isSelected: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    titleBarModifier: Modifier = Modifier
) {
    val titleBarColor = if (isSelected) {
        Theme[colors][selectionSurface]
    } else {
        Theme[chainColorTokens][chainSurfaceRaised]
    }

    val titleColor = if (isSelected) {
        Theme[colors][selectionForeground]
    } else {
        Theme[colors][cardForeground]
    }

    val borderColor = if (isSelected) {
        Theme[colors][selectionSurface]
    } else {
        Theme[chainColorTokens][chainBorder]
    }

    Box(
        modifier = modifier
            .clip(DefaultShape)
            .fillMaxHeight()
            .width(28.dp)
            .background(Theme[chainColorTokens][chainSurface])
            .border(1.dp, borderColor, DefaultShape)
            .alpha(if (isDragging) 0.2f else 1f)
            .then(titleBarModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .background(titleBarColor)
        ) {
            Text(
                text = title,
                style = Theme[typography][small],
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .verticalRotatedText()
            )
        }
    }
}

private fun Modifier.verticalRotatedText(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth
        )
    )
    layout(placeable.height, placeable.width) {
        placeable.placeRelativeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
            layerBlock = {
                rotationZ = -90f
            }
        )
    }
}