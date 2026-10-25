package dev.anthonyhfm.amethyst.workspace.ui.viewport

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.launchpad_selection_choose
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.modifier.rememberReducedMotion
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LaunchpadPickerOverlay(
    launchpad: LaunchpadViewportElement,
    index: Int,
    zoom: Float,
    onPick: (LaunchpadViewportElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(launchpad.launchpadId) { MutableInteractionSource() }
    val focusRequester = remember(launchpad.launchpadId) { FocusRequester() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = hovered || focused || pressed
    val reducedMotion = rememberReducedMotion()
    val highlight = Theme[colors][selectionBorder]
    val outline by animateColorAsState(
        targetValue = if (active) {
            highlight
        } else {
            Theme[colors][border]
        },
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 150)
        },
        label = "launchpad-picker-outline",
    )
    val label = "${index + 1} · ${launchpad.name}"

    LaunchedEffect(key1 = launchpad.launchpadId, key2 = index == 0) {
        if (index == 0) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .border(
                width = ((if (active) {
                    2.5f
                } else {
                    1f
                }) / zoom).dp,
                color = outline,
                shape = launchpad.shape,
            )
            .background(
                color = highlight.copy(
                    alpha = if (pressed) {
                        0.16f
                    } else if (active) {
                        0.07f
                    } else {
                        0f
                    }
                ),
                shape = launchpad.shape,
            )
            .focusRequester(focusRequester = focusRequester)
            .pointerHoverIcon(icon = PointerIcon.Hand)
            .hoverable(interactionSource = interactionSource)
            .semantics {
                contentDescription = label
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.launchpad_selection_choose, launchpad.name),
                onClick = { onPick(launchpad) },
            ),
    ) {
        Text(
            text = label,
            color = Theme[colors][foreground],
            fontSize = (12f / zoom).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
                .padding(all = (10f / zoom).dp)
                .background(
                    color = Theme[colors][card].copy(alpha = 0.96f),
                    shape = RoundedCornerShape(size = (6f / zoom).dp),
                )
                .padding(horizontal = (10f / zoom).dp, vertical = (5f / zoom).dp)
                .clearAndSetSemantics {},
        )
    }
}
