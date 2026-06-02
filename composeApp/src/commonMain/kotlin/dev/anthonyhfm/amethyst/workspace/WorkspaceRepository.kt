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
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
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
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceMeta
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

object WorkspaceRepository {
    private fun Throwable.isRecoverablePlatformInitFailure(): Boolean {
        val typeName = this::class.simpleName.orEmpty()
        return this is IllegalStateException ||
            this is NullPointerException ||
            typeName == "ExceptionInInitializerError" ||
            typeName == "NoClassDefFoundError"
    }

    private fun recentColorsPersistenceUnavailable(exception: Throwable) {
        println(
            "WorkspaceRepository: recent color persistence unavailable; using in-memory state only (${exception.message ?: exception::class.simpleName})"
        )
    }

    private fun loadPersistedRecentColors(): List<Triple<Float, Float, Float>> {
        return try {
            GlobalSettings.recentColors.map { Triple(it.r, it.g, it.b) }
        } catch (exception: Throwable) {
            if (!exception.isRecoverablePlatformInitFailure()) throw exception
            recentColorsPersistenceUnavailable(exception)
            emptyList()
        }
    }

    private fun persistRecentColors(colors: List<Triple<Float, Float, Float>>) {
        try {
            GlobalSettings.recentColors = colors.map { RecentColorRGB(it.first, it.second, it.third) }
        } catch (exception: Throwable) {
            if (!exception.isRecoverablePlatformInitFailure()) throw exception
            recentColorsPersistenceUnavailable(exception)
        }
    }

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

    var isInputFocused: Boolean = false
    
    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    // Recently used colors; initialize from GlobalSettings for persistence
    private val _recentColors: MutableStateFlow<List<Triple<Float, Float, Float>>> =
        MutableStateFlow(loadPersistedRecentColors())
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

    /**
     * When [fromRemote] is true the change arrived via the network and must not be re-broadcast.
     * The [WorkspaceEventBroadcaster] reads this flag to suppress the next identical emission.
     */
    @Volatile var isApplyingRemoteUpdate: Boolean = false
        private set

    fun markRemoteUpdateConsumed() {
        isApplyingRemoteUpdate = false
    }

    fun setBpm(bpm: Double, fromRemote: Boolean = false, undoable: Boolean = true) {
        val before = _bpm.value
        if (undoable && !fromRemote && before != bpm) {
            UndoManager.addAction(
                UndoableAction.WorkspaceBpmChange(
                    beforeBpm = before,
                    afterBpm = bpm
                )
            )
        }
        isApplyingRemoteUpdate = fromRemote
        _bpm.update { bpm }
        if (!fromRemote) isApplyingRemoteUpdate = false
    }

    fun setProjectName(name: String, fromRemote: Boolean = false) {
        isApplyingRemoteUpdate = fromRemote
        _projectName.update { name }
        if (!fromRemote) isApplyingRemoteUpdate = false
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

    fun setGridType(type: GridUtils.GridType, fromRemote: Boolean = false) {
        isApplyingRemoteUpdate = fromRemote
        _gridType.update { type }
        if (!fromRemote) isApplyingRemoteUpdate = false
    }

    fun setMacroValue(index: Int, macro: Macro, fromRemote: Boolean = false, undoable: Boolean = true) {
        if (index < 0 || index >= _macros.value.size) {
            println("Macro index out of bounds: $index")
            return
        }

        val before = _macros.value
        val after = before.toMutableList().apply {
            this[index] = macro
        }
        if (undoable && !fromRemote && before != after) {
            UndoManager.addAction(
                UndoableAction.WorkspaceMacrosChange(
                    beforeMacros = before,
                    afterMacros = after
                )
            )
        }
        isApplyingRemoteUpdate = fromRemote
        _macros.update { after }
        if (!fromRemote) isApplyingRemoteUpdate = false
    }

    /**
     * Replaces the entire macro list at once.
     * Used by [WorkspaceEventReceiver] to apply a remote [ConnectEvent.MacrosChanged] atomically.
     */
    fun setMacros(macros: List<Macro>, fromRemote: Boolean = false, undoable: Boolean = true) {
        val before = _macros.value
        if (undoable && !fromRemote && before != macros) {
            UndoManager.addAction(
                UndoableAction.WorkspaceMacrosChange(
                    beforeMacros = before,
                    afterMacros = macros
                )
            )
        }
        isApplyingRemoteUpdate = fromRemote
        _macros.update { macros }
        if (!fromRemote) isApplyingRemoteUpdate = false
    }

    /**
     * Synchronizes only the addition and removal of macros from a remote update,
     * preserving the local macro values of existing macros.
     */
    fun syncMacrosSize(remoteMacros: List<Macro>, fromRemote: Boolean = true) {
        val current = _macros.value
        if (current.size == remoteMacros.size) return

        val newMacros = if (remoteMacros.size > current.size) {
            current + remoteMacros.drop(current.size)
        } else {
            current.take(remoteMacros.size)
        }
        setMacros(newMacros, fromRemote = fromRemote, undoable = false)
    }

    fun addMacro(macro: Macro) {
        setMacros(_macros.value + macro)
    }

    fun removeMacro(index: Int) {
        if (index < 0 || index >= _macros.value.size) {
            println("Macro index out of bounds: $index")
            return
        }
        setMacros(_macros.value.toMutableList().apply { removeAt(index) })
    }

    suspend fun addVirtualDevice(
        element: LaunchpadViewportElement,
        fromRemote: Boolean = false
    ): Boolean {
        if (Heaven.devices.any { it.launchpadId == element.launchpadId }) return false

        Heaven.devices = Heaven.devices + element
        if (!fromRemote) {
            DeviceSyncCoordinator.onDevicePlaced(element)
        }
        deviceRefresh.emit(Unit)
        updateWorkspaceBounds()
        return true
    }

    suspend fun removeVirtualDeviceById(
        uuid: String,
        fromRemote: Boolean = false
    ): Boolean {
        val element = Heaven.devices.firstOrNull { it.selectionUUID == uuid || it.launchpadId == uuid }
            ?: return false

        element.deviceConfig.launchpadDevice?.midiOutput?.close()
        if (!fromRemote) {
            DeviceSyncCoordinator.onDeviceRemoved(element.launchpadId)
        }

        Heaven.devices = Heaven.devices.filterNot { it.selectionUUID == uuid || it.launchpadId == uuid }
        deviceRefresh.emit(Unit)
        updateWorkspaceBounds()
        return true
    }

    suspend fun moveVirtualDevice(
        deviceId: String,
        position: Offset,
        fromRemote: Boolean = false
    ): Boolean {
        val element = Heaven.devices.firstOrNull { it.launchpadId == deviceId || it.selectionUUID == deviceId }
            ?: return false

        element.position.value = position
        if (!fromRemote) {
            DeviceSyncCoordinator.onDeviceMoved(element)
        }
        deviceRefresh.emit(Unit)
        updateWorkspaceBounds()
        return true
    }

    suspend fun updateVirtualDeviceRotation(
        deviceId: String,
        rotationDegrees: Float,
        fromRemote: Boolean = false
    ): Boolean {
        val element = Heaven.devices.firstOrNull { it.launchpadId == deviceId || it.selectionUUID == deviceId }
            ?: return false

        element.rotationDegrees.floatValue = rotationDegrees
        if (!fromRemote) {
            DeviceSyncCoordinator.onDeviceRotationChanged(element)
        }
        deviceRefresh.emit(Unit)
        return true
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
        runBlocking {
            removeVirtualDeviceById(uuid)
        }
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
        AutomappingManager.reset()

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

        TimelineRepository.loadTracks(workspaceData.timelineData)
        AudioSourceLibrary.load(workspaceData.audioSources)
        migrateAudioEntries()

        Heaven.devices = workspaceData.launchpadDevices.map { savedDevice ->
            val device = when (savedDevice) {
                is SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro -> ViewportLaunchpadPro()
                is SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadIdealised -> ViewportLaunchpadIdealised()
                is SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadProMk3 -> ViewportLaunchpadProMk3()
                is SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadX -> ViewportLaunchpadX()
                is SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadMk2 -> ViewportLaunchpadMk2()
                is SavableWorkspaceData.SavableViewportLaunchpad.Mystrix -> ViewportMystrix()
                is SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64 -> ViewportMidiFighter64(initialStyle = savedDevice.style)
            }

            device.apply {
                position.value = Offset(savedDevice.positionX, savedDevice.positionY)
                rotationDegrees.floatValue = savedDevice.rotationDegrees
                if (savedDevice.id.isNotEmpty()) launchpadId = savedDevice.id
            }
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
            WorkspaceContract.WorkspaceMode.Performance()
        }
    }

    /**
     * Migrates legacy AudioEntry objects that still carry inline rawData (old format).
     * Creates an AudioSource for each such entry and updates the entry in-place.
     */
    private fun migrateAudioEntries() {
        val tracks = TimelineRepository.tracks.value
        tracks.filterIsInstance<AudioTimelineTrack>().forEach { track ->
            val migratedEntries = track.entries.mapValues { (_, entry) ->
                val legacy = entry.legacyRawData
                if (entry.sourceId.isEmpty() && legacy != null) {
                    val source = AudioSource(
                        id = UUID.randomUUID(),
                        fileName = entry.fileName,
                        rawData = legacy,
                        sampleRate = entry.sampleRate,
                        channels = entry.channels,
                        bitDepth = entry.bitDepth
                    )
                    AudioSourceLibrary.add(source)
                    val startSample = if (entry.legacySourceStartMs > 0) {
                        entry.legacySourceStartMs * entry.sampleRate / 1000L
                    } else 0L
                    val endSample = if (entry.legacySourceDurationMs > 0) {
                        startSample + (entry.legacySourceDurationMs * entry.sampleRate / 1000L)
                    } else source.totalSamples
                    entry.copy(
                        sourceId = source.id,
                        clipStartSample = startSample,
                        clipEndSample = endSample.coerceAtMost(source.totalSamples),
                        legacyRawData = null,
                        startTimeUs = dev.anthonyhfm.amethyst.timeline.data.msToUs(entry.startTimeMs),
                        durationUs = dev.anthonyhfm.amethyst.timeline.data.samplesToUs(
                            (endSample.coerceAtMost(source.totalSamples) - startSample).coerceAtLeast(0L),
                            entry.sampleRate
                        )
                    )
                } else {
                    entry
                }
            }
            track.entries.clear()
            track.entries.putAll(migratedEntries)
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
                when (device) {
                    is ViewportLaunchpadPro -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportLaunchpadIdealised -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadIdealised(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportLaunchpadProMk3 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadProMk3(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportLaunchpadX -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadX(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportLaunchpadMk2 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadMk2(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportMystrix -> SavableWorkspaceData.SavableViewportLaunchpad.Mystrix(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue
                    )
                    is ViewportMidiFighter64 -> SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        style = device.style
                    )
                    else -> { TODO("Could not serialize virtual launchpad element for the workspace") }
                }
            },
            audioSources = AudioSourceLibrary.all(),
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
            persistRecentColors(newList)
            newList
        }
    }

    fun hasUnsavedChanges(): Boolean {
        // Always show the Unsaved Changes dialog when attempting to close
        // This avoids file system access (PlatformFile) on mobile platforms
        return true
    }

    fun clean() {
        TransmitChainDevice.clearReceiversForTesting()
        AutomappingManager.reset()

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

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun getVerificationHash(): Int {
        val data = saveWorkspace()
        val hashableData = data.copy(
            macros = data.macros.map { Macro(0) }
        )
        val bytes = dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf.encodeToByteArray(
            SavableWorkspaceData.serializer(),
            hashableData
        )
        return bytes.contentHashCode()
    }

    private fun chainContainsChain(root: Chain, target: Chain): Boolean {
        if (root === target) return true

        return root.devices.value.any { device ->
            when (device) {
                is GroupChainDevice -> device.state.value.groups.any { group ->
                    chainContainsChain(group.chain, target)
                }

                is MultiGroupChainDevice -> device.state.value.groups.any { group ->
                    chainContainsChain(group.chain, target)
                }

                is ChokeChainDevice -> chainContainsChain(device.state.value.chain, target)
                else -> false
            }
        }
    }

    private fun chainContainsDevice(root: Chain, deviceSelectionUUID: String): Boolean {
        if (root.devices.value.any { it.selectionUUID == deviceSelectionUUID }) return true

        return root.devices.value.any { device ->
            when (device) {
                is GroupChainDevice -> device.state.value.groups.any { group ->
                    chainContainsDevice(group.chain, deviceSelectionUUID)
                }

                is MultiGroupChainDevice -> device.state.value.groups.any { group ->
                    chainContainsDevice(group.chain, deviceSelectionUUID)
                }

                is ChokeChainDevice -> chainContainsDevice(device.state.value.chain, deviceSelectionUUID)
                else -> false
            }
        }
    }
}
