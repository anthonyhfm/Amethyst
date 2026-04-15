package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken

val timelineColorTokens = ThemeProperty<Color>("timeline_colors")
val timelineDimensionTokens = ThemeProperty<Dp>("timeline_dimensions")

val timelineCanvas = ThemeToken<Color>("timeline_canvas")
val timelineShellBorder = ThemeToken<Color>("timeline_shell_border")
val timelineRulerSurface = ThemeToken<Color>("timeline_ruler_surface")
val timelineRulerHighlight = ThemeToken<Color>("timeline_ruler_highlight")
val timelineRulerAccent = ThemeToken<Color>("timeline_ruler_accent")
val timelineRulerText = ThemeToken<Color>("timeline_ruler_text")
val timelineTickMinor = ThemeToken<Color>("timeline_tick_minor")
val timelineTickMajor = ThemeToken<Color>("timeline_tick_major")
val timelineGridMinor = ThemeToken<Color>("timeline_grid_minor")
val timelineGridMajor = ThemeToken<Color>("timeline_grid_major")
val timelineLaneSurface = ThemeToken<Color>("timeline_lane_surface")
val timelineLaneSurfaceRaised = ThemeToken<Color>("timeline_lane_surface_raised")
val timelineTrackHeaderSurface = ThemeToken<Color>("timeline_track_header_surface")
val timelineTrackHeaderSurfaceSelected = ThemeToken<Color>("timeline_track_header_surface_selected")
val timelineTrackHeaderContent = ThemeToken<Color>("timeline_track_header_content")
val timelineTrackHeaderContentSelected = ThemeToken<Color>("timeline_track_header_content_selected")
val timelineTrackHeaderBorder = ThemeToken<Color>("timeline_track_header_border")
val timelineSelectionFill = ThemeToken<Color>("timeline_selection_fill")
val timelineSelectionStroke = ThemeToken<Color>("timeline_selection_stroke")
val timelineSelectionCursor = ThemeToken<Color>("timeline_selection_cursor")
val timelinePlayhead = ThemeToken<Color>("timeline_playhead")
val timelinePlayheadGlow = ThemeToken<Color>("timeline_playhead_glow")
val timelineAudioClipSurface = ThemeToken<Color>("timeline_audio_clip_surface")
val timelineAudioClipHeader = ThemeToken<Color>("timeline_audio_clip_header")
val timelineAudioClipBorder = ThemeToken<Color>("timeline_audio_clip_border")
val timelineAudioClipContent = ThemeToken<Color>("timeline_audio_clip_content")
val timelineLightsClipSurface = ThemeToken<Color>("timeline_lights_clip_surface")
val timelineLightsClipHeader = ThemeToken<Color>("timeline_lights_clip_header")
val timelineLightsClipBorder = ThemeToken<Color>("timeline_lights_clip_border")
val timelineLightsClipContent = ThemeToken<Color>("timeline_lights_clip_content")
val timelineMidiClipSurface = ThemeToken<Color>("timeline_midi_clip_surface")
val timelineMidiClipHeader = ThemeToken<Color>("timeline_midi_clip_header")
val timelineMidiClipBorder = ThemeToken<Color>("timeline_midi_clip_border")
val timelineMidiClipContent = ThemeToken<Color>("timeline_midi_clip_content")
val timelineClipSelectedSurface = ThemeToken<Color>("timeline_clip_selected_surface")
val timelineClipSelectedHeader = ThemeToken<Color>("timeline_clip_selected_header")
val timelineClipSelectedBorder = ThemeToken<Color>("timeline_clip_selected_border")
val timelineClipSelectedContent = ThemeToken<Color>("timeline_clip_selected_content")
val timelineAutomationLaneSurface = ThemeToken<Color>("timeline_automation_lane_surface")
val timelineAutomationLaneAccent = ThemeToken<Color>("timeline_automation_lane_accent")

val timelineTrackHeaderWidth = ThemeToken<Dp>("timeline_track_header_width")
val timelineLaneHeight = ThemeToken<Dp>("timeline_lane_height")
val timelineLaneSpacing = ThemeToken<Dp>("timeline_lane_spacing")
val timelineRulerHeight = ThemeToken<Dp>("timeline_ruler_height")
val timelineAddTrackHeight = ThemeToken<Dp>("timeline_add_track_height")
val timelineClipCornerRadius = ThemeToken<Dp>("timeline_clip_corner_radius")
val timelineClipHeaderHeight = ThemeToken<Dp>("timeline_clip_header_height")
val timelineResizeHandleWidth = ThemeToken<Dp>("timeline_resize_handle_width")
val timelinePlayheadWidth = ThemeToken<Dp>("timeline_playhead_width")
val timelineSelectionCursorWidth = ThemeToken<Dp>("timeline_selection_cursor_width")
val timelineSelectionCornerRadius = ThemeToken<Dp>("timeline_selection_corner_radius")

internal val lightTimelineColorMap = mapOf(
    timelineCanvas to Color(0xFFF3F5F8),
    timelineShellBorder to Color(0xFFD0D8E4),
    timelineRulerSurface to Color(0xFFE6EAF0),
    timelineRulerHighlight to Color(0xFFF9FBFE),
    timelineRulerAccent to Color(0xFFDCE4EF),
    timelineRulerText to Color(0xFF4B5563),
    timelineTickMinor to Color(0xFF95A1B2),
    timelineTickMajor to Color(0xFF5B6778),
    timelineGridMinor to Color(0xFFD6DDE7),
    timelineGridMajor to Color(0xFFBCC6D3),
    timelineLaneSurface to Color(0xFFF7F9FC),
    timelineLaneSurfaceRaised to Color(0xFFFFFFFF),
    timelineTrackHeaderSurface to Color(0xFFECEFF4),
    timelineTrackHeaderSurfaceSelected to Color(0xFFD9E6FF),
    timelineTrackHeaderContent to Color(0xFF111827),
    timelineTrackHeaderContentSelected to Color(0xFF0B1220),
    timelineTrackHeaderBorder to Color(0xFFD2D9E4),
    timelineSelectionFill to Color(0x332563EB),
    timelineSelectionStroke to Color(0xFF2563EB),
    timelineSelectionCursor to Color(0xFF1D4ED8),
    timelinePlayhead to Color(0xFF10B981),
    timelinePlayheadGlow to Color(0x9910B981),
    timelineAudioClipSurface to Color(0xFF5D74E6),
    timelineAudioClipHeader to Color(0xFFB5C4FF),
    timelineAudioClipBorder to Color(0xFF8EA4FF),
    timelineAudioClipContent to Color(0xFFF8FAFF),
    timelineLightsClipSurface to Color(0xFFC48A1E),
    timelineLightsClipHeader to Color(0xFFF0D089),
    timelineLightsClipBorder to Color(0xFFDDAE4D),
    timelineLightsClipContent to Color(0xFF231A09),
    timelineMidiClipSurface to Color(0xFFC55D91),
    timelineMidiClipHeader to Color(0xFFF1B1D1),
    timelineMidiClipBorder to Color(0xFFD987B0),
    timelineMidiClipContent to Color(0xFFFFF7FB),
    timelineClipSelectedSurface to Color(0xFF3B82F6),
    timelineClipSelectedHeader to Color(0xFFD7E7FF),
    timelineClipSelectedBorder to Color(0xFF93C5FD),
    timelineClipSelectedContent to Color(0xFF08111F),
    timelineAutomationLaneSurface to Color(0xFFEAF0F7),
    timelineAutomationLaneAccent to Color(0xFF2563EB),
)

internal val darkTimelineColorMap = mapOf(
    timelineCanvas to Color(0xFF0C1014),
    timelineShellBorder to Color(0xFF222B35),
    timelineRulerSurface to Color(0xFF10151A),
    timelineRulerHighlight to Color(0xFF172029),
    timelineRulerAccent to Color(0xFF151C24),
    timelineRulerText to Color(0xFFB5BFCC),
    timelineTickMinor to Color(0xFF4B5563),
    timelineTickMajor to Color(0xFF8692A2),
    timelineGridMinor to Color(0xFF1A2029),
    timelineGridMajor to Color(0xFF28303B),
    timelineLaneSurface to Color(0xFF12171C),
    timelineLaneSurfaceRaised to Color(0xFF171D23),
    timelineTrackHeaderSurface to Color(0xFF151A20),
    timelineTrackHeaderSurfaceSelected to Color(0xFF1E2630),
    timelineTrackHeaderContent to Color(0xFFE5E7EB),
    timelineTrackHeaderContentSelected to Color(0xFFF8FAFC),
    timelineTrackHeaderBorder to Color(0xFF27303B),
    timelineSelectionFill to Color(0x335A9BFF),
    timelineSelectionStroke to Color(0xFF6CA5FF),
    timelineSelectionCursor to Color(0xFF9CC2FF),
    timelinePlayhead to Color(0xFF7EF7AC),
    timelinePlayheadGlow to Color(0xAA7EF7AC),
    timelineAudioClipSurface to Color(0xFF4259D8),
    timelineAudioClipHeader to Color(0xFF9EB2FF),
    timelineAudioClipBorder to Color(0xFF7E96FF),
    timelineAudioClipContent to Color(0xFFF5F8FF),
    timelineLightsClipSurface to Color(0xFF8C6820),
    timelineLightsClipHeader to Color(0xFFE7C26E),
    timelineLightsClipBorder to Color(0xFFC79636),
    timelineLightsClipContent to Color(0xFFFFF7E6),
    timelineMidiClipSurface to Color(0xFF9A4D74),
    timelineMidiClipHeader to Color(0xFFE39AC2),
    timelineMidiClipBorder to Color(0xFFC971A0),
    timelineMidiClipContent to Color(0xFFFFF5FA),
    timelineClipSelectedSurface to Color(0xFF5B8DFF),
    timelineClipSelectedHeader to Color(0xFFE4EEFF),
    timelineClipSelectedBorder to Color(0xFFB5CCFF),
    timelineClipSelectedContent to Color(0xFF08101F),
    timelineAutomationLaneSurface to Color(0xFF10161C),
    timelineAutomationLaneAccent to Color(0xFF6CA5FF),
)

internal val timelineDimensionMap = mapOf(
    timelineTrackHeaderWidth to 200.dp,
    timelineLaneHeight to 120.dp,
    timelineLaneSpacing to 6.dp,
    timelineRulerHeight to 32.dp,
    timelineAddTrackHeight to 56.dp,
    timelineClipCornerRadius to 6.dp,
    timelineClipHeaderHeight to 20.dp,
    timelineResizeHandleWidth to 6.dp,
    timelinePlayheadWidth to 2.dp,
    timelineSelectionCursorWidth to 3.dp,
    timelineSelectionCornerRadius to 2.dp,
)

data class TimelinePalette(
    val canvas: Color,
    val shellBorder: Color,
    val rulerSurface: Color,
    val rulerHighlight: Color,
    val rulerAccent: Color,
    val rulerText: Color,
    val tickMinor: Color,
    val tickMajor: Color,
    val gridMinor: Color,
    val gridMajor: Color,
    val laneSurface: Color,
    val laneSurfaceRaised: Color,
    val trackHeaderSurface: Color,
    val trackHeaderSurfaceSelected: Color,
    val trackHeaderContent: Color,
    val trackHeaderContentSelected: Color,
    val trackHeaderBorder: Color,
    val selectionFill: Color,
    val selectionStroke: Color,
    val selectionCursor: Color,
    val playhead: Color,
    val playheadGlow: Color,
    val automationLaneSurface: Color,
    val automationLaneAccent: Color,
)

data class TimelineMetrics(
    val trackHeaderWidth: Dp,
    val laneHeight: Dp,
    val laneSpacing: Dp,
    val rulerHeight: Dp,
    val addTrackHeight: Dp,
    val clipCornerRadius: Dp,
    val clipHeaderHeight: Dp,
    val resizeHandleWidth: Dp,
    val playheadWidth: Dp,
    val selectionCursorWidth: Dp,
    val selectionCornerRadius: Dp,
)

data class TimelineTrackHeaderColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

data class TimelineClipColors(
    val background: Color,
    val header: Color,
    val border: Color,
    val content: Color,
)

enum class TimelineClipRole {
    Audio,
    Lights,
    Midi,
}

object TimelineTheme {
    val palette: TimelinePalette
        @Composable get() = TimelinePalette(
            canvas = Theme[timelineColorTokens][timelineCanvas],
            shellBorder = Theme[timelineColorTokens][timelineShellBorder],
            rulerSurface = Theme[timelineColorTokens][timelineRulerSurface],
            rulerHighlight = Theme[timelineColorTokens][timelineRulerHighlight],
            rulerAccent = Theme[timelineColorTokens][timelineRulerAccent],
            rulerText = Theme[timelineColorTokens][timelineRulerText],
            tickMinor = Theme[timelineColorTokens][timelineTickMinor],
            tickMajor = Theme[timelineColorTokens][timelineTickMajor],
            gridMinor = Theme[timelineColorTokens][timelineGridMinor],
            gridMajor = Theme[timelineColorTokens][timelineGridMajor],
            laneSurface = Theme[timelineColorTokens][timelineLaneSurface],
            laneSurfaceRaised = Theme[timelineColorTokens][timelineLaneSurfaceRaised],
            trackHeaderSurface = Theme[timelineColorTokens][timelineTrackHeaderSurface],
            trackHeaderSurfaceSelected = Theme[timelineColorTokens][timelineTrackHeaderSurfaceSelected],
            trackHeaderContent = Theme[timelineColorTokens][timelineTrackHeaderContent],
            trackHeaderContentSelected = Theme[timelineColorTokens][timelineTrackHeaderContentSelected],
            trackHeaderBorder = Theme[timelineColorTokens][timelineTrackHeaderBorder],
            selectionFill = Theme[timelineColorTokens][timelineSelectionFill],
            selectionStroke = Theme[timelineColorTokens][timelineSelectionStroke],
            selectionCursor = Theme[timelineColorTokens][timelineSelectionCursor],
            playhead = Theme[timelineColorTokens][timelinePlayhead],
            playheadGlow = Theme[timelineColorTokens][timelinePlayheadGlow],
            automationLaneSurface = Theme[timelineColorTokens][timelineAutomationLaneSurface],
            automationLaneAccent = Theme[timelineColorTokens][timelineAutomationLaneAccent],
        )

    val dimensions: TimelineMetrics
        @Composable get() = TimelineMetrics(
            trackHeaderWidth = Theme[timelineDimensionTokens][timelineTrackHeaderWidth],
            laneHeight = Theme[timelineDimensionTokens][timelineLaneHeight],
            laneSpacing = Theme[timelineDimensionTokens][timelineLaneSpacing],
            rulerHeight = Theme[timelineDimensionTokens][timelineRulerHeight],
            addTrackHeight = Theme[timelineDimensionTokens][timelineAddTrackHeight],
            clipCornerRadius = Theme[timelineDimensionTokens][timelineClipCornerRadius],
            clipHeaderHeight = Theme[timelineDimensionTokens][timelineClipHeaderHeight],
            resizeHandleWidth = Theme[timelineDimensionTokens][timelineResizeHandleWidth],
            playheadWidth = Theme[timelineDimensionTokens][timelinePlayheadWidth],
            selectionCursorWidth = Theme[timelineDimensionTokens][timelineSelectionCursorWidth],
            selectionCornerRadius = Theme[timelineDimensionTokens][timelineSelectionCornerRadius],
        )

    @Composable
    fun trackHeaderColors(selected: Boolean): TimelineTrackHeaderColors {
        val palette = palette
        return if (selected) {
            TimelineTrackHeaderColors(
                container = palette.trackHeaderSurfaceSelected,
                content = palette.trackHeaderContentSelected,
                border = palette.selectionStroke,
            )
        } else {
            TimelineTrackHeaderColors(
                container = palette.trackHeaderSurface,
                content = palette.trackHeaderContent,
                border = palette.trackHeaderBorder,
            )
        }
    }

    @Composable
    fun clipColors(role: TimelineClipRole, selected: Boolean): TimelineClipColors {
        return if (selected) {
            TimelineClipColors(
                background = Theme[timelineColorTokens][timelineClipSelectedSurface],
                header = Theme[timelineColorTokens][timelineClipSelectedHeader],
                border = Theme[timelineColorTokens][timelineClipSelectedBorder],
                content = Theme[timelineColorTokens][timelineClipSelectedContent],
            )
        } else {
            when (role) {
                TimelineClipRole.Audio -> TimelineClipColors(
                    background = Theme[timelineColorTokens][timelineAudioClipSurface],
                    header = Theme[timelineColorTokens][timelineAudioClipHeader],
                    border = Theme[timelineColorTokens][timelineAudioClipBorder],
                    content = Theme[timelineColorTokens][timelineAudioClipContent],
                )

                TimelineClipRole.Lights -> TimelineClipColors(
                    background = Theme[timelineColorTokens][timelineLightsClipSurface],
                    header = Theme[timelineColorTokens][timelineLightsClipHeader],
                    border = Theme[timelineColorTokens][timelineLightsClipBorder],
                    content = Theme[timelineColorTokens][timelineLightsClipContent],
                )

                TimelineClipRole.Midi -> TimelineClipColors(
                    background = Theme[timelineColorTokens][timelineMidiClipSurface],
                    header = Theme[timelineColorTokens][timelineMidiClipHeader],
                    border = Theme[timelineColorTokens][timelineMidiClipBorder],
                    content = Theme[timelineColorTokens][timelineMidiClipContent],
                )
            }
        }
    }
}
