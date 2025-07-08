package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.core.audio.AudioPlayer
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
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

    val audioRegistry: MutableMap<String, AudioClip> = mutableMapOf()

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

        _selectionUUID.update { null }

        _mode.update {
            mode
        }
    }

    fun switchToPreviousMode() {
        _selectionUUID.update { null }

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

        workspaceData.audioClips.forEach {
            AudioPlayer.preloadFromAudioClip(it)
        }

        lightsChain.heavenChain = workspaceData.lights.unpack()
        samplingChain.heavenChain = workspaceData.sampling.unpack()

        Heaven.devices = workspaceData.launchpadDevices.map { savedDevice ->
            val device = when (savedDevice.type) {
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO -> ViewportLaunchpadPro()
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO_MK3 -> ViewportLaunchpadProMk3()
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_X -> ViewportLaunchpadX()
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_MK2 -> ViewportLaunchpadMk2()
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MYSTRIX -> ViewportMystrix()
                SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MIDIFIGHTER64 -> ViewportMidiFighter64()
            }

            device.apply { position.value = Offset(savedDevice.positionX, savedDevice.positionY) }
        }

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
            sampling = StateChain.pack(samplingChain.heavenChain),
            settings = WorkspaceSettings(
                bpm = _bpm.value
            ),
            path = saveableWorkspaceData?.path,
            launchpadDevices = Heaven.devices.map { device ->
                SaveableWorkspaceData.SavableViewportLaunchpad(
                    type = when (device) {
                        is ViewportLaunchpadPro -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                        is ViewportLaunchpadProMk3 -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO_MK3
                        is ViewportLaunchpadX -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_X
                        is ViewportLaunchpadMk2 -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_MK2
                        is ViewportMystrix -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MYSTRIX
                        is ViewportMidiFighter64 -> SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MIDIFIGHTER64
                        else -> { TODO("Could not serialize virtual launchpad element for the workspace") }
                    },
                    positionX = device.position.value.x,
                    positionY = device.position.value.y
                )
            },
            audioClips = audioRegistry.values.map { it }
        ).also { saveableWorkspaceData = it }
    }
}

