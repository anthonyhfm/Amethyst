package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogState
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledDialogPanel
import com.composeunstyled.UnstyledScrim
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private val DrawerShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)

@Composable
fun Drawer(
    state: DialogState,
    onDismiss: () -> Unit = { state.visible = false },
    content: @Composable ColumnScope.() -> Unit,
) {
    UnstyledDialog(
        state = state,
        onDismiss = onDismiss,
    ) {
        UnstyledScrim(
            scrimColor = Theme[colors][foreground].copy(alpha = 0.5f),
            enter = fadeIn(),
            exit = fadeOut(),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            UnstyledDialogPanel(
                shape = DrawerShape,
                backgroundColor = Theme[colors][background],
                contentColor = Theme[colors][foreground],
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProvideTextStyle(Theme[typography][p].copy(color = Theme[colors][foreground])) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Handle indicator
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp, bottom = 8.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(width = 48.dp, height = 4.dp)
                                .background(Theme[colors][muted], FullShape),
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerTrigger(
    state: DialogState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    UnstyledButton(
        onClick = { state.visible = true },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DrawerContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
fun DrawerHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun DrawerTitle(
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
fun DrawerDescription(
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
fun DrawerFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun DrawerClose(
    state: DialogState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    UnstyledButton(
        onClick = { state.visible = false },
        modifier = modifier,
        content = content,
    )
}
