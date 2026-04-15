package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
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
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class SheetSide {
    Top,
    Bottom,
    Left,
    Right,
}

internal val LocalSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun Sheet(
    state: DialogState,
    onDismiss: () -> Unit = { state.visible = false },
    content: @Composable () -> Unit,
) {
    UnstyledDialog(
        state = state,
        onDismiss = onDismiss,
    ) {
        CompositionLocalProvider(LocalSheetDismiss provides onDismiss) {
            UnstyledScrim(
                scrimColor = Theme[colors][foreground].copy(alpha = 0.5f),
                enter = fadeIn(),
                exit = fadeOut(),
            )
            content()
        }
    }
}

@Composable
fun SheetTrigger(
    state: DialogState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    UnstyledButton(
        onClick = { state.visible = true },
        modifier = modifier,
        indication = null,
        contentPadding = PaddingValues(0.dp),
    ) {
        content()
    }
}

@Composable
fun SheetContent(
    side: SheetSide = SheetSide.Right,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter: EnterTransition = when (side) {
        SheetSide.Left -> slideInHorizontally { -it }
        SheetSide.Right -> slideInHorizontally { it }
        SheetSide.Top -> slideInVertically { -it }
        SheetSide.Bottom -> slideInVertically { it }
    }

    val exit: ExitTransition = when (side) {
        SheetSide.Left -> slideOutHorizontally { -it }
        SheetSide.Right -> slideOutHorizontally { it }
        SheetSide.Top -> slideOutVertically { -it }
        SheetSide.Bottom -> slideOutVertically { it }
    }

    val panelAlignment = when (side) {
        SheetSide.Left -> Alignment.CenterStart
        SheetSide.Right -> Alignment.CenterEnd
        SheetSide.Top -> Alignment.TopCenter
        SheetSide.Bottom -> Alignment.BottomCenter
    }

    val borderColor = Theme[colors][border]

    val borderModifier = Modifier.drawBehind {
        val strokeWidth = 1.dp.toPx()
        when (side) {
            SheetSide.Left -> drawLine(
                borderColor,
                Offset(size.width, 0f),
                Offset(size.width, size.height),
                strokeWidth,
            )
            SheetSide.Right -> drawLine(
                borderColor,
                Offset(0f, 0f),
                Offset(0f, size.height),
                strokeWidth,
            )
            SheetSide.Top -> drawLine(
                borderColor,
                Offset(0f, size.height),
                Offset(size.width, size.height),
                strokeWidth,
            )
            SheetSide.Bottom -> drawLine(
                borderColor,
                Offset(0f, 0f),
                Offset(size.width, 0f),
                strokeWidth,
            )
        }
    }

    val sizeModifier = when (side) {
        SheetSide.Left, SheetSide.Right -> Modifier.fillMaxHeight().widthIn(max = 400.dp)
        SheetSide.Top, SheetSide.Bottom -> Modifier.fillMaxWidth()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = panelAlignment,
    ) {
        UnstyledDialogPanel(
            shape = RectangleShape,
            backgroundColor = Theme[colors][background],
            contentColor = Theme[colors][foreground],
            enter = enter,
            exit = exit,
            modifier = sizeModifier.then(borderModifier).then(modifier),
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
                        SheetClose(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SheetHeader(
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
fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][h4],
        color = Theme[colors][foreground],
    )
}

@Composable
fun SheetDescription(
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
fun SheetFooter(
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
fun SheetClose(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = LocalSheetDismiss.current,
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
