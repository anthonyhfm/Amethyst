package dev.anthonyhfm.amethyst.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.SignalReceiver
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

abstract class GenericChainDevice <State : @Serializable DeviceState> : SignalReceiver(), Selectable {
    override var selectionUUID: String = UUID.randomUUID()

    abstract val state: MutableStateFlow<State>

    open val helpRef: String? = null

    var isDragging: MutableState<Boolean> = mutableStateOf(false)

    open fun onAddedToChain() = Unit

    open fun onAddedToChain(parentChain: Chain) {
        onAddedToChain()
    }

    open fun onRemovedFromChain() = Unit

    open fun onStateRestored() = Unit

    @Composable
    abstract fun Content()

    abstract override fun signalEnter(n: List<Signal>)

    protected fun pushStateChange(before: State, after: State) {
        if (before != after) {
            UndoManager.addAction(
                UndoableAction.ChangeDeviceState(
                    device = this,
                    beforeState = before,
                    afterState = after
                )
            )

            ChainSyncCoordinator.onDeviceStateChanged(this, after)
        }
    }
}

abstract class LEDChainDevice <State : @Serializable DeviceState> : GenericChainDevice<State>() {
    @Composable
    abstract override fun Content()

    abstract fun ledSignalEnter(n: List<Signal.LED>)

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.LED>().let {
            if (it.isNotEmpty()) {
                ledSignalEnter(it)
            }
        }
    }
}

abstract class AudioChainDevice <State : @Serializable DeviceState> : GenericChainDevice<State>() {
    @Composable
    abstract override fun Content()

    abstract override fun signalEnter(n: List<Signal>)
}
