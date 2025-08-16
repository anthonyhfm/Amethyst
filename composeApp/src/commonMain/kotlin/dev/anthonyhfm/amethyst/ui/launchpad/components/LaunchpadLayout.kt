package dev.anthonyhfm.amethyst.ui.launchpad.components

enum class LaunchpadLayout(
    val cols: Int,
    val rows: Int,
    val offsetX: Int,
    val offsetY: Int,
    val mainOffsetX: Int,
    val mainOffsetY: Int,
    val mainGridMaxX: Int,
    val mainGridMaxY: Int,
) {
    LAYOUT_8X8(
        cols = 8,
        rows = 8,
        offsetX = 1,
        offsetY = 1,
        mainOffsetX = 0,
        mainOffsetY = 0,
        mainGridMaxX = 7,
        mainGridMaxY = 7
    ), // Matrix, Midi Fighter 64

    LAYOUT_9X9(
        cols = 9,
        rows = 9,
        offsetX = 1,
        offsetY = 1,
        mainOffsetX = 0,
        mainOffsetY = 1,
        mainGridMaxX = 7,
        mainGridMaxY = 8
    ), // e.G. Launchpad X, Launchpad Mini, Launchpad Mk2
    LAYOUT_10X10(
        cols = 10,
        rows = 10,
        offsetX = 0,
        offsetY = 0,
        mainOffsetX = 1,
        mainOffsetY = 1,
        mainGridMaxX = 8,
        mainGridMaxY = 8
    ), // Launchpad Pro
}