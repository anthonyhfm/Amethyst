package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import dev.anthonyhfm.amethyst.devices.gem.GemChainDevice
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
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBuiltInPresets
import dev.anthonyhfm.amethyst.gem.GemHostIoContract
import dev.anthonyhfm.amethyst.gem.duplicate
import dev.anthonyhfm.amethyst.gem.host.GemAssetReference
import dev.anthonyhfm.amethyst.gem.host.GemDeviceResolution
import dev.anthonyhfm.amethyst.gem.host.GemDeviceState
import dev.anthonyhfm.amethyst.gem.host.GemHostResolver
import dev.anthonyhfm.amethyst.gem.host.GemWorkspaceAssetCatalog
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import dev.anthonyhfm.amethyst.gem.data.GemLoadError
import dev.anthonyhfm.amethyst.gem.ui.GemSelectionWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceGemAsset
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceMeta
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

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
    
    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    private val _gemAssets = MutableStateFlow<List<GemAsset>>(emptyList())
    val gemAssets: StateFlow<List<GemAsset>> = _gemAssets.asStateFlow()

    /** Maps assetId → human-readable load error message for assets that loaded with migration issues. */
    private val _gemAssetLoadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val gemAssetLoadErrors: StateFlow<Map<String, String>> = _gemAssetLoadErrors.asStateFlow()

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

        if (undoable && (current.selectable || current is GemSelectionWorkspaceMode)) {
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
        AutomappingManager.reset()

        // Store only metadata in memory
        workspaceMeta = WorkspaceMeta(
            path = workspaceData.path,
            title = workspaceData.title,
            author = workspaceData.author,
            settings = workspaceData.settings,
            autoPlay = workspaceData.autoPlay
        )

        val loadErrors = mutableMapOf<String, String>()
        _gemAssets.update {
            workspaceData.gemAssets.mapNotNull { serializedAsset ->
                val decoded = GemJsonPersistence.decode(serializedAsset.serializedAsset)
                val err = decoded.loadError
                when {
                    err is GemLoadError.MigrationError -> {
                        // Asset was migrated with issues — keep it and surface the error.
                        loadErrors[decoded.asset.metadata.id.ifBlank { serializedAsset.assetId }] = err.message
                        decoded.asset
                    }
                    err != null -> {
                        // Catastrophic parse/IO failure — skip and log.
                        println(
                            "Failed to decode workspace gem asset '${serializedAsset.assetId}': ${err.message}"
                        )
                        null
                    }
                    else -> decoded.asset
                }
            }
        }
        _gemAssetLoadErrors.update { loadErrors }

        lightsChain = workspaceData.lights.unpack()
        samplingChain = workspaceData.sampling.unpack()

        lightsChain.signalExit = {
            Heaven.midiEnter(it.filterIsInstance<Signal.LED>())
        }

        samplingChain.signalExit = {
            Echo.audioEnter(it.filterIsInstance<Signal.AudioSignal>())
        }

        refreshGemDevices()

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
        migrateAudioEntries()

        Heaven.devices = workspaceData.launchpadDevices.map { savedDevice ->
            val device = when (savedDevice.type) {
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO -> ViewportLaunchpadPro()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO_MK3 -> ViewportLaunchpadProMk3()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_X -> ViewportLaunchpadX()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_MK2 -> ViewportLaunchpadMk2()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MYSTRIX -> ViewportMystrix()
                SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.MIDIFIGHTER64 -> ViewportMidiFighter64()
            }

            device.apply {
                position.value = Offset(savedDevice.positionX, savedDevice.positionY)
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
            gemAssets = _gemAssets.value.map(SavableWorkspaceGemAsset::from),
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
                    id = device.launchpadId,
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
            persistRecentColors(newList)
            newList
        }
    }

    fun hasUnsavedChanges(): Boolean {
        // Always show the Unsaved Changes dialog when attempting to close
        // This avoids file system access (PlatformFile) on mobile platforms
        return true
    }

    fun replaceGemAssets(assets: List<GemAsset>) {
        _gemAssets.update { assets }
        refreshGemDevices()
    }

    fun createGemAsset(preferredHostDomain: GemSignalDomain? = null): GemAsset {
        val asset = Gem.emptyAsset(
            assetId = "gem://workspace/${UUID.randomUUID()}",
            name = nextGemAssetName()
        ).copy(
            definition = Gem.emptyAsset().definition.copy(
                host = GemHostIoContract(
                    assetShape = GemHostIoContract().assetShape,
                    supportedDomains = preferredHostDomain?.let(::listOf).orEmpty()
                )
            )
        )
        return saveGemAsset(asset)
    }

    fun saveGemAsset(asset: GemAsset, previousAssetId: String? = null): GemAsset {
        val requestedAssetId = asset.metadata.id.trim()
        val conflictExists = previousAssetId != null &&
            requestedAssetId.isNotBlank() &&
            requestedAssetId != previousAssetId &&
            _gemAssets.value.any { it.metadata.id == requestedAssetId }
        val resolvedAssetId = when {
            requestedAssetId.isNotBlank() && !conflictExists -> requestedAssetId
            !previousAssetId.isNullOrBlank() -> previousAssetId
            else -> "gem://workspace/${UUID.randomUUID()}"
        }
        val persistedAsset = asset.copy(
            metadata = asset.metadata.copy(id = resolvedAssetId)
        )

        _gemAssets.update { current ->
            val withoutPrevious = if (!previousAssetId.isNullOrBlank() && previousAssetId != resolvedAssetId) {
                current.filterNot { it.metadata.id == previousAssetId }
            } else {
                current
            }
            val existingIndex = withoutPrevious.indexOfFirst { it.metadata.id == resolvedAssetId }
            if (existingIndex >= 0) {
                withoutPrevious.toMutableList().apply { this[existingIndex] = persistedAsset }
            } else {
                withoutPrevious + persistedAsset
            }
        }
        refreshGemDevices()
        return persistedAsset
    }

    fun upsertGemAsset(asset: GemAsset) {
        saveGemAsset(asset)
    }

    fun removeGemAsset(assetId: String) {
        _gemAssets.update { current -> current.filterNot { it.metadata.id == assetId } }
        refreshGemDevices()
    }

    /**
     * Creates a workspace copy of [asset] with a fresh ID and a "Copy" name suffix.
     * The duplicate is immediately persisted and returned.
     */
    fun duplicateGemAsset(asset: GemAsset): GemAsset {
        val duplicate = asset.duplicate(newId = "gem://workspace/${UUID.randomUUID()}")
        return saveGemAsset(duplicate)
    }

    /** Returns the list of built-in starter Gem presets, ready to duplicate into the workspace. */
    fun getBuiltInPresets(): List<GemAsset> = GemBuiltInPresets.getAll()

    fun gemAssetCatalog(): GemWorkspaceAssetCatalog = GemHostResolver.catalog(_gemAssets.value)

    fun resolveGemAsset(reference: GemAssetReference): GemAsset? = gemAssetCatalog().assetsById[reference.assetId]

    fun resolveGemDevice(state: GemDeviceState): GemDeviceResolution = GemHostResolver.resolve(
        deviceState = state,
        catalog = gemAssetCatalog()
    )

    fun hostDomainForChain(chain: Chain): GemSignalDomain? = when {
        chainContainsChain(lightsChain, chain) -> GemSignalDomain.LED
        else -> null
    }

    fun hostDomainForDevice(device: GenericChainDevice<*>): GemSignalDomain? = when {
        chainContainsDevice(lightsChain, device.selectionUUID) -> GemSignalDomain.LED
        else -> null
    }

    fun refreshGemDevices() {
        refreshGemDevicesInChain(lightsChain, GemSignalDomain.LED)
        refreshGemDevicesInChain(samplingChain, null)
    }

    fun refreshGemDevicesInChain(chain: Chain, hostDomain: GemSignalDomain?) {
        chain.devices.value.forEach { device ->
            when (device) {
                is GemChainDevice -> device.attachToHostContext(hostDomain)
                is GroupChainDevice -> device.state.value.groups.forEach { group ->
                    refreshGemDevicesInChain(group.chain, hostDomain)
                }

                is MultiGroupChainDevice -> device.state.value.groups.forEach { group ->
                    refreshGemDevicesInChain(group.chain, hostDomain)
                }

                is ChokeChainDevice -> refreshGemDevicesInChain(device.state.value.chain, hostDomain)
            }
        }
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
        _gemAssets.update { emptyList() }
        _gemAssetLoadErrors.update { emptyMap() }
        previousMode = WorkspaceContract.WorkspaceMode.Layout()
        _gridType.update { GridUtils.GridType.Flexible.Medium }
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

    private fun nextGemAssetName(): String {
        val usedNames = _gemAssets.value.map { it.metadata.name.trim() }.toSet()
        if ("Untitled Gem" !in usedNames) {
            return "Untitled Gem"
        }

        var index = 2
        while ("Untitled Gem $index" in usedNames) {
            index += 1
        }
        return "Untitled Gem $index"
    }
}
