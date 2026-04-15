package dev.anthonyhfm.amethyst.gem.ui.editor

import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemNodeInstance
import dev.anthonyhfm.amethyst.gem.GemNodeLayout
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemValidationErrorCode
import dev.anthonyhfm.amethyst.gem.GemValidationResult
import dev.anthonyhfm.amethyst.gem.GemValidator
import dev.anthonyhfm.amethyst.gem.runtime.GemCompilationResult
import dev.anthonyhfm.amethyst.gem.runtime.GemCompiler
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GemEditorSession(
    initialAsset: GemAsset,
    private val registry: GemNodeRegistry = GemNodeRegistry.builtIns,
    private val persistAsset: (GemAsset, String?) -> GemAsset = { asset, _ -> asset },
    historyLimit: Int = DEFAULT_HISTORY_LIMIT
) {
    private val history = GemEditorUndoHistory(limit = historyLimit)
    private var transientSnapshot: GemEditorSnapshot? = null
    private val _state = MutableStateFlow(buildState(originalAsset = initialAsset, editedAsset = initialAsset))
    val state: StateFlow<GemEditorSessionState> = _state.asStateFlow()

    fun replaceEditedAsset(asset: GemAsset): GemAsset = applyEditedAsset(asset)

    fun updateAsset(transform: (GemAsset) -> GemAsset): GemAsset = applyEditedAsset(
        transform(state.value.editedAsset)
    )

    fun updateGraph(
        graphId: String = Gem.rootGraphId,
        transform: (dev.anthonyhfm.amethyst.gem.GemGraph) -> dev.anthonyhfm.amethyst.gem.GemGraph
    ): GemAsset = updateAsset { it.updateGraph(graphId = graphId, transform = transform) }

    fun updateNode(
        nodeId: String,
        graphId: String = state.value.selection.graphId,
        transform: (GemNodeInstance) -> GemNodeInstance
    ): GemAsset = updateGraph(graphId = graphId) { graph ->
        graph.updateNode(nodeId = nodeId, transform = transform)
    }

    fun putNode(
        node: GemNodeInstance,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset { it.putNode(node = node, graphId = graphId) }

    fun removeNode(
        nodeId: String,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset { it.removeNode(nodeId = nodeId, graphId = graphId) }

    fun moveNode(
        nodeId: String,
        position: GemNodePosition,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset {
        it.moveNode(
            nodeId = nodeId,
            position = position,
            graphId = graphId
        )
    }

    fun moveNodes(
        positions: Map<String, GemNodePosition>,
        graphId: String = Gem.rootGraphId
    ): GemAsset {
        if (positions.isEmpty()) {
            return state.value.editedAsset
        }
        return updateGraph(graphId) { graph ->
            positions.entries.fold(graph) { current, (nodeId, position) ->
                current.moveNode(
                    nodeId = nodeId,
                    position = position
                )
            }
        }
    }

    fun moveNodesTransient(
        positions: Map<String, GemNodePosition>,
        graphId: String = Gem.rootGraphId
    ): GemAsset {
        if (positions.isEmpty()) {
            return state.value.editedAsset
        }
        return applyEditedAssetTransient(
            state.value.editedAsset.updateGraph(graphId) { graph ->
                positions.entries.fold(graph) { current, (nodeId, position) ->
                    current.moveNode(
                        nodeId = nodeId,
                        position = position
                    )
                }
            }
        )
    }

    fun commitTransientChanges(): GemAsset {
        val snapshot = transientSnapshot ?: return state.value.editedAsset
        transientSnapshot = null
        val current = state.value
        if (snapshot.asset == current.editedAsset && snapshot.selection == current.selection) {
            return current.editedAsset
        }
        history.record(snapshot)
        publishState(
            originalAsset = current.originalAsset,
            editedAsset = current.editedAsset,
            selection = current.selection,
            pendingTransition = current.pendingTransition
        )
        return state.value.editedAsset
    }

    fun connect(
        connection: GemConnection,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset { it.connect(connection = connection, graphId = graphId) }

    fun disconnect(
        connectionId: String,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset { it.disconnect(connectionId = connectionId, graphId = graphId) }

    fun disconnect(
        pin: GemPinRef,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateAsset { it.disconnect(pin = pin, graphId = graphId) }

    fun setSelection(selection: GemEditorSelection) {
        _state.update { current ->
            current.copy(
                selection = sanitizeSelection(
                    asset = current.editedAsset,
                    selection = selection
                )
            )
        }
    }

    fun selectNodes(
        nodeIds: Set<String>,
        graphId: String = state.value.selection.graphId
    ) {
        setSelection(
            state.value.selection.copy(
                graphId = graphId,
                nodeIds = nodeIds
            )
        )
    }

    fun selectConnections(
        connectionIds: Set<String>,
        graphId: String = state.value.selection.graphId
    ) {
        setSelection(
            state.value.selection.copy(
                graphId = graphId,
                connectionIds = connectionIds
            )
        )
    }

    fun selectExposedParameter(parameterId: String?) {
        setSelection(
            state.value.selection.copy(
                exposedParameterId = parameterId
            )
        )
    }

    fun clearSelection() {
        setSelection(GemEditorSelection())
    }

    fun deleteSelection(): GemAsset {
        val current = state.value
        val selection = current.selection
        if (selection.nodeIds.isEmpty() && selection.connectionIds.isEmpty()) {
            return current.editedAsset
        }
        val updated = deleteGemSelection(
            asset = current.editedAsset,
            selection = selection
        )
        val applied = applyEditedAsset(updated)
        setSelection(
            selection.copy(
                nodeIds = emptySet(),
                connectionIds = emptySet()
            )
        )
        return applied
    }

    fun duplicateSelection(): GemAsset {
        val current = state.value
        val result = duplicateGemSelection(
            asset = current.editedAsset,
            selection = current.selection
        )
        if (result.asset == current.editedAsset) {
            return current.editedAsset
        }
        val applied = applyEditedAsset(result.asset)
        setSelection(
            current.selection.copy(
                nodeIds = result.duplicatedNodeIds,
                connectionIds = result.duplicatedConnectionIds
            )
        )
        return applied
    }

    fun undo(): Boolean {
        transientSnapshot = null
        val current = state.value
        val previous = history.undo(
            current = GemEditorSnapshot(
                asset = current.editedAsset,
                selection = current.selection
            )
        ) ?: return false

        publishState(
            originalAsset = current.originalAsset,
            editedAsset = previous.asset,
            selection = previous.selection,
            pendingTransition = null
        )
        return true
    }

    fun redo(): Boolean {
        transientSnapshot = null
        val current = state.value
        val next = history.redo(
            current = GemEditorSnapshot(
                asset = current.editedAsset,
                selection = current.selection
            )
        ) ?: return false

        publishState(
            originalAsset = current.originalAsset,
            editedAsset = next.asset,
            selection = next.selection,
            pendingTransition = null
        )
        return true
    }

    fun save(): GemAsset {
        transientSnapshot = null
        val current = state.value
        val previousAssetId = current.originalAsset.metadata.id.takeIf { it.isNotBlank() }
        val persisted = persistAsset(current.editedAsset, previousAssetId)
        publishState(
            originalAsset = persisted,
            editedAsset = persisted,
            selection = current.selection,
            pendingTransition = current.pendingTransition
        )
        return persisted
    }

    fun discardChanges(): GemAsset {
        transientSnapshot = null
        val current = state.value
        publishState(
            originalAsset = current.originalAsset,
            editedAsset = current.originalAsset,
            selection = current.selection,
            pendingTransition = null,
            clearHistory = true
        )
        return state.value.editedAsset
    }

    fun requestAssetSwitch(asset: GemAsset): GemEditorTransitionResult {
        val current = state.value
        if (!current.isDirty &&
            asset.metadata.id == current.originalAsset.metadata.id &&
            asset == current.originalAsset
        ) {
            return GemEditorTransitionResult.NoOp
        }
        return requestTransition(GemEditorPendingTransition.Target.Asset(asset))
    }

    fun requestExit(): GemEditorTransitionResult = requestTransition(
        GemEditorPendingTransition.Target.Exit
    )

    fun resolvePendingTransition(decision: GemEditorPendingDecision): GemEditorTransitionResult {
        val pending = state.value.pendingTransition ?: return GemEditorTransitionResult.NoOp
        return when (decision) {
            GemEditorPendingDecision.Cancel -> {
                _state.update { current -> current.copy(pendingTransition = null) }
                GemEditorTransitionResult.Cancelled
            }

            GemEditorPendingDecision.Save -> {
                save()
                applyResolvedTransition(pending.target)
            }

            GemEditorPendingDecision.Discard -> {
                when (pending.target) {
                    is GemEditorPendingTransition.Target.Asset -> openAsset(
                        pending.target.asset
                    )

                    GemEditorPendingTransition.Target.Exit -> {
                        discardChanges()
                    }
                }
                _state.update { current -> current.copy(pendingTransition = null) }
                when (pending.target) {
                    is GemEditorPendingTransition.Target.Asset -> GemEditorTransitionResult.AssetOpened(
                        asset = state.value.editedAsset
                    )

                    GemEditorPendingTransition.Target.Exit -> GemEditorTransitionResult.ExitApproved
                }
            }
        }
    }

    private fun requestTransition(target: GemEditorPendingTransition.Target): GemEditorTransitionResult {
        val current = state.value
        if (!current.isDirty) {
            return applyResolvedTransition(target)
        }

        val pending = GemEditorPendingTransition(
            target = target,
            prompt = GemEditorUnsavedChangesPrompt(
                activeAssetId = current.editedAsset.metadata.id,
                activeAssetName = current.editedAsset.metadata.name,
                isDirty = current.isDirty,
                isValid = current.validationResult.isValid,
                isRunnable = current.refreshState.isRunnable
            )
        )
        _state.update { it.copy(pendingTransition = pending) }
        return GemEditorTransitionResult.PromptRequired(pending)
    }

    private fun applyResolvedTransition(target: GemEditorPendingTransition.Target): GemEditorTransitionResult {
        _state.update { current -> current.copy(pendingTransition = null) }
        return when (target) {
            is GemEditorPendingTransition.Target.Asset -> {
                openAsset(target.asset)
                GemEditorTransitionResult.AssetOpened(asset = state.value.editedAsset)
            }

            GemEditorPendingTransition.Target.Exit -> GemEditorTransitionResult.ExitApproved
        }
    }

    private fun openAsset(asset: GemAsset) {
        transientSnapshot = null
        publishState(
            originalAsset = asset,
            editedAsset = asset,
            selection = GemEditorSelection(),
            pendingTransition = null,
            clearHistory = true
        )
    }

    private fun applyEditedAsset(asset: GemAsset): GemAsset {
        transientSnapshot = null
        val current = state.value
        if (asset == current.editedAsset) {
            return current.editedAsset
        }

        history.record(
            GemEditorSnapshot(
                asset = current.editedAsset,
                selection = current.selection
            )
        )
        publishState(
            originalAsset = current.originalAsset,
            editedAsset = asset,
            selection = current.selection,
            pendingTransition = null
        )
        return state.value.editedAsset
    }

    private fun applyEditedAssetTransient(asset: GemAsset): GemAsset {
        val current = state.value
        if (asset == current.editedAsset) {
            return current.editedAsset
        }
        if (transientSnapshot == null) {
            transientSnapshot = GemEditorSnapshot(
                asset = current.editedAsset,
                selection = current.selection
            )
        }
        // Transient changes (e.g. node position drags) do not affect graph topology, so we
        // reuse the current validation and compilation results to avoid O(N) recompilation
        // on every drag frame.
        publishState(
            originalAsset = current.originalAsset,
            editedAsset = asset,
            selection = current.selection,
            pendingTransition = current.pendingTransition,
            skipRevalidation = true
        )
        return state.value.editedAsset
    }

    private fun publishState(
        originalAsset: GemAsset,
        editedAsset: GemAsset,
        selection: GemEditorSelection,
        pendingTransition: GemEditorPendingTransition?,
        clearHistory: Boolean = false,
        skipRevalidation: Boolean = false
    ) {
        if (clearHistory) {
            history.clear()
        }
        val sanitizedSelection = sanitizeSelection(
            asset = editedAsset,
            selection = selection
        )
        // Skip validation and compilation when topology hasn't changed (e.g. only node
        // positions differ). This covers both explicit transient moves (skipRevalidation=true)
        // and any other position-only edits such as committed drag operations.
        val effectiveSkipRevalidation = skipRevalidation ||
            (!clearHistory && gemAssetTopologyEquals(_state.value.editedAsset, editedAsset))
        _state.value = buildState(
            originalAsset = originalAsset,
            editedAsset = editedAsset,
            selection = sanitizedSelection,
            pendingTransition = pendingTransition,
            previousState = _state.value.takeIf { effectiveSkipRevalidation }
        )
    }

    private fun buildState(
        originalAsset: GemAsset,
        editedAsset: GemAsset,
        selection: GemEditorSelection = GemEditorSelection(),
        pendingTransition: GemEditorPendingTransition? = null,
        previousState: GemEditorSessionState? = null
    ): GemEditorSessionState {
        val validation = previousState?.validationResult
            ?: GemValidator.validate(editedAsset, registry)
        val compilation = previousState?.compilationResult
            ?: GemCompiler.compile(
                asset = editedAsset,
                validation = validation
            )
        val refreshState = previousState?.refreshState ?: GemEditorRefreshState(
            status = when {
                !validation.isValid -> GemEditorRefreshStatus.Invalid
                compilation.isSuccess -> GemEditorRefreshStatus.Runnable
                else -> GemEditorRefreshStatus.Unrunnable
            },
            hasUnsupportedNodes = validation.errors.any { error ->
                error.code == GemValidationErrorCode.UNKNOWN_NODE_TYPE ||
                    error.code == GemValidationErrorCode.UNSUPPORTED_NODE_VERSION
            } || compilation.diagnostics.any { diagnostic ->
                diagnostic.code == GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS
            }
        )
        return GemEditorSessionState(
            originalAsset = originalAsset,
            editedAsset = editedAsset,
            selection = selection,
            validationResult = validation,
            compilationResult = compilation,
            refreshState = refreshState,
            undoDepth = history.undoDepth,
            redoDepth = history.redoDepth,
            pendingTransition = pendingTransition
        )
    }

    private fun sanitizeSelection(
        asset: GemAsset,
        selection: GemEditorSelection
    ): GemEditorSelection {
        val graph = asset.graph(selection.graphId)
            ?: asset.definition.rootGraph
        val validNodeIds = selection.nodeIds.filterTo(linkedSetOf()) { nodeId ->
            graph.node(nodeId) != null
        }
        val validConnectionIds = selection.connectionIds.filterTo(linkedSetOf()) { connectionId ->
            graph.connection(connectionId) != null
        }
        val validParameterId = selection.exposedParameterId?.takeIf { parameterId ->
            asset.definition.exposedParameters.any { parameter -> parameter.id == parameterId }
        }
        return selection.copy(
            graphId = graph.id,
            nodeIds = validNodeIds,
            connectionIds = validConnectionIds,
            exposedParameterId = validParameterId
        )
    }

    companion object {
        private const val DEFAULT_HISTORY_LIMIT = 100
    }
}

data class GemEditorSessionState(
    val originalAsset: GemAsset,
    val editedAsset: GemAsset,
    val selection: GemEditorSelection,
    val validationResult: GemValidationResult,
    val compilationResult: GemCompilationResult,
    val refreshState: GemEditorRefreshState,
    val undoDepth: Int,
    val redoDepth: Int,
    val pendingTransition: GemEditorPendingTransition? = null
) {
    val isDirty: Boolean
        get() = editedAsset != originalAsset

    val canUndo: Boolean
        get() = undoDepth > 0

    val canRedo: Boolean
        get() = redoDepth > 0
}

data class GemEditorSelection(
    val graphId: String = Gem.rootGraphId,
    val nodeIds: Set<String> = emptySet(),
    val connectionIds: Set<String> = emptySet(),
    val exposedParameterId: String? = null
)

data class GemEditorRefreshState(
    val status: GemEditorRefreshStatus,
    val hasUnsupportedNodes: Boolean
) {
    val isRunnable: Boolean
        get() = status == GemEditorRefreshStatus.Runnable
}

enum class GemEditorRefreshStatus {
    Runnable,
    Invalid,
    Unrunnable
}

data class GemEditorPendingTransition(
    val target: Target,
    val prompt: GemEditorUnsavedChangesPrompt
) {
    sealed interface Target {
        data class Asset(val asset: GemAsset) : Target
        data object Exit : Target
    }
}

data class GemEditorUnsavedChangesPrompt(
    val activeAssetId: String,
    val activeAssetName: String,
    val isDirty: Boolean,
    val isValid: Boolean,
    val isRunnable: Boolean
)

enum class GemEditorPendingDecision {
    Save,
    Discard,
    Cancel
}

sealed interface GemEditorTransitionResult {
    data class PromptRequired(val pendingTransition: GemEditorPendingTransition) : GemEditorTransitionResult
    data class AssetOpened(val asset: GemAsset) : GemEditorTransitionResult
    data object ExitApproved : GemEditorTransitionResult
    data object Cancelled : GemEditorTransitionResult
    data object NoOp : GemEditorTransitionResult
}

private data class GemEditorSnapshot(
    val asset: GemAsset,
    val selection: GemEditorSelection
)

private class GemEditorUndoHistory(
    private val limit: Int
) {
    private val undoStack = ArrayDeque<GemEditorSnapshot>()
    private val redoStack = ArrayDeque<GemEditorSnapshot>()

    val undoDepth: Int
        get() = undoStack.size

    val redoDepth: Int
        get() = redoStack.size

    fun record(snapshot: GemEditorSnapshot) {
        undoStack.addLast(snapshot)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: GemEditorSnapshot): GemEditorSnapshot? {
        if (undoStack.isEmpty()) {
            return null
        }

        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: GemEditorSnapshot): GemEditorSnapshot? {
        if (redoStack.isEmpty()) {
            return null
        }

        val next = redoStack.removeLast()
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

/**
 * Returns true if [a] and [b] have the same graph topology — that is, the same nodes (by
 * identity, type, state, connections, and structure) regardless of their canvas layout
 * positions. This is used to skip redundant validation and compilation when only positions
 * changed (e.g. after a committed node drag).
 */
private fun gemAssetTopologyEquals(a: GemAsset, b: GemAsset): Boolean {
    if (a == b) return true
    return a.withoutNodeLayouts() == b.withoutNodeLayouts()
}

private fun GemAsset.withoutNodeLayouts(): GemAsset = copy(
    definition = definition.copy(
        rootGraph = definition.rootGraph.withoutNodeLayouts(),
        subgraphs = definition.subgraphs.map { it.withoutNodeLayouts() }
    )
)

private fun GemGraph.withoutNodeLayouts(): GemGraph = copy(
    nodes = nodes.map { it.copy(layout = GemNodeLayout()) }
)
