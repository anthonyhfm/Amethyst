package dev.anthonyhfm.amethyst.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WorkspaceRepository {
    private val _mode: MutableStateFlow<WorkspaceContract.WorkspaceMode> = MutableStateFlow(WorkspaceContract.WorkspaceMode.Layout())
    val mode: StateFlow<WorkspaceContract.WorkspaceMode> = _mode.asStateFlow()

    private val _bpm = MutableStateFlow(120.00)
    val bpm: StateFlow<Double> = _bpm.asStateFlow()

    // Keep track of the previous mode
    private var previousMode: WorkspaceContract.WorkspaceMode = WorkspaceContract.WorkspaceMode.Layout()

    fun switchMode(mode: WorkspaceContract.WorkspaceMode) {
        // Store the current mode as the previous mode before switching
        previousMode = _mode.value

        _mode.update {
            mode
        }
    }

    // Switch back to the previous mode
    fun switchToPreviousMode() {
        _mode.update {
            previousMode
        }
    }

    fun setBpm(bpm: Double) {
        _bpm.update {
            bpm
        }
    }
}
