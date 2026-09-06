package dev.anthonyhfm.amethyst.workspace.audio

import androidx.compose.runtime.compositionLocalOf
import com.mohamedrejeb.compose.dnd.DragAndDropState
import dev.anthonyhfm.amethyst.timeline.data.AudioSource

val LocalAudioLibraryDragAndDropState =
    compositionLocalOf<DragAndDropState<AudioSource>?> { null }
