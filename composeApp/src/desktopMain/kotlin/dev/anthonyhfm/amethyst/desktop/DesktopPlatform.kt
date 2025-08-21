package dev.anthonyhfm.amethyst.desktop

import com.formdev.flatlaf.util.SystemInfo

sealed interface DesktopPlatform {
    data object MacOS : DesktopPlatform
    data object Windows : DesktopPlatform
    data object Linux : DesktopPlatform
    data object Unknown : DesktopPlatform

    companion object {
        fun get(): DesktopPlatform {
            if (SystemInfo.isMacOS) {
                return MacOS
            } else if (SystemInfo.isWindows_10_orLater) {
                return Windows
            } else if (SystemInfo.isLinux) {
                return Linux
            }

            return Unknown
        }
    }
}