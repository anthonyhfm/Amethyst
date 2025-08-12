package dev.anthonyhfm.amethyst.desktop

sealed interface DesktopPlatform {
    data object MacOS : DesktopPlatform
    data object Windows : DesktopPlatform
    data object Linux : DesktopPlatform
    data object Unknown : DesktopPlatform

    companion object {
        fun get(): DesktopPlatform {
            val system = System.getProperty("os.name")

            if (system.contains("Mac OS X")) {
                return MacOS
            } else if (system.contains("Windows")) {
                return Windows
            }

            return Unknown
        }
    }
}