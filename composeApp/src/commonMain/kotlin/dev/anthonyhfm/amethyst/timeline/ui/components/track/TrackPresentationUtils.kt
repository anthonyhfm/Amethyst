package dev.anthonyhfm.amethyst.timeline.ui.components.track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground

data class TrackPresentation(
    val defaultName: String,
    val label: String,
    val icon: ImageVector,
    val role: TimelineClipRole,
)

fun TimelineTrack<*>.displayName(
    trackIndex: Int,
    allTracks: List<TimelineTrack<*>>
): String = name.takeIf { it.isNotBlank() } ?: presentation(trackIndex, allTracks).defaultName

fun TimelineTrack<*>.presentation(
    trackIndex: Int,
    allTracks: List<TimelineTrack<*>>
): TrackPresentation =
    when (this) {
        is AudioTimelineTrack -> TrackPresentation(
            defaultName = "Audio Track ${trackIndex + 1}",
            label = "Audio",
            icon = Icons.Default.Audiotrack,
            role = TimelineClipRole.Audio,
        )

        is MidiTimelineTrack -> TrackPresentation(
            defaultName = "Midi Track ${trackIndex + 1}",
            label = "Midi",
            icon = Icons.Default.Lightbulb,
            role = TimelineClipRole.Midi,
        )

        else -> TrackPresentation(
            defaultName = "Track ${trackIndex + 1}",
            label = "Track",
            icon = Icons.Default.Lightbulb,
            role = TimelineClipRole.Midi,
        )
    }

@Composable
fun Color.contrastForeground(): Color =
    if (((red * 0.2126f) + (green * 0.7152f) + (blue * 0.0722f)) > 0.45f) {
        TimelineTheme.palette.canvas
    } else {
        Theme[colors][foreground].copy(alpha = 0.96f)
    }
