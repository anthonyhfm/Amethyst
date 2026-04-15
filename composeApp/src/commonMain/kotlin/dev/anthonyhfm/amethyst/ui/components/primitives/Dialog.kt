package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogState
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledDialogPanel
import com.composeunstyled.UnstyledScrim
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.large
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

internal val LocalDialogDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun Dialog(
    state: DialogState,
    onDismiss: () -> Unit = { state.visible = false },
    content: @Composable () -> Unit,
) {
    UnstyledDialog(
        state = state,
        onDismiss = onDismiss,
    ) {
        CompositionLocalProvider(LocalDialogDismiss provides onDismiss) {
            UnstyledScrim(
                scrimColor = Color.Black.copy(alpha = 0.6f),
                enter = fadeIn(),
                exit = fadeOut(),
            )
            content()
        }
    }
}

@Composable
fun DialogContent(
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    UnstyledDialogPanel(
        shape = DefaultShape,
        backgroundColor = Theme[colors][background],
        contentColor = Theme[colors][foreground],
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .widthIn(max = 512.dp)
            .border(1.dp, Theme[colors][border], DefaultShape),
    ) {
        ProvideTextStyle(Theme[typography][p].copy(color = Theme[colors][foreground])) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }

                if (showCloseButton) {
                    DialogClose(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DialogHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun DialogTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][large],
        color = Theme[colors][foreground],
    )
}

@Composable
fun DialogDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][small],
        color = Theme[colors][mutedForeground],
    )
}

@Composable
fun DialogFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        content = content,
    )
}

@Composable
fun DialogClose(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = LocalDialogDismiss.current,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.alpha(if (hovered) 1f else 0.7f),
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][mutedForeground],
        )
    }
}
