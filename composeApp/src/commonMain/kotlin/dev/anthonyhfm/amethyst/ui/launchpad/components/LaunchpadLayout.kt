package dev.anthonyhfm.amethyst.ui.launchpad.components

enum class LaunchpadLayout(
    val x: Int,
    val y: Int,
    val offsetX: Int,
    val offsetY: Int
) {
    LAYOUT_8X8(8, 8, 1, 1), // Matrix, Midi Fighter 64
    LAYOUT_9X9(9, 9, 1, 1), // e.G. Launchpad X, Launchpad Mini, Launchpad Mk2
    LAYOUT_10X10(10, 10, 0, 0), // Launchpad Pro
}