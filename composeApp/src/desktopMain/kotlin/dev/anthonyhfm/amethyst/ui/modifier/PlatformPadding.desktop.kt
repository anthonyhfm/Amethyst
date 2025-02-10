package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform

actual fun Modifier.platformPaddingTop(): Modifier {
    return if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
        this.padding(top = 26.dp)
    } else {
        this
    }
}