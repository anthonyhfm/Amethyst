package dev.anthonyhfm.amethyst.workspace

import dev.anthonyhfm.amethyst.workspace.chain.WorkspaceChain
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WorkspaceRepository {
    var lightsChain: WorkspaceChain = WorkspaceChain()
        private set

    var samplingChain: WorkspaceChain = WorkspaceChain(isSampling = true)
        private set

    private var saveableWorkspaceData: SaveableWorkspaceData? = null

    private val _mode: MutableStateFlow<WorkspaceContract.WorkspaceMode> = MutableStateFlow(WorkspaceContract.WorkspaceMode.Layout())
    val mode: StateFlow<WorkspaceContract.WorkspaceMode> = _mode.asStateFlow()

    private val _bpm = MutableStateFlow(120.00)
    val bpm: StateFlow<Double> = _bpm.asStateFlow()

    private val _selectionUUID: MutableStateFlow<String?> = MutableStateFlow(null)
    val selectionUUID: StateFlow<String?> = _selectionUUID.asStateFlow()

    // Keep track of the previous mode
    private var previousMode: WorkspaceContract.WorkspaceMode = WorkspaceContract.WorkspaceMode.Layout()

    fun switchMode(mode: WorkspaceContract.WorkspaceMode) {
        previousMode = _mode.value

        _mode.update {
            mode
        }
    }

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

    fun setSelection(uuid: String) {
        _selectionUUID.update {
            uuid
        }
    }

    fun loadWorkspace(workspaceData: SaveableWorkspaceData) {
        saveableWorkspaceData = workspaceData

        lightsChain.heavenChain = workspaceData.lights.unpack()
        samplingChain.heavenChain = workspaceData.sampling.unpack()

        _bpm.update {
            workspaceData.settings.bpm
        }

        _mode.update {
            WorkspaceContract.WorkspaceMode.Preview()
        }
    }

    fun saveWorkspace(): SaveableWorkspaceData {
        return SaveableWorkspaceData(
            title = saveableWorkspaceData?.title ?: "Untitled Project",
            lights = StateChain.pack(lightsChain.heavenChain),
            sampling = StateChain.pack(lightsChain.heavenChain),
            settings = WorkspaceSettings(
                bpm = _bpm.value
            ),
            path = saveableWorkspaceData?.path,
        ).also { saveableWorkspaceData = it }
    }
}

