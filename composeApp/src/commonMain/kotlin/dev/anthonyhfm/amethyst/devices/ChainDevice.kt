package dev.anthonyhfm.amethyst.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.heaven.elements.SignalReceiver
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

abstract class ChainDevice <State : @Serializable DeviceState> : SignalReceiver(), Selectable {
    override val selectionUUID: String = UUID.randomUUID()

    abstract val state: MutableStateFlow<State>

    val isDragging: MutableState<Boolean> = mutableStateOf(false)

    @Composable
    abstract fun Content()

    abstract override fun midiEnter(n: List<Signal>)
}