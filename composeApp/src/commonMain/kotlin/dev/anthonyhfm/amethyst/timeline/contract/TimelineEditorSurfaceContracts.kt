package dev.anthonyhfm.amethyst.timeline.contract

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils

enum class TimelineEditorTool {
    SELECT,
    DRAW,
    ERASE
}

enum class GridResolution(val snapDivisionsPerBeat: Int, val subBeatsPerBeat: Int) {
    Quarter(snapDivisionsPerBeat = 4, subBeatsPerBeat = 1),
    Eighth(snapDivisionsPerBeat = 8, subBeatsPerBeat = 2),
    Sixteenth(snapDivisionsPerBeat = 16, subBeatsPerBeat = 4),
    ThirtySecond(snapDivisionsPerBeat = 32, subBeatsPerBeat = 8),
    SixtyFourth(snapDivisionsPerBeat = 64, subBeatsPerBeat = 16),
    OneTwentyEighth(snapDivisionsPerBeat = 128, subBeatsPerBeat = 32);

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
    val activeTool: TimelineEditorTool = TimelineEditorTool.SELECT,
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
