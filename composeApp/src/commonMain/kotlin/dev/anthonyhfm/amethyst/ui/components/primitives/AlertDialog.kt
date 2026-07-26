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

import androidx.compose.foundation.layout.fillMaxWidth
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform

@Composable
fun AlertDialog(
    state: DialogState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { state.visible = false },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (platform is Platform.Android || platform is Platform.iOS) {
        Drawer(
            state = state,
            onDismiss = onDismiss,
        ) {
            DrawerContent(
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    } else {
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
    content: @Composable () -> Unit,
) {
    if (platform is Platform.Android || platform is Platform.iOS) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
fun AlertDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = if (platform is Platform.Android || platform is Platform.iOS) ButtonSize.Mobile else ButtonSize.Small,
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
    size: ButtonSize = if (platform is Platform.Android || platform is Platform.iOS) ButtonSize.Mobile else ButtonSize.Small,
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
