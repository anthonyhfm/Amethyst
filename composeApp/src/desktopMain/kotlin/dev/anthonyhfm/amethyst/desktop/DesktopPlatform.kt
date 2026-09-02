package dev.anthonyhfm.amethyst.desktop

sealed interface DesktopPlatform {
    data object MacOS : DesktopPlatform
    data object Windows : DesktopPlatform
    data object Linux : DesktopPlatform
    data object Unknown : DesktopPlatform

    companion object {
        fun get(): DesktopPlatform {
            val osName = System.getProperty("os.name")?.lowercase().orEmpty()

            return when {
                osName.startsWith("windows") -> Windows
                osName.startsWith("mac") -> MacOS
                osName.startsWith("linux") -> Linux
                else -> Unknown
            }
        }
    }
}