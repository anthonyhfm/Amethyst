package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.resolvedRawData
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import dev.anthonyhfm.amethyst.workspace.data.mergeMacroStructure
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
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import kotlin.concurrent.Volatile
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    val deviceRefresh: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)

    var lightsChain: Chain = Chain()
        private set

    var samplingChain: AudioChain = AudioChain()
        private set

    var bounds: Pair<IntOffset, IntSize> by mutableStateOf(IntOffset(0, 0) to IntSize(0, 0))
        private set

    // Only keep lightweight metadata in memory instead of the full SavableWorkspaceData
    var workspaceMeta: WorkspaceMeta? = null

    private val _macros: MutableStateFlow<List<Macro>> = MutableStateFlow(listOf(Macro(1)))
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _parameterMappings = MutableStateFlow<List<ParameterMapping>>(emptyList())
    val parameterMappings: StateFlow<List<ParameterMapping>> = _parameterMappings.asStateFlow()

    private val _mode: MutableStateFlow<WorkspaceMode> = MutableStateFlow(LayoutWorkspaceMode())
    val mode: StateFlow<WorkspaceMode> = _mode.asStateFlow()

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
    private var previousMode: WorkspaceMode = LayoutWorkspaceMode()

    private val _gridType = MutableStateFlow<GridUtils.GridType>(GridUtils.GridType.Flexible.Medium)
    val gridType: StateFlow<GridUtils.GridType> = _gridType.asStateFlow()

    private val _showDeviceConfigurator = MutableStateFlow<String?>(null)
    val showDeviceConfigurator: StateFlow<String?> = _showDeviceConfigurator.asStateFlow()

    private val _showDevicePicker = MutableStateFlow(false)
    val showDevicePicker: StateFlow<Boolean> = _showDevicePicker.asStateFlow()

    fun openDeviceConfigurator(uuid: String) {
        if (_mode.value is LayoutWorkspaceMode) {
            _showDeviceConfigurator.update { uuid }
        }
    }

    fun closeDeviceConfigurator() {
        _showDeviceConfigurator.update { null }
    }

    fun openDevicePicker() {
        _showDevicePicker.update { true }
    }

    fun closeDevicePicker() {
        _showDevicePicker.update { false }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val midiManager = AmethystMidiManager()

    init {
        setupChains()
        setupMidiAutoDetect()
        setupModeTransitionObserver()
        setupCollaborationPresence()
    }

    private fun setupMidiAutoDetect() {
        midiManager.startAutoDetectLoop()
        repositoryScope.launch {
            ViewportRepository.devices.collect {
                midiManager.refreshConnections()
            }
        }
    }

    fun changeMidiDeviceConfig(uuid: String, deviceId: String?) {
        midiManager.changeDeviceConfig(uuid, deviceId)
    }

    private fun setupModeTransitionObserver() {
        repositoryScope.launch {
            mode.collect { newMode ->
                when (newMode) {
                    is KeyframesWorkspaceMode -> {
                        Heaven.clear()
                        newMode.wake()
                    }
                    is CoordinateFilterWorkspaceMode -> {
                        Heaven.clear()
                        newMode.wake()
                    }
                    else -> { /* cleanup is handled in replaceMode */ }
                }
            }
        }
    }

    private fun setupCollaborationPresence() {
        repositoryScope.launch {
            SelectionManager.selections
                .map { selections -> selections.firstOrNull()?.selectionUUID }
                .distinctUntilChanged()
                .collect { focusedElementId ->
                    CollaborationPresence.sendFocusedElement(focusedElementId)
                }
        }
    }

    private fun setupChains() {
        lightsChain.signalExit = {
            Heaven.midiEnter(it.filterIsInstance<Signal.LED>())
        }
        Echo.attachAudioChain(samplingChain)
    }

    fun switchMode(mode: WorkspaceMode, undoable: Boolean = true) {
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

        if (undoable && current.selectableMode) {
            previousMode = current
        }

        replaceMode(mode)
    }

    fun switchToPreviousMode() {
        replaceMode(previousMode)
    }

    private fun replaceMode(mode: WorkspaceMode) {
        val current = _mode.value
        if (current == mode) return

        if (current is CoordinateFilterWorkspaceMode) {
            Heaven.clear()
            current.close()
        }

        if (current is KeyframesWorkspaceMode) {
            Heaven.clear()
            current.close()
        }

        if (current is LayoutWorkspaceMode) {
            updateWorkspaceBounds()
            if (SelectionManager.selections.value.any { it is Selectable.VirtualViewportDevice }) {
                SelectionManager.clear()
            }
        }

        current.onDeactivate()
        _mode.update { mode }
        mode.onActivate()
    }

    @Volatile var isApplyingRemoteBpmUpdate: Boolean = false
        private set
    @Volatile var isApplyingRemoteProjectNameUpdate: Boolean = false
        private set
    @Volatile var isApplyingRemoteMacrosUpdate: Boolean = false
        private set
    @Volatile var isApplyingRemoteParameterMappingsUpdate: Boolean = false
        private set
    @Volatile var isApplyingRemoteGridTypeUpdate: Boolean = false
        private set

    fun markRemoteBpmUpdateConsumed() { isApplyingRemoteBpmUpdate = false }
    fun markRemoteProjectNameUpdateConsumed() { isApplyingRemoteProjectNameUpdate = false }
    fun markRemoteMacrosUpdateConsumed() { isApplyingRemoteMacrosUpdate = false }
    fun markRemoteParameterMappingsUpdateConsumed() { isApplyingRemoteParameterMappingsUpdate = false }
    fun markRemoteGridTypeUpdateConsumed() { isApplyingRemoteGridTypeUpdate = false }

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
        isApplyingRemoteBpmUpdate = fromRemote
        _bpm.update { bpm }
        if (!fromRemote) isApplyingRemoteBpmUpdate = false
    }

    fun setProjectName(name: String, fromRemote: Boolean = false) {
        isApplyingRemoteProjectNameUpdate = fromRemote
        workspaceMeta = workspaceMeta?.copy(title = name)
        _projectName.update { name }
        if (!fromRemote) isApplyingRemoteProjectNameUpdate = false
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
        isApplyingRemoteGridTypeUpdate = fromRemote
        _gridType.update { type }
        if (!fromRemote) isApplyingRemoteGridTypeUpdate = false
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
        isApplyingRemoteMacrosUpdate = fromRemote
        _macros.update { after }
        if (!fromRemote) isApplyingRemoteMacrosUpdate = false
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
        isApplyingRemoteMacrosUpdate = fromRemote
        _macros.update { macros }
        if (!fromRemote) isApplyingRemoteMacrosUpdate = false
    }

    /**
     * Synchronizes only the addition and removal of macros from a remote update,
     * preserving the local macro values of existing macros.
     */
    fun syncMacrosSize(remoteMacros: List<Macro>, fromRemote: Boolean = true) {
        val current = _macros.value
        val newMacros = mergeMacroStructure(current, remoteMacros)
        if (current == newMacros) {
            if (fromRemote) isApplyingRemoteMacrosUpdate = false
            return
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
        val beforeMacros = _macros.value
        val beforeMappings = _parameterMappings.value
        val removedId = beforeMacros[index].id
        val afterMacros = beforeMacros.toMutableList().apply { removeAt(index) }
        val afterMappings = beforeMappings.filterNot { it.macroId == removedId }
        UndoManager.addAction(
            UndoableAction.WorkspaceMacroRemoval(
                beforeMacros = beforeMacros,
                afterMacros = afterMacros,
                beforeMappings = beforeMappings,
                afterMappings = afterMappings,
            ),
        )
        setMacros(afterMacros, undoable = false)
        setParameterMappings(afterMappings, undoable = false)
    }

    fun affectedMappingsForMacro(macroId: String): Int =
        _parameterMappings.value.count { it.macroId == macroId }

    fun setParameterMappings(
        mappings: List<ParameterMapping>,
        fromRemote: Boolean = false,
        undoable: Boolean = true,
    ) {
        val orderedMappings = mappings.sortedBy(ParameterMapping::id)
        val before = _parameterMappings.value
        if (before == orderedMappings) {
            if (fromRemote) isApplyingRemoteParameterMappingsUpdate = false
            return
        }
        if (undoable && !fromRemote) {
            UndoManager.addAction(
                UndoableAction.WorkspaceParameterMappingsChange(
                    beforeMappings = before,
                    afterMappings = orderedMappings,
                ),
            )
        }
        isApplyingRemoteParameterMappingsUpdate = fromRemote
        _parameterMappings.value = orderedMappings
        if (!fromRemote) isApplyingRemoteParameterMappingsUpdate = false
    }

    suspend fun addVirtualDevice(
        element: LaunchpadViewportElement,
        fromRemote: Boolean = false
    ): Boolean {
        if (ViewportRepository.devices.value.any { it.launchpadId == element.launchpadId }) return false

        ViewportRepository.addDevice(element)
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
        val element = ViewportRepository.devices.value.firstOrNull { it.selectionUUID == uuid || it.launchpadId == uuid }
            ?: return false

        midiManager.detachElement(element)
        element.close()
        if (!fromRemote) {
            DeviceSyncCoordinator.onDeviceRemoved(element.launchpadId)
        }

        ViewportRepository.removeDevice(uuid)
        deviceRefresh.emit(Unit)
        updateWorkspaceBounds()
        return true
    }

    suspend fun moveVirtualDevice(
        deviceId: String,
        position: Offset,
        fromRemote: Boolean = false
    ): Boolean {
        val element = ViewportRepository.devices.value.firstOrNull { it.launchpadId == deviceId || it.selectionUUID == deviceId }
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
        val element = ViewportRepository.devices.value.firstOrNull { it.launchpadId == deviceId || it.selectionUUID == deviceId }
            ?: return false

        element.rotationDegrees.floatValue = rotationDegrees
        if (!fromRemote) {
            DeviceSyncCoordinator.onDeviceRotationChanged(element)
        }
        deviceRefresh.emit(Unit)
        return true
    }

    fun updateWorkspaceBounds() {
        val currentDevices = ViewportRepository.devices.value
        if (currentDevices.isNotEmpty()) {
            bounds = Pair(
                first = IntOffset(
                    x = currentDevices.minOf { it.position.value.x.toInt() },
                    y = currentDevices.minOf { it.position.value.y.toInt() }
                ),
                second = IntSize(
                    width = currentDevices.maxOf { it.position.value.x.toInt() + it.size.width.toInt() } - currentDevices.minOf { it.position.value.x.toInt() },
                    height = currentDevices.maxOf { it.position.value.y.toInt() + it.size.height.toInt() } - currentDevices.minOf { it.position.value.y.toInt() }
                )
            )
        }
        TimelineRepository.refreshChainEffectDurations()
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
                    }
                }
                if (device is NestedChainDevice) {
                    device.nestedChains().forEach(::recursiveResetMulti)
                }
            }
        }

        recursiveResetMulti(lightsChain)
        recursiveResetMulti(samplingChain)
    }

    fun loadWorkspace(workspaceData: SavableWorkspaceData, fromRemote: Boolean = false) {
        AutoPlayRepository.stopAutoPlay()
        TimelineRepository.stop()
        Echo.reset()
        Heaven.clear()

        if (fromRemote) {
            isApplyingRemoteBpmUpdate = true
            isApplyingRemoteProjectNameUpdate = true
            isApplyingRemoteMacrosUpdate = true
            isApplyingRemoteGridTypeUpdate = true
            TimelineRepository.isApplyingRemoteUpdate = true
        }
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
        samplingChain = workspaceData.sampling.unpackAudio()

        lightsChain.signalExit = {
            Heaven.midiEnter(it.filterIsInstance<Signal.LED>())
        }
        Echo.attachAudioChain(samplingChain)

        fun renderAnimationsInChain(chain: Chain): Int {
            var rendered = 0
            chain.devices.value.forEach { device ->
                when (device) {
                    is KeyframesChainDevice -> {
                        device.renderAnimation()
                        rendered += 1
                    }

                    is CompositionChainDevice -> {
                        device.renderAnimation()
                        rendered += 1
                    }
                }
                if (device is NestedChainDevice) {
                    device.nestedChains().forEach {
                        rendered += renderAnimationsInChain(it)
                    }
                }
            }
            return rendered
        }

        if (fromRemote) {
            syncMacrosSize(workspaceData.macros, fromRemote = true)
        } else {
            _macros.update { workspaceData.macros }
        }
        setParameterMappings(
            workspaceData.parameterMappings,
            fromRemote = fromRemote,
            undoable = false,
        )

        TimelineRepository.loadTracks(workspaceData.timelineData)
        AudioSourceLibrary.load(workspaceData.audioSources)
        migrateAudioEntries()
        canonicalizeSampleSources(samplingChain)

        ViewportRepository.devices.value.forEach { device ->
            midiManager.detachElement(device)
            device.close()
        }
        val loadedDevices = workspaceData.launchpadDevices.map { savedDevice ->
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
                savedMidiDeviceId = savedDevice.midiDeviceId
                savedInputPortId = savedDevice.inputPortId
                savedInputPortName = savedDevice.inputPortName
                savedOutputPortId = savedDevice.outputPortId
                savedOutputPortName = savedDevice.outputPortName
            }
        }
        ViewportRepository.setDevices(loadedDevices)

        if (ViewportRepository.devices.value.isNotEmpty()) {
            updateWorkspaceBounds()
        }

        _bpm.update {
            workspaceData.settings.bpm
        }

        renderAnimationsInChain(lightsChain)

        _projectName.update {
            workspaceData.title
        }

        if (!fromRemote) {
            replaceMode(PerformanceWorkspaceMode())
        }

        runBlocking {
            deviceRefresh.emit(Unit)
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

    /**
     * Moves Sample-device PCM into the shared source library. Legacy states
     * remain readable, while new workspace data stores each payload only once.
     */
    private fun canonicalizeSampleSources(chain: Chain) {
        data class SourceFingerprint(
            val sampleRate: Int,
            val channels: Int,
            val bitDepth: Int,
            val byteCount: Int,
            val contentHash: Int,
        )

        fun AudioSource.fingerprint() = SourceFingerprint(
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            byteCount = rawData.size,
            contentHash = rawData.contentHashCode(),
        )

        val sourceIndex = AudioSourceLibrary.all()
            .groupByTo(mutableMapOf(), AudioSource::fingerprint)
            .mapValuesTo(mutableMapOf()) { (_, sources) -> sources.toMutableList() }

        fun visit(current: Chain) {
            current.devices.value.forEach { device ->
                when (device) {
                    is SampleChainDevice -> {
                        val state = device.state.value
                        val bytes = state.resolvedRawData() ?: return@forEach
                        val directSource = state.sourceId
                            ?.let(AudioSourceLibrary::get)
                            ?.takeIf {
                                it.sampleRate == state.sampleRate &&
                                    it.channels == state.channels &&
                                    it.bitDepth == state.bitDepth
                            }
                        val fingerprint = SourceFingerprint(
                            sampleRate = state.sampleRate,
                            channels = state.channels,
                            bitDepth = state.bitDepth,
                            byteCount = bytes.size,
                            contentHash = bytes.contentHashCode(),
                        )
                        val source = directSource
                            ?: sourceIndex[fingerprint]
                                ?.firstOrNull {
                                    it.rawData === bytes || it.rawData.contentEquals(bytes)
                                }
                            ?: AudioSource(
                                id = UUID.randomUUID(),
                                fileName = state.fileName,
                                rawData = bytes,
                                sampleRate = state.sampleRate,
                                channels = state.channels,
                                bitDepth = state.bitDepth,
                            ).also {
                                AudioSourceLibrary.add(it)
                                sourceIndex.getOrPut(fingerprint, ::mutableListOf) += it
                            }
                        if (state.sourceId != source.id || state.rawData != null) {
                            device.state.value = state.copy(
                                rawData = null,
                                sourceId = source.id,
                            )
                            device.onStateRestored()
                        }
                    }

                    is NestedChainDevice -> device.nestedChains().forEach(::visit)
                }
            }
        }
        visit(chain)
    }

    private fun buildWorkspaceData(): SavableWorkspaceData {
        canonicalizeSampleSources(samplingChain)
        TimelineRepository.flushChainEffectRuntimeState()
        return SavableWorkspaceData(
            path = workspaceMeta?.path,
            title = workspaceMeta?.title ?: "Untitled",
            author = workspaceMeta?.author ?: "Unknown Author",
            lights = StateChain.pack(lightsChain),
            sampling = StateChain.pack(samplingChain),
            autoPlay = workspaceMeta?.autoPlay ?: AutoPlayData(emptyMap()),
            timelineData = TimelineRepository.tracks.value,
            macros = _macros.value,
            parameterMappings = _parameterMappings.value,
            settings = WorkspaceSettings(
                bpm = _bpm.value,
                autoPlayShowButtonPresses = workspaceMeta?.settings?.autoPlayShowButtonPresses ?: true,
                autoPlayShowLights = workspaceMeta?.settings?.autoPlayShowLights ?: true
            ),
            launchpadDevices = ViewportRepository.devices.value.map { device ->
                val inputPortId = device.launchpadDevice?.connection?.input?.portId ?: device.savedInputPortId
                val inputPortName = device.savedInputPortName
                val outputPortId = device.launchpadDevice?.midiOutput?.portId ?: device.savedOutputPortId
                val outputPortName = device.savedOutputPortName

                when (device) {
                    is ViewportLaunchpadPro -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportLaunchpadIdealised -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadIdealised(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportLaunchpadProMk3 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadProMk3(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportLaunchpadX -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadX(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportLaunchpadMk2 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadMk2(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportMystrix -> SavableWorkspaceData.SavableViewportLaunchpad.Mystrix(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
                    )
                    is ViewportMidiFighter64 -> SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64(
                        id = device.launchpadId,
                        positionX = device.position.value.x,
                        positionY = device.position.value.y,
                        rotationDegrees = device.rotationDegrees.floatValue,
                        style = device.style,
                        midiDeviceId = device.savedMidiDeviceId,
                        inputPortId = inputPortId,
                        inputPortName = inputPortName,
                        outputPortId = outputPortId,
                        outputPortName = outputPortName
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
        AutoPlayRepository.stopAutoPlay()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
        UndoManager.clear()
        SelectionManager.clear()
        Echo.reset()
        Heaven.clear()
        AudioSourceLibrary.clear()
        TransmitChainDevice.clearReceiversForTesting()
        AutomappingManager.reset()

        // Reset chains
        lightsChain = Chain()
        samplingChain = AudioChain()
        
        // Re-setup signal exits
        setupChains()
        
        // Clear devices
        ViewportRepository.devices.value.forEach { device ->
            midiManager.detachElement(device)
            device.close()
        }
        ViewportRepository.clear()
        
        // Reset state
        bounds = Pair(IntOffset(0, 0), IntSize(0, 0))
        workspaceMeta = null
        _macros.update { listOf(Macro(1)) }
        _parameterMappings.value = emptyList()
        replaceMode(LayoutWorkspaceMode())
        _bpm.update { 120.00 }
        _projectName.update { null }
        previousMode = LayoutWorkspaceMode()
        _gridType.update { GridUtils.GridType.Flexible.Medium }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun getVerificationHash(): Int {
        val data = saveWorkspace()
        val hashableData = data.copy(
            macros = data.macros.map(Macro::withoutLocalValue),
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
            device is NestedChainDevice && device.nestedChains().any { chainContainsChain(it, target) }
        }
    }

    private fun chainContainsDevice(root: Chain, deviceSelectionUUID: String): Boolean {
        if (root.devices.value.any { it.selectionUUID == deviceSelectionUUID }) return true

        return root.devices.value.any { device ->
            device is NestedChainDevice && device.nestedChains().any { chainContainsDevice(it, deviceSelectionUUID) }
        }
    }
}
