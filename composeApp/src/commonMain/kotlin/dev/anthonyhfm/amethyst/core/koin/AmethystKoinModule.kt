package dev.anthonyhfm.amethyst.core.koin

import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.project.AmethystReader
import dev.anthonyhfm.amethyst.core.data.project.AmethystWriter
import dev.anthonyhfm.amethyst.core.midi.midiKoinModule
import dev.anthonyhfm.amethyst.editor.EditorViewModel
import dev.anthonyhfm.amethyst.editor.trackeditor.TrackEditorViewModel
import dev.anthonyhfm.amethyst.editor.tracks.TracksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val amethystKoinModule = module {
    includes(midiKoinModule)

    single { ProjectRepository() }

    single { AmethystReader(get()) }
    single { AmethystWriter(get()) }

    viewModel { EditorViewModel(get()) }
    viewModel { TracksViewModel(get()) }
    viewModel { TrackEditorViewModel(get()) }
}