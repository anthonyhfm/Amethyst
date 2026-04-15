package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogState
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun AlertDialog(
    state: DialogState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { state.visible = false },
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        state = state,
        onDismiss = onDismiss,
    ) {
        DialogContent(
            modifier = modifier,
            showCloseButton = false,
        ) {
            content()
        }
    }
}

@Composable
fun AlertDialogHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun AlertDialogTitle(
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
fun AlertDialogDescription(
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
fun AlertDialogFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        content = content,
    )
}

@Composable
fun AlertDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Small,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        size = size,
        content = content,
    )
}

@Composable
fun AlertDialogCancel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Small,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Outline,
        size = size,
        content = content,
    )
}
