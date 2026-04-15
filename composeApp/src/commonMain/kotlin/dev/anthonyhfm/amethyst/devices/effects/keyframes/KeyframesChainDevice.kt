package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.*
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.runBlocking
import kotlin.math.pow
import dev.anthonyhfm.amethyst.devices.effects.keyframes.util.Pincher
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import io.github.vinceglb.filekit.readBytes
import androidx.compose.runtime.snapshotFlow

class KeyframesChainDevice : LEDChainDevice<KeyframesChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode()
    private var lastSelectedFrameIndex: Int? = null
    private val dragVisitedPads: MutableSet<Triple<String, Int, Int>> = mutableSetOf()
    private var dragEraseMode: Boolean = false
    private var stateBeforeDrag: KeyframesChainDeviceState? = null
    private var lastObservedFrameEntries: List<KeyframesEntry>? = null
    private val stateObserverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        renderAnimation()

        customMode.state = state.asStateFlow()
        customMode.parentDevice = this

        customMode.onEvent = { onEvent(it) }

        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        customMode.modeClose = {
            Heaven.devices.forEach { device ->
                device.previewState.clear()
            }
        }

        customMode.onVirtualDeviceDragStart = { device, localX, localY ->
            beginVirtualDrag(device, localX, localY)
        }

        customMode.onVirtualDeviceDrag = { device, localX, localY ->
            continueVirtualDrag(device, localX, localY)
        }

        customMode.onVirtualDeviceDragEnd = {
            endVirtualDrag()
        }
        
        // Observe state changes to refresh virtual devices when state is restored (e.g., undo/redo).
        // Only refresh when the keyframes editor mode is active — refreshVirtualDevices() is a
        // direct-preview feature of that mode, and calling it outside the mode (e.g. during project
        // load) causes the current keyframe to flash briefly on virtual devices before playback starts.
        stateObserverScope.launch {
            state.collect { newState ->
                // Check if frame entries changed (but only when not actively dragging)
                if (!isDragging.value) {
                    val currentFrameEntries = newState.frames.getOrNull(newState.currentFrameIndex)?.entries
                    if (currentFrameEntries != lastObservedFrameEntries) {
                        lastObservedFrameEntries = currentFrameEntries
                        // Only refresh virtual devices when the keyframes editor mode is active.
                        // refreshVirtualDevices() is a direct-preview feature of that mode; calling
                        // it outside (e.g. during project load) causes the current keyframe to flash
                        // briefly on virtual devices before playback starts.
                        if (WorkspaceRepository.mode.value === customMode) {
                            refreshVirtualDevices()
                        }
                    }
                }
            }
        }

        // Re-render the pre-computed animation whenever any device is repositioned, so that
        // device-anchored entries continue to resolve to the correct global coordinates.
        stateObserverScope.launch {
            snapshotFlow {
                Heaven.devices.map { it.position.value }
            }.drop(1).collect {
                renderAnimation()
            }
        }
    }

    @Composable
    override fun Content() {
        val bpm = WorkspaceRepository.bpm.collectAsState()
        val state by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        LaunchedEffect(bpm, state.frames) {
            renderAnimation()
        }

        ChainDeviceShell(
            title = "Keyframes",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Button(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                variant = ButtonVariant.Default,
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = "Draw",
                    modifier = Modifier
                        .size(36.dp),
                    tint = Theme[colors][primaryForeground],
                )
            }
        }
    }

    private fun beginVirtualDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        isDragging.value = true
        dragVisitedPads.clear()
        dragEraseMode = padMatchesSelectedColor(device.launchpadId, localX, localY)
        
        // Capture the state before drawing starts
        stateBeforeDrag = state.value

        applyDragAt(device, localX, localY)
    }

    private fun continueVirtualDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        if (!isDragging.value) {
            beginVirtualDrag(device, localX, localY)
            return
        }

        applyDragAt(device, localX, localY)
    }

    private fun endVirtualDrag() {
        isDragging.value = false
        
        // Push state change for undo/redo
        stateBeforeDrag?.let { beforeState ->
            pushStateChange(beforeState, state.value)
        }
        
        stateBeforeDrag = null
        dragVisitedPads.clear()
    }

    private fun applyDragAt(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        if (!dragVisitedPads.add(Triple(device.launchpadId, localX, localY))) return

        if (dragEraseMode) {
            applyColorAt(device, localX, localY, Triple(0f, 0f, 0f), addToRecent = false)
        } else {
            applyColorAt(device, localX, localY, state.value.selectedColor, addToRecent = true)
        }
    }

    private fun padMatchesSelectedColor(launchpadId: String, localX: Int, localY: Int): Boolean {
        return currentEntryColor(launchpadId, localX, localY) == state.value.selectedColor
    }

    private fun currentEntryColor(launchpadId: String, localX: Int, localY: Int): Triple<Float, Float, Float> {
        val frame = state.value.frames[state.value.currentFrameIndex]
        val entry = frame.entries.firstOrNull { it.launchpadId == launchpadId && it.localX == localX && it.localY == localY }

        return if (entry != null) {
            Triple(entry.r, entry.g, entry.b)
        } else {
            Triple(0f, 0f, 0f)
        }
    }

    private fun applyColorAt(
        device: LaunchpadViewportElement,
        localX: Int,
        localY: Int,
        color: Triple<Float, Float, Float>,
        addToRecent: Boolean
    ) {
        if (addToRecent) {
            WorkspaceRepository.addRecentColor(color)
        }

        val launchpadId = device.launchpadId
        val globalX = localX + device.position.value.x.toInt()
        val globalY = localY + device.position.value.y.toInt()

        state.update { currentState ->
            currentState.copy(
                frames = currentState.frames.toMutableList().apply {
                    set(
                        index = currentState.currentFrameIndex,
                        element = currentState.frames[currentState.currentFrameIndex].copy(
                            entries = currentState.frames[currentState.currentFrameIndex].entries.toMutableList().apply {
                                val index = indexOfFirst { it.launchpadId == launchpadId && it.localX == localX && it.localY == localY }
                                if (index != -1) removeAt(index)
                                add(
                                    KeyframesEntry(
                                        x = globalX,
                                        y = globalY,
                                        r = color.first,
                                        g = color.second,
                                        b = color.third,
                                        launchpadId = launchpadId,
                                        localX = localX,
                                        localY = localY,
                                    )
                                )
                            }
                        )
                    )
                }
            )
        }

        Heaven.midiEnter(
            listOf(
                Signal.LED(
                    origin = this,
                    x = globalX,
                    y = globalY,
                    color = Color(
                        red = color.first,
                        green = color.second,
                        blue = color.third
                    ),
                    layer = 0
                )
            )
        )
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.OnPaintButton -> {
                val globalX = event.x
                val globalY = event.y

                // Resolve global coords to a specific device + local coords
                val device = Heaven.devices.firstOrNull { d ->
                    val lx = globalX - d.position.value.x.toInt()
                    val ly = globalY - d.position.value.y.toInt()
                    lx in 0 until d.layout.cols && ly in 0 until d.layout.rows
                }

                if (device != null) {
                    val localX = globalX - device.position.value.x.toInt()
                    val localY = globalY - device.position.value.y.toInt()
                    val selectedColor = state.value.selectedColor
                    val currentColor = currentEntryColor(device.launchpadId, localX, localY)
                    val targetColor = if (currentColor == selectedColor) {
                        Triple(0f, 0f, 0f)
                    } else {
                        selectedColor
                    }
                    applyColorAt(
                        device = device,
                        localX = localX,
                        localY = localY,
                        color = targetColor,
                        addToRecent = targetColor != Triple(0f, 0f, 0f)
                    )
                }
            }

            is Event.OnColorUpdate -> {
                state.update {
                    it.copy(selectedColor = Triple(event.color.red, event.color.green, event.color.blue))
                }
            }

            is Event.OnSelectFrame -> {
                if (event.frameIndex == state.value.frames.lastIndex + 1) {
                    onEvent(Event.OnAddFrame())
                    return
                } else if (event.frameIndex == -1) {
                    onEvent(Event.OnAddFrame(0))
                    return
                }

                val keyframeItem = Selectable.KeyframeItem(
                    parent = this,
                    frameIndex = event.frameIndex
                )

                when {
                    event.rangeSelect -> {
                        val startIndex = state.value.currentFrameIndex
                        val endIndex = event.frameIndex

                        val start = minOf(startIndex, endIndex)
                        val end = maxOf(startIndex, endIndex)

                        SelectionManager.clear()

                        for (i in start..end) {
                            if (i in 0 until state.value.frames.size) {
                                SelectionManager.select(
                                    Selectable.KeyframeItem(parent = this, frameIndex = i),
                                    single = false
                                )
                            }
                        }

                        lastSelectedFrameIndex = event.frameIndex
                    }
                    event.multiSelect -> {
                        SelectionManager.select(keyframeItem, single = false)
                        lastSelectedFrameIndex = event.frameIndex
                    }
                    else -> {
                        SelectionManager.select(keyframeItem, single = true)
                        lastSelectedFrameIndex = event.frameIndex

                        state.update {
                            it.copy(
                                currentFrameIndex = if (event.frameIndex < 0) {
                                    0
                                } else if (event.frameIndex >= it.frames.size) {
                                    it.frames.size - 1
                                } else {
                                    event.frameIndex
                                }
                            )
                        }
                    }
                }

                refreshVirtualDevices()
            }

            is Event.OnAddFrame -> {
                val newFrame = Frame(
                    timing = if (event.atIndex != null) {
                        state.value.frames.getOrNull(event.atIndex - 1)?.timing
                            ?: state.value.frames[state.value.currentFrameIndex].timing
                    } else {
                        state.value.frames[state.value.currentFrameIndex].timing
                    }
                )

                val frameIndex = event.atIndex ?: state.value.frames.size

                UndoManager.addAction(
                    UndoableAction.KeyframeCreation(
                        device = this,
                        frameIndex = frameIndex,
                        frame = newFrame
                    )
                )

                addFrameInternal(frameIndex, newFrame)
                onEvent(Event.OnSelectFrame(frameIndex))
                refreshVirtualDevices()
            }

            is Event.OnDuplicateFrame -> {
                val frameIndexToDuplicate = event.frameIndex ?: state.value.currentFrameIndex
                val frameToDuplicate = state.value.frames[frameIndexToDuplicate]
                val newFrame = frameToDuplicate.copy(_internalUuid = UUID.randomUUID())
                val insertIndex = frameIndexToDuplicate + 1

                UndoManager.addAction(
                    UndoableAction.KeyframeDuplication(
                        device = this,
                        originalIndex = frameIndexToDuplicate,
                        duplicatedIndex = insertIndex,
                        duplicatedFrame = newFrame
                    )
                )

                duplicateFrameInternal(frameIndexToDuplicate, insertIndex)
                onEvent(Event.OnSelectFrame(insertIndex))
                refreshVirtualDevices()
            }

            is Event.OnDeleteFrame -> {
                if (state.value.frames.size <= 1) return

                val frameToDelete = state.value.frames[event.frameIndex]

                UndoManager.addAction(
                    UndoableAction.KeyframeDeletion(
                        device = this,
                        frameIndex = event.frameIndex,
                        frame = frameToDelete
                    )
                )

                removeFrameInternal(event.frameIndex)

                if (event.frameIndex == state.value.frames.size) {
                    val newFrameIndex = maxOf(0, state.value.frames.size - 1)
                    SelectionManager.select(
                        Selectable.KeyframeItem(parent = this, frameIndex = newFrameIndex),
                        single = true
                    )
                }

                refreshVirtualDevices()
            }

            is Event.OnChangeFramePosition -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            add(event.to, removeAt(event.from))
                        }
                    )
                }

                refreshVirtualDevices()
            }

            is Event.OnChangeFrameTiming -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            set(
                                index = event.frameIndex,
                                element = it.frames[event.frameIndex].copy(timing = event.timing, gate = event.gate)
                            )
                        }
                    )
                }
            }

            is Event.OnChangeMultiFrameTiming -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            event.frameIndices.forEach { frameIndex ->
                                if (frameIndex in 0 until size) {
                                    set(
                                        index = frameIndex,
                                        element = get(frameIndex).copy(timing = event.timing, gate = event.gate)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            is Event.OnImportMidiFile -> {
                runBlocking {
                    val file = FileKit.openFilePicker(
                        type = FileKitType.File(
                            extension = "mid"
                        ),
                    )

                    if (file == null) return@runBlocking

                    try {
                        val data = MidiFileImporter.loadData(
                            data = file.readBytes(),
                            bpm = WorkspaceRepository.bpm.value
                        )

                        this@KeyframesChainDevice.state.update {
                            data
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            is Event.OnChangeInfinity -> {
                state.update {
                    it.copy(
                        infinity = event.checked
                    )
                }
            }

            is Event.OnChangePinch -> {
                val newPinch = event.pinch.coerceIn(-2f, 2f)
                state.update { it.copy(pinch = newPinch) }
                renderAnimation()
            }

            Event.OnTogglePinchBilateral -> {
                state.update { it.copy(bilateralPinch = !it.bilateralPinch) }
                renderAnimation()
            }

            is Event.OnChangeRepeats -> {
                state.update { it.copy(repeats = event.repeats.coerceAtLeast(1)) }
            }

            is Event.OnChangePlaybackMode -> {
                state.update { it.copy(playbackMode = event.playbackMode) }
            }

            is Event.OnChangeRootKey -> {
                state.update { it.copy(rootKey = event.rootKey) }
            }

            is Event.OnChangeWrap -> {
                state.update { it.copy(wrap = event.wrap) }
            }

            is Event.OnChangeIsolate -> {
                state.update { it.copy(isolate = event.isolate) }
            }
        }
    }

    fun removeFrames(frameIndices: List<Int>) {
        if (frameIndices.isEmpty()) return

        val sortedIndices = frameIndices.sortedDescending()
        val deletions = mutableListOf<UndoableAction.KeyframeDeletionInfo>()

        sortedIndices.forEach { index ->
            if (index < state.value.frames.size) {
                val frame = state.value.frames[index]
                deletions.add(
                    UndoableAction.KeyframeDeletionInfo(
                        frameIndex = index,
                        frame = frame
                    )
                )
            }
        }

        UndoManager.addAction(
            UndoableAction.MultiKeyframeDeletion(
                device = this,
                deletions = deletions
            )
        )

        sortedIndices.forEach { index ->
            if (index < state.value.frames.size) {
                removeFrameInternal(index)
            }
        }

        refreshVirtualDevices()
    }

    internal fun addFrameInternal(index: Int, frame: Frame) {
        state.update {
            it.copy(
                frames = it.frames.toMutableList().apply {
                    add(index, frame)
                }
            )
        }
    }

    internal fun removeFrameInternal(index: Int) {
        state.update {
            val newFrames = it.frames.toMutableList().apply {
                removeAt(index)
            }

            if (newFrames.isEmpty()) {
                newFrames.add(Frame(
                    timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
                    _internalUuid = UUID.randomUUID()
                ))
            }

            val newCurrentFrameIndex = when {
                newFrames.size == 1 -> 0
                it.currentFrameIndex >= newFrames.size -> newFrames.size - 1
                it.currentFrameIndex > index -> it.currentFrameIndex - 1
                else -> it.currentFrameIndex
            }

            it.copy(
                frames = newFrames,
                currentFrameIndex = newCurrentFrameIndex
            )
        }
    }

    internal fun duplicateFrameInternal(originalIndex: Int, targetIndex: Int) {
        state.update {
            val currentFrames = it.frames.toMutableList()
            val frame = currentFrames[originalIndex]
            val newFrame = frame.copy(_internalUuid = UUID.randomUUID())

            val safeTargetIndex = targetIndex.coerceIn(0, currentFrames.size)

            currentFrames.add(safeTargetIndex, newFrame)

            it.copy(frames = currentFrames)
        }
    }

    fun refreshVirtualDevices() {
        Heaven.clear()

        Heaven.midiEnter(
            state.value.frames[state.value.currentFrameIndex].entries.mapNotNull { it.resolveToSignal(Color(it.r, it.g, it.b)) }
        )
    }

    fun renderAnimation() {
        val bpm = WorkspaceRepository.bpm.value
        var animationMs = 0

        val frames = state.value.frames + Frame(
            timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_16),
            _internalUuid = UUID.randomUUID()
        )

        val raw = buildList {
            frames.forEachIndexed { index, frame ->
                val previousFrame = frames.getOrNull(index - 1)
                val deltaMs = previousFrame?.timing?.toMsValue(bpm)?.times(frame.gate * 2)?.toInt() ?: 0
                animationMs += deltaMs

                val signals = buildList {
                    addAll(frame.entries.filter { !(previousFrame?.entries?.contains(it) ?: false) }.mapNotNull { it.toSignal() })

                    if (previousFrame != null) {
                        if (state.value.infinity && index == frames.lastIndex) return@buildList

                        val cleared = previousFrame.entries.filter { prev ->
                            frame.entries.none { it.samePosition(prev) }
                        }.mapNotNull { it.toOffSignal() }

                        addAll(cleared)
                    }
                }

                add(animationMs to signals)
            }
        }

        val pinch = state.value.pinch.coerceIn(-2f, 2f)
        val bilateral = state.value.bilateralPinch
        val totalDuration = raw.lastOrNull()?.first ?: 0

        val processed = if (totalDuration > 0 && (pinch != 0f || bilateral)) {
            val totalD = totalDuration.toDouble()
            raw.map { (time, signals) ->
                val mapped = Pincher.applyPinch(time.toDouble(), totalD, pinch, bilateral).toInt()
                mapped to signals
            }.distinctBy { it.first }
                .sortedBy { it.first }
                .let { mappedList ->
                    val withStart = if (mappedList.firstOrNull()?.first != 0) listOf(0 to mappedList.first().second) + mappedList else mappedList
                    if (withStart.lastOrNull()?.first != totalDuration) withStart + (totalDuration to emptyList()) else withStart
                }
        } else raw

        state.update { it.copy(renderedAnimation = processed) }
    }

    /** Resolves the entry's coordinates to global and returns a coloured signal, or null if
     *  the anchored device is not currently in [Heaven.devices]. */
    private fun KeyframesEntry.resolveGlobal(): Pair<Int, Int>? {
        if (isDeviceAnchored) {
            val device = Heaven.devices.firstOrNull { it.launchpadId == launchpadId } ?: return null
            return Pair(localX!! + device.position.value.x.toInt(), localY!! + device.position.value.y.toInt())
        }
        return Pair(x, y)
    }

    private fun KeyframesEntry.resolveToSignal(color: Color): Signal.LED? {
        val (gx, gy) = resolveGlobal() ?: return null
        return Signal.LED(origin = this, x = gx, y = gy, color = color, layer = 0)
    }

    /** Returns true when [other] occupies the same physical position as this entry. */
    private fun KeyframesEntry.samePosition(other: KeyframesEntry): Boolean {
        return if (isDeviceAnchored && other.isDeviceAnchored) {
            launchpadId == other.launchpadId && localX == other.localX && localY == other.localY
        } else {
            x == other.x && y == other.y
        }
    }

    private fun KeyframesEntry.toSignal(): Signal.LED? = resolveToSignal(Color(r, g, b))

    private fun KeyframesEntry.toOffSignal(): Signal.LED? = resolveToSignal(Color.Black)

    private val heldSignals = mutableSetOf<Int>() // Signals currently held in Loop mode

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val state = state.value
        if (state.isolate) {
            Heaven.cancelJobsForOwner(this)
        }
        
        n.forEach { signal ->
            if (signal.color != Color.Black) {
                val identifier = signal.x * 10 + signal.y
                
                when (state.playbackMode) {
                    PlaybackMode.Mono -> {
                        if (!state.isolate) {
                            Heaven.cancelJobsForOwner(this)
                        }
                        startPlayback(signal)
                    }
                    PlaybackMode.Poly -> {
                        startPlayback(signal)
                    }
                    PlaybackMode.Loop -> {
                        if (heldSignals.add(identifier)) {
                            Heaven.cancelJobsForOwner(this, identifier)
                            startLoopPlayback(signal, identifier)
                        }
                    }
                }
            } else {
                val identifier = signal.x * 10 + signal.y
                if (state.playbackMode == PlaybackMode.Loop) {
                    heldSignals.remove(identifier)
                    Heaven.cancelJobsForOwner(this, identifier)
                }
            }
        }
    }

    private fun startPlayback(triggerSignal: Signal.LED) {
        val state = state.value
        val repeats = state.repeats
        val animation = state.renderedAnimation
        val totalDuration = animation.lastOrNull()?.first ?: 0
        val identifier = if (state.playbackMode == PlaybackMode.Poly) triggerSignal.x * 10 + triggerSignal.y else null

        for (r in 0 until repeats) {
            val offset = r * totalDuration
            animation.forEach { (time, signals) ->
                Heaven.schedule(offset + time.toDouble(), owner = this, identifier = identifier) {
                    val transformed = transformSignals(signals, triggerSignal)
                    signalExit?.invoke(transformed)
                }
            }
        }
    }

    private fun startLoopPlayback(triggerSignal: Signal.LED, identifier: Int) {
        val state = state.value
        val animation = state.renderedAnimation
        val totalDuration = (animation.lastOrNull()?.first ?: 0).toDouble()
        if (totalDuration <= 0) return

        fun playOnce(loopOffset: Double) {
            if (!heldSignals.contains(identifier)) return

            animation.forEach { (time, signals) ->
                Heaven.schedule(loopOffset + time, owner = this, identifier = identifier) {
                    val transformed = transformSignals(signals, triggerSignal)
                    signalExit?.invoke(transformed)
                    
                    // If this is the last frame of the animation, schedule the next loop
                    if (time.toDouble() == totalDuration && heldSignals.contains(identifier)) {
                        playOnce(loopOffset + totalDuration)
                    }
                }
            }
        }

        playOnce(0.0)
    }

    private fun transformSignals(signals: List<Signal>, triggerSignal: Signal.LED): List<Signal> {
        val state = state.value
        val rootKey = state.rootKey ?: return signals
        
        val dx = triggerSignal.x - (rootKey % 10)
        val dy = triggerSignal.y - (rootKey / 10)
        
        if (dx == 0 && dy == 0) return signals

        return signals.map { signal ->
            if (signal is Signal.LED) {
                var newX = signal.x + dx
                var newY = signal.y + dy
                
                if (state.wrap) {
                    newX = (newX % 10 + 10) % 10
                    newY = (newY % 10 + 10) % 10
                }
                
                if (newX in 0..9 && newY in 0..9) {
                    signal.copy(x = newX, y = newY, origin = signal.origin)
                } else {
                    signal.copy(color = Color.Black, origin = signal.origin)
                }
            } else signal
        }
    }

    fun duplicateFrames(frameIndices: List<Int>, targetStartIndex: Int? = null) {
        if (frameIndices.isEmpty()) return

        val sortedIndices = frameIndices.sorted()
        val duplications = mutableListOf<UndoableAction.KeyframeDuplicationInfo>()

        val baseTargetIndex = targetStartIndex ?: (sortedIndices.last() + 1)

        sortedIndices.forEachIndexed { index, originalIndex ->
            val frame = state.value.frames[originalIndex]
            val newFrame = frame.copy(_internalUuid = UUID.randomUUID())
            val targetIndex = baseTargetIndex + index

            duplications.add(
                UndoableAction.KeyframeDuplicationInfo(
                    originalIndex = originalIndex,
                    duplicatedIndex = targetIndex,
                    duplicatedFrame = newFrame
                )
            )
        }

        UndoManager.addAction(
            UndoableAction.MultiKeyframeDuplication(
                device = this,
                duplications = duplications
            )
        )

        duplications.forEach { duplication ->
            addFrameInternal(duplication.duplicatedIndex, duplication.duplicatedFrame)
        }

        SelectionManager.clear()
        duplications.forEach { duplication ->
            SelectionManager.select(
                Selectable.KeyframeItem(parent = this, frameIndex = duplication.duplicatedIndex),
                single = false
            )
        }

        refreshVirtualDevices()
    }

    fun pasteFrames(frames: List<Frame>, targetStartIndex: Int? = null) {
        if (frames.isEmpty()) return

        val baseTargetIndex = targetStartIndex ?: state.value.currentFrameIndex + 1
        val pastedFrameInfos = mutableListOf<UndoableAction.KeyframePasteInfo>()

        frames.forEachIndexed { index, frame ->
            val targetIndex = baseTargetIndex + index
            val newFrame = frame.copy(_internalUuid = UUID.randomUUID())

            pastedFrameInfos.add(
                UndoableAction.KeyframePasteInfo(
                    frameIndex = targetIndex,
                    frame = newFrame
                )
            )
        }

        UndoManager.addAction(
            UndoableAction.KeyframePaste(
                device = this,
                pastedFrames = pastedFrameInfos
            )
        )

        pastedFrameInfos.forEach { pasteInfo ->
            addFrameInternal(pasteInfo.frameIndex, pasteInfo.frame)
        }

        SelectionManager.clear()
        pastedFrameInfos.forEach { pasteInfo ->
            SelectionManager.select(
                Selectable.KeyframeItem(parent = this, frameIndex = pasteInfo.frameIndex),
                single = false
            )
        }

        refreshVirtualDevices()
    }

    override fun onChoke() {
        // Cancel all scheduled Heaven tasks owned by this device
        Heaven.cancelJobsForOwner(this)
        heldSignals.clear()
    }
}
