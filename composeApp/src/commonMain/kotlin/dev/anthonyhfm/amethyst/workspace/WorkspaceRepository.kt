package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.data.settings.RecentColorRGB
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceMeta

object WorkspaceRepository {
    val deviceRefresh: MutableSharedFlow<Unit> = MutableSharedFlow()

    var lightsChain: Chain = Chain()
        private set

    var samplingChain: Chain = Chain()
        private set

    var bounds: Pair<IntOffset, IntSize> = Pair(IntOffset(0, 0), IntSize(0, 0))
        private set

    // Only keep lightweight metadata in memory instead of the full SavableWorkspaceData
    var workspaceMeta: WorkspaceMeta? = null

    private val _macros: MutableStateFlow<List<Macro>> = MutableStateFlow(listOf(Macro(1)))
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _mode: MutableStateFlow<WorkspaceContract.WorkspaceMode> = MutableStateFlow(WorkspaceContract.WorkspaceMode.Layout())
    val mode: StateFlow<WorkspaceContract.WorkspaceMode> = _mode.asStateFlow()

    private val _bpm = MutableStateFlow(120.00)
    val bpm: StateFlow<Double> = _bpm.asStateFlow()
    
    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    // Recently used colors; initialize from GlobalSettings for persistence
    private val _recentColors: MutableStateFlow<List<Triple<Float, Float, Float>>> =
        MutableStateFlow(GlobalSettings.recentColors.map { Triple(it.r, it.g, it.b) })
    val recentColors: StateFlow<List<Triple<Float, Float, Float>>> = _recentColors.asStateFlow()

    // Keep track of the previous mode
    private var previousMode: WorkspaceContract.WorkspaceMode = WorkspaceContract.WorkspaceMode.Layout()

    private val _gridType = MutableStateFlow<GridUtils.GridType>(GridUtils.GridType.Flexible.Medium)
    val gridType: StateFlow<GridUtils.GridType> = _gridType.asStateFlow()

    init {
        setupChains()
    }

    private fun setupChains() {
        lightsChain.signalExit = {
            Heaven.midiEnter(it.filterIsInstance<Signal.LED>())
        }

        samplingChain.signalExit = {
            Echo.audioEnter(it.filterIsInstance<Signal.AudioSignal>())
        }
    }

    fun switchMode(mode: WorkspaceContract.WorkspaceMode, undoable: Boolean = true) {
        val current = _mode.value
        if (current == mode) return

        if (undoable) {
            UndoManager.addAction(
                UndoableAction.WorkspaceModeChange(
                    beforeMode = current,
                    afterMode = mode
                )
            )
        }

        if (undoable && current.selectable) {
            previousMode = current
        }

        _mode.update { mode }
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

    fun updateAutoPlaySettings(showButtonPresses: Boolean, showLights: Boolean) {
        workspaceMeta?.let { currentMeta ->
            workspaceMeta = currentMeta.copy(
                settings = currentMeta.settings.copy(
                    autoPlayShowButtonPresses = showButtonPresses,
                    autoPlayShowLights = showLights
                )
            )
        }
    }

    fun setGridType(type: GridUtils.GridType) { _gridType.update { type } }

    fun setMacroValue(index: Int, macro: Macro) {
        if (index < 0 || index >= _macros.value.size) {
            println("Macro index out of bounds: $index")
        }

        _macros.update { currentMacros ->
            currentMacros.toMutableList().apply {
                this[index] = macro
            }
        }
    }

    fun addMacro(macro: Macro) {
        _macros.update { currentMacros ->
            currentMacros.toMutableList().apply {
                add(macro)
            }
        }
    }

    fun updateWorkspaceBounds() {
        if (Heaven.devices.isNotEmpty()) {
            bounds = Pair(
                first = IntOffset(
                    x = Heaven.devices.minOf { it.position.value.x.toInt() },
                    y = Heaven.devices.minOf { it.position.value.y.toInt() }
                ),
                second = IntSize(
                    width = Heaven.devices.maxOf { it.position.value.x.toInt() + it.size.width.toInt() } - Heaven.devices.minOf { it.position.value.x.toInt() },
                    height = Heaven.devices.maxOf { it.position.value.y.toInt() + it.size.height.toInt() } - Heaven.devices.minOf { it.position.value.y.toInt() }
                )
            )
        }
    }

    fun removeVirtualDevice(uuid: String) {
        Heaven.devices = Heaven.devices.filterNot { it.selectionUUID == uuid }

        runBlocking {
            deviceRefresh.emit(Unit)
        }

        updateWorkspaceBounds()
    }

    fun resetMulti() {
        fun recursiveResetMulti(chain: Chain) {
            chain.devices.value.forEach { device ->
                when (device) {
                    is MultiGroupChainDevice -> {
                        device.state.update {
                            it.copy(
                                currentMultiIndex = if (it.type == MultiGroupChainDeviceState.TYPE.BACKWARD) {
                                    it.groups.lastIndex
                                } else { 0 }
                            )
                        }
                        device.state.value.groups.forEach { group ->
                            recursiveResetMulti(group.chain)
                        }
                    }

                    is GroupChainDevice -> {
                        device.state.value.groups.forEach { group ->
                            recursiveResetMulti(group.chain)
                        }
                    }

                    is ChokeChainDevice -> {
                        recursiveResetMulti(device.state.value.chain)
                    }
                }
            }
        }

        recursiveResetMulti(lightsChain)
        recursiveResetMulti(samplingChain)
    }

    fun loadWorkspace(workspaceData: SavableWorkspaceData) {
        // Store only metadata in memory
        workspaceMeta = WorkspaceMeta(
            path = workspaceData.path,
            title = workspaceData.title,
            author = workspaceData.author,
            settings = workspaceData.settings,
            autoPlay = workspaceData.autoPlay
        )

        lightsChain = workspaceData.lights.unpack()
        samplingChain = workspaceData.sampling.unpack()

        lightsChain.signalExit = {
            Heaven.midiEnter(it.filterIsInstance<Signal.LED>())
        }

        samplingChain.signalExit = {
            Echo.audioEnter(it.filterIsInstance<Signal.AudioSignal>())
        }

        fun recursiveRenderingKeyframes(chain: Chain) {
            chain.devices.value.forEach { device ->
                when (device) {
                    is KeyframesChainDevice -> {
                        device.renderAnimation()
                    }

                    is GroupChainDevice -> {
                        device.state.value.groups.forEach {
                            recursiveRenderingKeyframes(it.chain)
                        }
                    }

                    is MultiGroupChainDevice -> {
                        device.state.value.groups.forEach {
                            recursiveRenderingKeyframes(it.chain)
                        }
                    }

                    is ChokeChainDevice -> {
                        recursiveRenderingKeyframes(device.state.value.chain)
                    }
                }
            }
        }

        recursiveRenderingKeyframes(lightsChain)

        _macros.update { workspaceData.macros }

        Heaven.devices = workspaceData.launchpadDevices.map { savedDevice ->
            val device = when (savedDevice.type) {
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO -> ViewportLaunchpadPro()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO_MK3 -> ViewportLaunchpadProMk3()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_X -> ViewportLaunchpadX()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_MK2 -> ViewportLaunchpadMk2()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MYSTRIX -> ViewportMystrix()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MIDIFIGHTER64 -> ViewportMidiFighter64()
            }

            device.apply { position.value = Offset(savedDevice.positionX, savedDevice.positionY) }
        }

        if (Heaven.devices.isNotEmpty()) {
            updateWorkspaceBounds()
        }

        _bpm.update {
            workspaceData.settings.bpm
        }
        
        _projectName.update {
            workspaceData.title
        }

        _mode.update {
            WorkspaceContract.WorkspaceMode.Preview()
        }
    }

    private fun buildWorkspaceData(): SavableWorkspaceData {
        return SavableWorkspaceData(
            path = workspaceMeta?.path,
            title = workspaceMeta?.title ?: "Untitled",
            author = workspaceMeta?.author ?: "Unknown Author",
            lights = StateChain.pack(lightsChain),
            sampling = StateChain.pack(samplingChain),
            autoPlay = workspaceMeta?.autoPlay ?: AutoPlayData(emptyMap()),
            timelineData = TimelineRepository.tracks.value,
            macros = _macros.value,
            settings = WorkspaceSettings(
                bpm = _bpm.value,
                autoPlayShowButtonPresses = workspaceMeta?.settings?.autoPlayShowButtonPresses ?: true,
                autoPlayShowLights = workspaceMeta?.settings?.autoPlayShowLights ?: true
            ),
            launchpadDevices = Heaven.devices.map { device ->
                SavableWorkspaceData.SavableViewportLaunchpad(
                    type = when (device) {
                        is ViewportLaunchpadPro -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                        is ViewportLaunchpadProMk3 -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO_MK3
                        is ViewportLaunchpadX -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_X
                        is ViewportLaunchpadMk2 -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_MK2
                        is ViewportMystrix -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MYSTRIX
                        is ViewportMidiFighter64 -> SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MIDIFIGHTER64
                        else -> { TODO("Could not serialize virtual launchpad element for the workspace") }
                    },
                    positionX = device.position.value.x,
                    positionY = device.position.value.y
                )
            },
        )
    }

    fun saveWorkspace(): SavableWorkspaceData {
        // Build and return current state; metadata remains lightweight
        return buildWorkspaceData()
    }

    fun addRecentColor(color: Triple<Float, Float, Float>, maxSize: Int = 24) {
        _recentColors.update { current ->
            // skip if the color is already the most recent one we added
            if (current.isNotEmpty() && current.first() == color) return@update current

            val newList = buildList {
                add(color)
                addAll(current.filterNot { it == color })
            }.take(maxSize)
            GlobalSettings.recentColors = newList.map { RecentColorRGB(it.first, it.second, it.third) }
            newList
        }
    }

    fun hasUnsavedChanges(): Boolean {
        // Always show the Unsaved Changes dialog when attempting to close
        // This avoids file system access (PlatformFile) on mobile platforms
        return true
    }

    fun clean() {
        // Reset chains
        lightsChain = Chain()
        samplingChain = Chain()
        
        // Re-setup signal exits
        setupChains()
        
        // Clear devices
        Heaven.devices = emptyList()
        
        // Reset state
        bounds = Pair(IntOffset(0, 0), IntSize(0, 0))
        workspaceMeta = null
        _macros.update { listOf(Macro(1)) }
        _mode.update { WorkspaceContract.WorkspaceMode.Layout() }
        _bpm.update { 120.00 }
        _projectName.update { null }
        previousMode = WorkspaceContract.WorkspaceMode.Layout()
        _gridType.update { GridUtils.GridType.Flexible.Medium }
    }
}