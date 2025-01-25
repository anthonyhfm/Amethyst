package dev.anthonyhfm.amethyst.core.koin

import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.midi.midiKoinModule
import dev.anthonyhfm.amethyst.editor.EditorViewModel
import dev.anthonyhfm.amethyst.editor.ui.trackeditor.TrackEditorViewModel
import dev.anthonyhfm.amethyst.editor.ui.tracks.TracksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val amethystKoinModule = module {
    includes(midiKoinModule)

    single { ProjectRepository() }

    viewModel { EditorViewModel(get(), get()) }
    viewModel { TracksViewModel(get()) }
    viewModel { TrackEditorViewModel(get()) }
}