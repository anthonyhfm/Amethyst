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

private fun blend(base: Color, overlay: Color, amount: Float): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (overlay.red - base.red) * fraction,
        green = base.green + (overlay.green - base.green) * fraction,
        blue = base.blue + (overlay.blue - base.blue) * fraction,
        alpha = base.alpha + (overlay.alpha - base.alpha) * fraction,
    )
}

private fun contrastingText(background: Color, palette: AmethystColorPalette): Color {
    val luminance = (background.red * 0.2126f) +
        (background.green * 0.7152f) +
        (background.blue * 0.0722f)
    return if (luminance > 0.45f) palette.background else palette.foreground
}

private fun clipColorSet(
    accent: Color,
    palette: AmethystColorPalette,
): List<Color> {
    val surface = blend(palette.background, accent, 0.72f)
    val header = blend(surface, accent, 0.38f)
    return listOf(
        surface,
        header,
        blend(surface, accent, 0.68f),
        contrastingText(surface, palette),
    )
}

/**
 * Keeps the timeline-specific semantic names while deriving every value from
 * the app-wide Amethyst palette. Clip roles use the global chart colors as
 * controlled accents rather than introducing another color system.
 */
internal fun timelineColorMap(palette: AmethystColorPalette): Map<ThemeToken<Color>, Color> {
    val audio = clipColorSet(palette.chart1, palette)
    val lights = clipColorSet(palette.chart2, palette)
    val midi = clipColorSet(palette.chart4, palette)
    val laneSurface = blend(palette.background, palette.secondary, 0.28f)
    val raisedSurface = blend(palette.card, palette.secondary, 0.18f)
    val selectedHeader = blend(palette.muted, palette.accent, 0.72f)

    return mapOf(
        timelineCanvas to palette.background,
        timelineShellBorder to palette.border,
        timelineRulerSurface to palette.secondary,
        timelineRulerHighlight to palette.card,
        timelineRulerAccent to palette.accent,
        timelineRulerText to palette.mutedForeground,
        timelineTickMinor to palette.border.copy(alpha = 0.72f),
        timelineTickMajor to palette.mutedForeground,
        timelineGridMinor to palette.border.copy(alpha = 0.48f),
        timelineGridMajor to palette.border.copy(alpha = 0.82f),
        timelineLaneSurface to laneSurface,
        timelineLaneSurfaceRaised to raisedSurface,
        timelineTrackHeaderSurface to palette.muted,
        timelineTrackHeaderSurfaceSelected to selectedHeader,
        timelineTrackHeaderContent to palette.foreground,
        timelineTrackHeaderContentSelected to palette.accentForeground,
        timelineTrackHeaderBorder to palette.border,
        timelineSelectionFill to palette.primary.copy(alpha = 0.22f),
        timelineSelectionStroke to palette.primary,
        timelineSelectionCursor to palette.primary,
        timelinePlayhead to palette.chart2,
        timelineAudioClipSurface to audio[0],
        timelineAudioClipHeader to audio[1],
        timelineAudioClipBorder to audio[2],
        timelineAudioClipContent to audio[3],
        timelineLightsClipSurface to lights[0],
        timelineLightsClipHeader to lights[1],
        timelineLightsClipBorder to lights[2],
        timelineLightsClipContent to lights[3],
        timelineMidiClipSurface to midi[0],
        timelineMidiClipHeader to midi[1],
        timelineMidiClipBorder to midi[2],
        timelineMidiClipContent to midi[3],
        timelineClipSelectedSurface to palette.primary,
        timelineClipSelectedHeader to blend(palette.primary, palette.primaryForeground, 0.48f),
        timelineClipSelectedBorder to palette.ring,
        timelineClipSelectedContent to palette.primaryForeground,
        timelineAutomationLaneSurface to blend(palette.background, palette.secondary, 0.42f),
        timelineAutomationLaneAccent to palette.primary,
    )
}

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
    timelineSelectionCursorWidth to 2.dp,
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
                container = Theme[colors][selectionSurface],
                content = Theme[colors][selectionForeground],
                border = Theme[colors][selectionSurface],
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
                background = Theme[colors][selectionSurface],
                header = Theme[colors][selectionSurface],
                border = Theme[colors][selectionSurface],
                content = Theme[colors][selectionForeground],
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
