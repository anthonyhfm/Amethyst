package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground

@Composable
actual fun AddDeviceButton(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        variant = ButtonVariant.Default,
        size = ButtonSize.Icon,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = Theme[colors][primaryForeground],
        )
    }
}
