package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.*
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.runBlocking

class KeyframesChainDevice : ChainDevice<KeyframesChainDeviceState>() {
    override val state = MutableStateFlow(KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode()
    private var lastSelectedFrameIndex: Int? = null

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

        customMode.onVirtualDevicePress = { x, y ->
            onEvent(Event.OnPaintButton(x, y))
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

        AmethystDevice(
            title = "Keyframes",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp)
        ) {
            FilledIconButton(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                modifier = Modifier
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = "Draw",
                    modifier = Modifier
                        .size(36.dp)
                )
            }
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.OnPaintButton -> {
                state.update { state ->
                    state.copy(
                        frames = state.frames.toMutableList().apply {
                            set(
                                index = state.currentFrameIndex,
                                element = state.frames[state.currentFrameIndex].copy(
                                    entries = state.frames[state.currentFrameIndex].entries.toMutableList().apply {
                                        val index: Int = indexOfFirst { it.x == event.x && it.y == event.y }

                                        if (index != -1) {
                                            removeAt(index)
                                        }

                                        add(
                                            element = KeyframesEntry(
                                                x = event.x,
                                                y = event.y,
                                                r = state.selectedColor.first,
                                                g = state.selectedColor.second,
                                                b = state.selectedColor.third
                                            )
                                        )

                                        Heaven.midiEnter(
                                            listOf(
                                                Signal(
                                                    origin = this,
                                                    x = event.x,
                                                    y = event.y,
                                                    color = Color(
                                                        red = state.selectedColor.first,
                                                        green = state.selectedColor.second,
                                                        blue = state.selectedColor.third
                                                    ),
                                                    layer = 0
                                                )
                                            )
                                        )
                                    }
                                )
                            )
                        }
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

                // Update selection to new last frame if we deleted the last frame
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

            is Event.OnImportMidiFile -> {
                runBlocking {
                    val file = FileKit.openFilePicker(
                        type = FileKitType.File(
                            extension = "mid"
                        ),
                    )

                    if (file == null) return@runBlocking

                    val data = MidiFileImporter.loadFile(
                        file = file,
                        bpm = WorkspaceRepository.bpm.value
                    )

                    this@KeyframesChainDevice.state.update {
                        data
                    }
                }
            }
        }
    }

    fun duplicateFrame(index: Int, toIndex: Int? = null) {
        val frame = state.value.frames[index]
        val targetIndex = toIndex ?: (index + 1)
        val newFrame = frame.copy(_internalUuid = UUID.randomUUID())

        UndoManager.addAction(
            UndoableAction.KeyframeDuplication(
                device = this,
                originalIndex = index,
                duplicatedIndex = targetIndex,
                duplicatedFrame = newFrame
            )
        )

        duplicateFrameInternal(index, targetIndex)
        refreshVirtualDevices()
    }

    fun removeFrame(index: Int) {
        val frameToDelete = state.value.frames[index]

        UndoManager.addAction(
            UndoableAction.KeyframeDeletion(
                device = this,
                frameIndex = index,
                frame = frameToDelete
            )
        )

        removeFrameInternal(index)
        refreshVirtualDevices()
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
            state.value.frames[state.value.currentFrameIndex].entries.map {
                Signal(
                    origin = this,
                    x = it.x,
                    y = it.y,
                    color = Color(it.r, it.g, it.b),
                    layer = 0
                )
            }
        )
    }

    fun renderAnimation() {
        val bpm = WorkspaceRepository.bpm.value
        var animationMs = 0

        val frames = state.value.frames + Frame(
            timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_16),
            _internalUuid = UUID.randomUUID()
        )

        val renderedAnimation = buildList {
            frames.forEachIndexed { index, frame ->
                val previousFrame = frames.getOrNull(index - 1)
                val deltaMs = previousFrame?.timing?.toMsValue(bpm)?.times(frame.gate * 2)?.toInt() ?: 0
                animationMs += deltaMs

                val signals = buildList {
                    addAll(frame.entries.filter { !(previousFrame?.entries?.contains(it) ?: false) }.map { it.toSignal() })

                    if (previousFrame != null) {
                        val cleared = previousFrame.entries.filter { prev ->
                            frame.entries.none { it.x == prev.x && it.y == prev.y }
                        }.map {
                            it.toOffSignal()
                        }

                        addAll(cleared)
                    }
                }

                add(animationMs to signals)
            }
        }

        state.update { it.copy(renderedAnimation = renderedAnimation) }
    }

    private fun KeyframesEntry.toSignal() = Signal(
        origin = this,
        x = x,
        y = y,
        color = Color(r, g, b),
        layer = 0
    )

    private fun KeyframesEntry.toOffSignal() = Signal(
        origin = this,
        x = x,
        y = y,
        color = Color.Black,
        layer = 0
    )

    override fun midiEnter(n: List<Signal>) {
        n.forEach {
            if (it.color != Color.Black) {
                // Cancel alle vorherigen Animation-Jobs für dieses Device
                Heaven.cancelJobsForOwner(this)

                // Starte neue Animation
                state.value.renderedAnimation.forEach {
                    Heaven.schedule(it.first.toDouble(), owner = this) {
                        midiExit?.invoke(it.second)
                    }
                }
            }
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

        // Select the newly duplicated frames
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

        // Prepare paste info for all frames
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

        // Add to UndoManager as single action
        UndoManager.addAction(
            UndoableAction.KeyframePaste(
                device = this,
                pastedFrames = pastedFrameInfos
            )
        )

        // Perform the actual paste operations
        pastedFrameInfos.forEach { pasteInfo ->
            addFrameInternal(pasteInfo.frameIndex, pasteInfo.frame)
        }

        // Select the newly pasted frames
        SelectionManager.clear()
        pastedFrameInfos.forEach { pasteInfo ->
            SelectionManager.select(
                Selectable.KeyframeItem(parent = this, frameIndex = pasteInfo.frameIndex),
                single = false
            )
        }

        refreshVirtualDevices()
    }
}
