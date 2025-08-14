package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.desktop.DesktopPlatform

actual val platform: Platform
    get() = when (DesktopPlatform.get()) {
        DesktopPlatform.Linux -> Platform.Desktop.Linux
        DesktopPlatform.Windows -> Platform.Desktop.Windows
        DesktopPlatform.MacOS -> Platform.Desktop.MacOS
        DesktopPlatform.Unknown -> throw Exception("Unknown Desktop Platform")
    }