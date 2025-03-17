package dev.anthonyhfm.amethyst.core.koin

import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.project.AmethystReader
import dev.anthonyhfm.amethyst.core.data.project.AmethystWriter
import dev.anthonyhfm.amethyst.core.midi.midiKoinModule
import dev.anthonyhfm.amethyst.workspace.WorkspaceController
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val amethystKoinModule = module {
    includes(midiKoinModule)

    single { ProjectRepository() }

    single { AmethystReader(get()) }
    single { AmethystWriter(get()) }

    single { WorkspaceController() }

    viewModel { WorkspaceViewModel(get(), get()) }
}