package dev.anthonyhfm.amethyst.home

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object HomeCommandSurface {
    sealed interface HomeCommand {
        data object NewProject : HomeCommand
        data object OpenProject : HomeCommand
    }

    private val _commands = MutableSharedFlow<HomeCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<HomeCommand> = _commands.asSharedFlow()

    fun emit(command: HomeCommand) {
        _commands.tryEmit(command)
    }
}
