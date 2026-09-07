package dev.anthonyhfm.amethyst.timeline.contract

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils

enum class TimelineEditorTool {
    NORMAL,
    DRAW
}

enum class GridResolution(
    val snapDivisionsPerBeat: Int,
    val subBeatsPerBeat: Int,
    val label: String,
) {
    Quarter(snapDivisionsPerBeat = 1, subBeatsPerBeat = 1, label = "1/4"),
    Eighth(snapDivisionsPerBeat = 2, subBeatsPerBeat = 2, label = "1/8"),
    Sixteenth(snapDivisionsPerBeat = 4, subBeatsPerBeat = 4, label = "1/16"),
    ThirtySecond(snapDivisionsPerBeat = 8, subBeatsPerBeat = 8, label = "1/32"),
    SixtyFourth(snapDivisionsPerBeat = 16, subBeatsPerBeat = 16, label = "1/64"),
    OneTwentyEighth(snapDivisionsPerBeat = 32, subBeatsPerBeat = 32, label = "1/128");

    companion object {
        fun fromZoomFactor(zoomFactor: Float): GridResolution = when {
            zoomFactor < 1.5f -> Quarter
            zoomFactor < 2.5f -> Eighth
            zoomFactor < 4f -> Sixteenth
            zoomFactor < 6f -> ThirtySecond
            zoomFactor < 9f -> SixtyFourth
            else -> OneTwentyEighth
        }
    }
}

data class TimelineTimingContext(
    val bpm: Double,
    val gridType: GridUtils.GridType,
    val zoomLevel: Float,
    val playheadPositionMs: Long,
    val isPlaying: Boolean
)

data class TimelineEditorSurface(
    val activeTool: TimelineEditorTool = TimelineEditorTool.NORMAL,
    val timingContext: TimelineTimingContext? = null,
    val gridResolution: GridResolution = GridResolution.Quarter
)

data class TimelineActiveEditorContext(
    val clipContext: TimelineClipContext,
    val surface: TimelineEditorSurface
) {
    val clipKey: TimelineClipKey
        get() = clipContext.clipKey

    val isNoteCapable: Boolean
        get() = clipContext.isNoteCapable
}
